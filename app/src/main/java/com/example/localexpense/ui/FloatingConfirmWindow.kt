package com.example.localexpense.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.example.localexpense.R
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.util.Logger
import com.example.localexpense.util.MainThreadGuard
import java.lang.ref.WeakReference

/**
 * 悬浮窗确认界面 (模仿 iOS Cookie 的胶囊提示)
 * 当识别到账单后,在屏幕顶部弹出确认窗口
 *
 * 生命周期管理：
 * 1. show() - 显示悬浮窗
 * 2. dismiss() - 用户取消，触发 onDismiss 回调
 * 3. 自动消失 - 超时后自动确认并保存
 * 4. onServiceDestroy() - 服务销毁时清理资源
 *
 * 线程安全：
 * - 所有状态变更通过 synchronized(lock) 保护
 * - 回调使用 WeakReference 防止内存泄漏
 * - 所有 UI 操作确保在主线程执行
 */
class FloatingConfirmWindow(context: Context) {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val AUTO_DISMISS_DELAY_MS = 10000L  // 优化：延长到10秒，给用户更多确认时间
        private const val WINDOW_Y_OFFSET = 100  // 距离顶部偏移量（像素）
        private const val ANIMATION_DURATION_MS = 300L  // 动画时长
        private const val COUNTDOWN_WARNING_THRESHOLD = 3  // 倒计时警告阈值（最后3秒）

        fun hasPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        /**
         * 跳转到悬浮窗权限设置页面
         */
        fun requestPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

    // 使用 WeakReference 防止 Context 泄漏
    private val contextRef = WeakReference(context.applicationContext)
    private val windowManager: WindowManager? = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var floatingView: View? = null
    private var onConfirm: ((ExpenseEntity) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var pendingTransaction: ExpenseEntity? = null

    // 线程同步锁，保护 isShowing 状态和相关操作
    private val lock = Any()

    // 状态标志
    @Volatile
    private var isShowing = false

    @Volatile
    private var isAnimating = false

    // 使用 Handler 管理定时任务
    private val handler = Handler(Looper.getMainLooper())

    // 标记服务是否已销毁
    @Volatile
    private var isServiceDestroyed = false

    // 动画相关
    private var showAnimator: ObjectAnimator? = null
    private var dismissAnimator: ObjectAnimator? = null

    // 倒计时显示
    private var countdownSeconds = (AUTO_DISMISS_DELAY_MS / 1000).toInt()
    private var countdownTextView: TextView? = null
    private var showStartTime = 0L  // 显示开始时间，用于精确计算倒计时

    // 自动消失时默认确认保存交易
    private val autoDismissRunnable = Runnable {
        if (isServiceDestroyed) {
            Logger.w(TAG, "服务已销毁，取消自动保存")
            return@Runnable
        }

        val transaction: ExpenseEntity?
        val confirmCallback: ((ExpenseEntity) -> Unit)?

        synchronized(lock) {
            transaction = pendingTransaction
            confirmCallback = onConfirm
        }

        if (transaction != null && confirmCallback != null) {
            try {
                confirmCallback.invoke(transaction)
                Logger.d(TAG) { "自动确认保存交易" }
            } catch (e: Exception) {
                Logger.e(TAG, "自动保存回调异常", e)
            }
        }
        dismissWithAnimation()
    }

    // 倒计时更新（使用绝对时间计算，避免累计误差）
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!isShowing || isServiceDestroyed) return

            val elapsed = System.currentTimeMillis() - showStartTime
            val remaining = ((AUTO_DISMISS_DELAY_MS - elapsed) / 1000).toInt().coerceAtLeast(0)

