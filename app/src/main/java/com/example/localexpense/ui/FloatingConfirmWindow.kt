package com.example.localexpense.ui

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
import android.widget.TextView
import com.example.localexpense.R
import com.example.localexpense.data.ExpenseEntity

/**
 * 悬浮窗确认界面 (模仿 iOS Cookie 的胶囊提示)
 * 当识别到账单后,在屏幕顶部弹出确认窗口
 */
class FloatingConfirmWindow(private val context: Context) {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val AUTO_DISMISS_DELAY_MS = 8000L  // 自动隐藏延迟
        private const val WINDOW_Y_OFFSET = 100  // 距离顶部偏移量（像素）

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

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var onConfirm: ((ExpenseEntity) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var pendingTransaction: ExpenseEntity? = null

    // 线程同步锁，保护 isShowing 状态和相关操作
    private val lock = Any()

    // 状态标志，防止重复显示
    @Volatile
    private var isShowing = false

    // 使用 Handler 管理定时任务，避免内存泄漏
    private val handler = Handler(Looper.getMainLooper())
    // 自动消失时默认确认保存交易（符合用户预期）
    private val autoDismissRunnable = Runnable {
        // 在 synchronized 块外保存引用，避免在锁内执行回调
        val transaction = pendingTransaction
        val confirmCallback = onConfirm
        if (transaction != null && confirmCallback != null) {
            confirmCallback.invoke(transaction)
        }
        dismissInternal()
    }

    @SuppressLint("InflateParams")
    fun show(transaction: ExpenseEntity, onConfirm: (ExpenseEntity) -> Unit, onDismiss: () -> Unit) {
        // 使用 synchronized 保护状态变更，防止竞态条件
        synchronized(lock) {
            // 如果已经显示，先关闭旧窗口（不触发回调，因为新交易会取代旧交易）
            if (isShowing) {
                android.util.Log.d(TAG, "悬浮窗已在显示中，关闭旧窗口显示新交易")
                dismissInternalLocked()
            }

            this.pendingTransaction = transaction
            this.onConfirm = onConfirm
            this.onDismiss = onDismiss

            // 创建悬浮窗布局
            floatingView = LayoutInflater.from(context).inflate(R.layout.floating_confirm_window, null)

            // 设置数据
            floatingView?.apply {
                findViewById<TextView>(R.id.tv_amount)?.text = "¥${String.format("%.2f", transaction.amount)}"
                findViewById<TextView>(R.id.tv_type)?.text = if (transaction.type == "income") "收入" else "支出"
                findViewById<TextView>(R.id.tv_merchant)?.text = transaction.merchant
                findViewById<TextView>(R.id.tv_category)?.text = transaction.category

                // 确认按钮
                findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                    this@FloatingConfirmWindow.onConfirm?.invoke(transaction)
                    dismissInternal()  // 使用内部方法，避免重复触发回调
                }

                // 关闭按钮（用户主动取消）
                findViewById<View>(R.id.btn_dismiss)?.setOnClickListener {
                    dismiss()  // 使用外部方法，会触发 onDismiss 回调
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
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = WINDOW_Y_OFFSET
            }

            try {
                windowManager.addView(floatingView, params)
                isShowing = true

                // 自动隐藏，使用 Handler 以便能够取消
                handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "显示悬浮窗失败: ${e.message}")
                isShowing = false
            }
        }
    }

    /**
     * 外部调用的dismiss方法（用户点击取消按钮时调用）
     */
    fun dismiss() {
        synchronized(lock) {
            onDismiss?.invoke()
            dismissInternalLocked()
        }
    }

    /**
     * 内部使用的dismiss方法，不触发回调
     * 用于：1. 自动消失后  2. 显示新窗口前关闭旧窗口
     * 会自动获取锁
     */
    private fun dismissInternal() {
        synchronized(lock) {
            dismissInternalLocked()
        }
    }

    /**
     * 内部dismiss实现，调用前必须持有lock
     * 用于在已持有锁的上下文中调用
     */
    private fun dismissInternalLocked() {
        // 取消定时任务
        handler.removeCallbacks(autoDismissRunnable)

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 忽略移除失败
            }
        }
        floatingView = null
        pendingTransaction = null
        isShowing = false
        // 清理回调引用，防止内存泄漏
        onConfirm = null
        onDismiss = null
    }
}
