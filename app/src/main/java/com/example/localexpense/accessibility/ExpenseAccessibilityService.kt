package com.example.localexpense.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.localexpense.BuildConfig
import com.example.localexpense.R
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.parser.TransactionParser
import com.example.localexpense.parser.RuleEngine
import com.example.localexpense.ui.FloatingConfirmWindow
import com.example.localexpense.ocr.ScreenCaptureManager
import com.example.localexpense.ocr.OcrParser
import com.example.localexpense.util.Constants
import com.example.localexpense.util.MonitorSettings

class ExpenseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ExpenseService"
        private const val CHANNEL_ID = "expense_channel"
        // 通知ID范围：1 到 NOTIFICATION_ID_MOD
        private const val NOTIFICATION_ID_MIN = 1
        private const val NOTIFICATION_ID_MAX = Constants.NOTIFICATION_ID_MOD

        // 监听的App包名
        private val MONITORED_PACKAGES = setOf(
            "com.tencent.mm",              // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.unionpay"                 // 云闪付
        )
    }

    private var repository: TransactionRepository? = null
    private var notificationManager: NotificationManager? = null
    @Volatile
    private var isInitialized = false

    // 悬浮窗管理器
    private var floatingWindow: FloatingConfirmWindow? = null

    // 截图管理器（Android 11+ OCR备用方案）
    private var screenCaptureManager: ScreenCaptureManager? = null

    // 通知ID，使用 AtomicInteger 确保线程安全
    private val notifyId = java.util.concurrent.atomic.AtomicInteger(NOTIFICATION_ID_MIN)

    // 防重复检测器
    private val duplicateChecker = DuplicateChecker()

    // OCR识别频率限制（毫秒），防止频繁触发OCR
    private val OCR_COOLDOWN_MS = 2000L
    @Volatile
    private var lastOcrTime = 0L

    // 文本收集最大深度，防止栈溢出
    private val maxCollectDepth = 25

    // 最大子节点数量限制（增加到100以减少文本遗漏）
    private val maxChildCount = 100

    // 最大文本数量限制
    private val maxTextCount = 300

    /**
     * 防重复检测器 - 线程安全
     * 基于金额+商户+类型的组合判断，而非仅依赖 rawText hash
     * 这样可以减少误判：相同金额但不同商户的交易不会被过滤
     */
    private class DuplicateChecker {
        private val lock = Any()
        // 使用更精确的标识：金额+商户+类型
        @Volatile private var lastKey: String = ""
        @Volatile private var lastTime: Long = 0

        /**
         * 检查交易是否应该被处理
         * @param amount 交易金额
         * @param merchant 商户名称
         * @param type 交易类型 (expense/income)
         * @return true 表示应该处理，false 表示重复应跳过
         */
        fun shouldProcess(amount: Double, merchant: String, type: String): Boolean = synchronized(lock) {
            val now = System.currentTimeMillis()
            // 组合金额+商户+类型作为唯一标识
            val key = "$amount|$merchant|$type"
            if (key == lastKey && now - lastTime < Constants.DUPLICATE_CHECK_INTERVAL_MS) {
                false
            } else {
                lastKey = key
                lastTime = now
                true
            }
        }

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            createNotificationChannel()

            // 使用 try-catch 包裹 Repository 初始化，防止崩溃
            try {
                repository = TransactionRepository.getInstance(applicationContext)

                // 初始化规则引擎
                RuleEngine.init(applicationContext)

                // 初始化悬浮窗
                floatingWindow = FloatingConfirmWindow(this)

                // 初始化截图管理器（Android 11+）
                screenCaptureManager = ScreenCaptureManager(this)

                isInitialized = true
                Log.i(TAG, "无障碍服务已启动")

                // 自动开启监听（用户可能通过设置开启了无障碍服务）
                if (!MonitorSettings.isMonitorEnabled(applicationContext)) {
                    MonitorSettings.setMonitorEnabled(applicationContext, true)
                    Log.i(TAG, "自动开启监听功能")
                }

                // 检查OCR功能可用性
                if (screenCaptureManager?.isSupported() == true) {
                    Log.i(TAG, "OCR备用方案已启用 (Android 11+)")
                } else {
                    Log.w(TAG, "OCR备用方案不可用 (需要 Android 11+)")
                }

                showNotification("服务已启动", "正在监听微信、支付宝、云闪付交易")
            } catch (e: Exception) {
                Log.e(TAG, "Repository 初始化失败: ${e.message}", e)
                isInitialized = false
                // 通知用户初始化失败
                showNotification("服务异常", "数据库初始化失败，自动记账功能暂不可用")
            }
        } catch (e: Exception) {
            Log.e(TAG, "服务初始化失败: ${e.message}", e)
            isInitialized = false
            // 通知用户服务初始化失败
            showNotification("服务异常", "无障碍服务初始化失败: ${e.message}")
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "记账通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "自动记账成功通知"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(title: String, content: String) {
        try {
            val nm = notificationManager ?: return
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            // 线程安全的通知ID循环
            val currentId = notifyId.getAndUpdate { id ->
                if (id >= NOTIFICATION_ID_MAX) NOTIFICATION_ID_MIN else id + 1
            }
            nm.notify(currentId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 全局异常保护，防止服务崩溃
        try {
            handleAccessibilityEventSafely(event)
        } catch (e: Exception) {
            Log.e(TAG, "onAccessibilityEvent 异常: ${e.message}", e)
        }
    }

    private fun handleAccessibilityEventSafely(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isInitialized || repository == null) return  // 服务未初始化完成，跳过

        // 检查监听是否已启用
        if (!MonitorSettings.isMonitorEnabled(applicationContext)) {
            return  // 用户未开启监听，跳过
        }

        val pkg = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // 处理通知事件（优先级最高，快速返回）
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (pkg in MONITORED_PACKAGES) {
                try {
                    handleNotification(event, pkg)
                } catch (e: Exception) {
                    Log.e(TAG, "处理通知失败: ${e.message}")
                }
            }
            return
        }

        // 只处理目标App的窗口变化事件
        if (pkg !in MONITORED_PACKAGES) return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        try {
            val root = rootInActiveWindow ?: return

            try {
                // 快速检查：只在可能包含交易信息的页面才收集所有文本
                if (!quickCheckForTransaction(root)) {
                    return
                }

                val texts = collectAllText(root)
                if (texts.isEmpty()) return

                // 1. 优先使用规则引擎解析（通过 TransactionParser）
                val transaction = TransactionParser.parse(texts, pkg)

                if (transaction != null) {
                    // 节点解析成功
                    handleTransactionFound(transaction)
                } else {
                    // 2. 节点解析失败，尝试 OCR 备用方案
                    tryOcrFallback(pkg)
                }
            } finally {
                // API 26-32 需要手动 recycle root 节点
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    try {
                        root.recycle()
                    } catch (e: Exception) {
                        // 忽略 recycle 异常
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理窗口事件出错: ${e.message}")
        }
    }

    /**
     * 处理找到的交易信息
     * 使用悬浮窗让用户确认，或直接保存（降级方案）
     */
    private fun handleTransactionFound(transaction: com.example.localexpense.data.ExpenseEntity) {
        // 防重复检测 - 使用金额+商户+类型组合判断，减少误判
        if (!duplicateChecker.shouldProcess(transaction.amount, transaction.merchant, transaction.type)) return

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "✓ 识别到交易: ${transaction.type} ¥*** [商户已隐藏]")
        }

        // 检查是否有悬浮窗权限
        if (FloatingConfirmWindow.hasPermission(this)) {
            // 显示悬浮窗让用户确认
            floatingWindow?.show(
                transaction = transaction,
                onConfirm = { confirmedTransaction ->
                    // 用户点击确认后才保存
                    saveAndNotify(confirmedTransaction, "记账成功")
                },
                onDismiss = {
                    // 用户取消，不保存
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "用户取消了记账")
                    }
                }
            )
        } else {
            // 没有悬浮窗权限，降级到直接保存 + 通知
            saveAndNotify(transaction, "记账成功", showMerchant = true)
        }
    }

    /**
     * 保存交易并发送通知（统一入口）
     */
    private fun saveAndNotify(
        transaction: com.example.localexpense.data.ExpenseEntity,
        title: String,
        showMerchant: Boolean = false
    ) {
        val repo = repository
        if (repo == null) {
            Log.w(TAG, "Repository 已释放，无法保存交易")
            showNotification("记账失败", "服务未初始化完成")
            return
        }

        repo.insertTransaction(transaction) { errorMessage ->
            // 插入失败时显示错误通知
            showNotification("记账失败", errorMessage)
        }

        val typeText = if (transaction.type == "income") "收入" else "支出"
        val content = if (showMerchant) {
            "$typeText ¥${transaction.amount} - ${transaction.merchant}"
        } else {
            "$typeText ¥${transaction.amount}"
        }
        showNotification(title, content)
    }

    /**
     * OCR 备用方案 (Android 11+)
     * 当节点解析失败时，尝试截图+OCR识别
     * 添加频率限制，防止频繁触发OCR导致性能问题和重复记录
     */
    private fun tryOcrFallback(packageName: String) {
        val captureManager = screenCaptureManager
        if (captureManager == null || !captureManager.isSupported()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OCR 备用方案不可用")
            }
            return
        }

        // OCR频率限制检查
        val now = System.currentTimeMillis()
        if (now - lastOcrTime < OCR_COOLDOWN_MS) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OCR 冷却中，跳过本次识别")
            }
            return
        }
        lastOcrTime = now

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "节点解析失败，尝试 OCR 备用方案...")
        }

        captureManager.captureScreen { bitmap ->
            if (bitmap != null) {
                OcrParser.parseFromBitmap(bitmap, packageName) { ocrTransaction ->
                    if (ocrTransaction != null) {
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "✓ OCR 识别成功")
                        }
                        handleTransactionFound(ocrTransaction)
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "OCR 未能识别到交易信息")
                        }
                    }

                    // 释放 Bitmap
                    bitmap.recycle()
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "截图失败")
                }
            }
        }
    }

    /**
     * 快速检查是否可能是交易页面，避免对所有页面都进行完整文本收集
     */
    private fun quickCheckForTransaction(root: AccessibilityNodeInfo): Boolean {
        // 使用 findAccessibilityNodeInfosByText 快速搜索关键词
        val keywords = listOf("￥", "¥", "元", "红包", "转账", "支付")
        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNotEmpty()) {
                // API 26-32 需要手动 recycle AccessibilityNodeInfo
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    nodes.forEach { node ->
                        try {
                            node.recycle()
                        } catch (e: Exception) {
                            // 忽略 recycle 异常
                        }
                    }
                }
                return true
            }
        }
        return false
    }
    
    private fun handleNotification(event: AccessibilityEvent, pkg: String) {
        if (pkg !in MONITORED_PACKAGES) return

        val text = event.text?.joinToString(" ") ?: return

        // 解析通知中的交易信息
        val transaction = TransactionParser.parseNotification(text, pkg) ?: return

        // 防重复检测 - 使用与窗口事件相同的Key（金额+商户+类型），防止同一笔交易被通知和窗口同时记录
        if (!duplicateChecker.shouldProcess(transaction.amount, transaction.merchant, transaction.type)) return

        saveAndNotify(transaction, "记账成功(通知)")
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源，防止内存泄漏
        try {
            floatingWindow?.dismiss()
            floatingWindow = null
        } catch (e: Exception) {
            Log.e(TAG, "清理悬浮窗失败: ${e.message}")
        }

        try {
            screenCaptureManager?.release()
            screenCaptureManager = null
        } catch (e: Exception) {
            Log.e(TAG, "清理截图管理器失败: ${e.message}")
        }

        try {
            OcrParser.release()
        } catch (e: Exception) {
            Log.e(TAG, "清理 OCR 解析器失败: ${e.message}")
        }

        // 无障碍服务关闭时，自动关闭监听
        try {
            if (MonitorSettings.isMonitorEnabled(applicationContext)) {
                MonitorSettings.setMonitorEnabled(applicationContext, false)
                Log.i(TAG, "自动关闭监听功能")
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭监听失败: ${e.message}")
        }

        repository = null
        notificationManager = null
        isInitialized = false
        Log.i(TAG, "无障碍服务已销毁")
    }

    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        collectTextRecursive(node, result, 0)
        return result
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, list: MutableList<String>, depth: Int) {
        // 深度限制，防止栈溢出
        if (node == null || depth > maxCollectDepth) return
        // 文本数量限制，避免收集过多
        if (list.size >= maxTextCount) return

        // 获取文本
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 500 }?.let {
            list.add(it)
        }

        // 获取内容描述
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 500 }?.let {
            list.add(it)
        }

        // 递归子节点
        val actualChildCount = node.childCount
        val childCount = actualChildCount.coerceAtMost(maxChildCount)

        // 如果有子节点被截断，记录日志（仅调试模式）
        if (BuildConfig.DEBUG && actualChildCount > maxChildCount) {
            Log.w(TAG, "节点子元素过多(${actualChildCount})，已截断至 $maxChildCount")
        }

        for (i in 0 until childCount) {
            // 提前检查文本数量限制
            if (list.size >= maxTextCount) break

            var child: AccessibilityNodeInfo? = null
            try {
                child = node.getChild(i)
                if (child != null) {
                    collectTextRecursive(child, list, depth + 1)
                }
            } catch (e: Exception) {
                // 忽略获取子节点时的异常
            } finally {
                // API 26-32 需要手动 recycle AccessibilityNodeInfo
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    try {
                        child?.recycle()
                    } catch (e: Exception) {
                        // 忽略 recycle 异常
                    }
                }
            }
        }
    }
}