            if (remaining > 0) {
                // 优化：最后3秒显示更醒目的提示
                val text = if (remaining <= COUNTDOWN_WARNING_THRESHOLD) {
                    "⚠️ ${remaining}秒后自动记录"
                } else {
                    "${remaining}秒后自动记录"
                }
                countdownTextView?.text = text

                // 最后3秒时改变文字颜色（如果可能）
                if (remaining <= COUNTDOWN_WARNING_THRESHOLD) {
                    countdownTextView?.setTextColor(android.graphics.Color.parseColor("#FF5722"))
                }

                handler.postDelayed(this, 500)  // 更频繁更新以提高精度
            } else {
                countdownTextView?.text = "正在记录..."
            }
        }
    }

    @SuppressLint("InflateParams")
    fun show(transaction: ExpenseEntity, onConfirm: (ExpenseEntity) -> Unit, onDismiss: () -> Unit) {
        MainThreadGuard.runOnMainThread {
            showInternal(transaction, onConfirm, onDismiss)
        }
    }

    private fun showInternal(transaction: ExpenseEntity, onConfirm: (ExpenseEntity) -> Unit, onDismiss: () -> Unit) {
        val context = contextRef.get()
        if (context == null) {
            Logger.w(TAG, "Context 已被回收")
            return
        }

        if (windowManager == null) {
            Logger.w(TAG, "WindowManager 不可用")
            return
        }

        synchronized(lock) {
            // 如果已经显示，先关闭旧窗口
            if (isShowing) {
                Logger.d(TAG) { "关闭旧窗口显示新交易" }
                dismissInternalLocked(animate = false)
            }

            // 如果正在动画中，等待动画完成
            if (isAnimating) {
                showAnimator?.cancel()
                dismissAnimator?.cancel()
            }

            this.pendingTransaction = transaction
            this.onConfirm = onConfirm
            this.onDismiss = onDismiss
            this.countdownSeconds = (AUTO_DISMISS_DELAY_MS / 1000).toInt()

            // 创建悬浮窗布局
            try {
                floatingView = LayoutInflater.from(context).inflate(R.layout.floating_confirm_window, null)
            } catch (e: Exception) {
                Logger.e(TAG, "加载布局失败", e)
                return
            }

            // 设置数据
            floatingView?.apply {
                alpha = 0f  // 初始透明，用于动画

                findViewById<TextView>(R.id.tv_amount)?.text = "¥${String.format("%.2f", transaction.amount)}"
                findViewById<TextView>(R.id.tv_type)?.text = if (transaction.type == "income") "收入" else "支出"
                findViewById<TextView>(R.id.tv_merchant)?.text = transaction.merchant.take(15)
                findViewById<TextView>(R.id.tv_category)?.text = transaction.category

                // 倒计时文本
                countdownTextView = findViewById(R.id.tv_countdown)
                countdownTextView?.text = "${countdownSeconds}秒后自动记录"

                // 确认按钮
                findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                    if (!isAnimating) {
                        this@FloatingConfirmWindow.onConfirm?.invoke(transaction)
                        dismissWithAnimation()
                    }
                }

                // 关闭按钮
                findViewById<View>(R.id.btn_dismiss)?.setOnClickListener {
                    if (!isAnimating) {
                        this@FloatingConfirmWindow.onDismiss?.invoke()
                        dismissWithAnimation()
                    }
                }
            }

            // 悬浮窗参数
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = WINDOW_Y_OFFSET
            }

            try {
                windowManager.addView(floatingView, params)
                isShowing = true
                showStartTime = System.currentTimeMillis()  // 记录显示开始时间

                // 显示动画
                playShowAnimation()

                // 自动隐藏
                handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS)

                // 倒计时更新
                handler.postDelayed(countdownRunnable, 500)

                Logger.d(TAG) { "悬浮窗显示成功" }
            } catch (e: Exception) {
                Logger.e(TAG, "显示悬浮窗失败", e)
                isShowing = false
                floatingView = null
            }
        }
    }

    /**
     * 播放显示动画
     */
    private fun playShowAnimation() {
        val view = floatingView ?: return

        isAnimating = true
        showAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            start()
        }
    }

    /**
     * 外部调用的dismiss方法（用户点击取消按钮时调用）
     */
    fun dismiss() {
        MainThreadGuard.runOnMainThread {
            synchronized(lock) {
                onDismiss?.invoke()
                dismissInternalLocked(animate = true)
            }
        }
    }

    /**
     * 带动画的dismiss
     */
    private fun dismissWithAnimation() {
        MainThreadGuard.runOnMainThread {
            synchronized(lock) {
                dismissInternalLocked(animate = true)
            }
        }
    }

    /**
     * 内部dismiss实现，调用前必须持有lock
     */
    private fun dismissInternalLocked(animate: Boolean) {
        // 取消定时任务
        handler.removeCallbacks(autoDismissRunnable)
        handler.removeCallbacks(countdownRunnable)

        // 取消正在进行的动画
        showAnimator?.cancel()
        dismissAnimator?.cancel()

        val view = floatingView
        if (view == null) {
            isShowing = false
            isAnimating = false
            return
        }

        // 先清理回调引用，防止动画过程中被调用
        val savedOnConfirm = onConfirm
        val savedOnDismiss = onDismiss
        onConfirm = null
        onDismiss = null

        if (animate && !isServiceDestroyed && view.isAttachedToWindow) {
            // 播放消失动画
            isAnimating = true
            dismissAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                duration = ANIMATION_DURATION_MS
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isShowing = false
                        removeViewSafely(view)
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        isAnimating = false
                        isShowing = false
                        removeViewSafely(view)
                    }
                })
                start()
            }
        } else {
            // 直接移除
            isShowing = false
            isAnimating = false
            removeViewSafely(view)
        }

        floatingView = null
        pendingTransaction = null
        countdownTextView = null
    }

    /**
     * 安全移除视图
     */
    private fun removeViewSafely(view: View) {
        try {
            if (view.isAttachedToWindow) {
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "移除视图失败: ${e.message}")
        }
    }

    /**
     * 检查悬浮窗是否正在显示
     */
    fun isShowing(): Boolean = isShowing

    /**
     * 标记服务已销毁
     */
    fun onServiceDestroy() {
        Logger.d(TAG) { "服务销毁，清理悬浮窗资源" }
        isServiceDestroyed = true

        MainThreadGuard.runOnMainThread {
            synchronized(lock) {
                handler.removeCallbacksAndMessages(null)
                showAnimator?.cancel()
                dismissAnimator?.cancel()
                floatingView?.let { removeViewSafely(it) }
                floatingView = null
                pendingTransaction = null
                countdownTextView = null
                isShowing = false
                isAnimating = false
                onConfirm = null
                onDismiss = null
            }
        }
    }
}
