package com.example.localexpense.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localexpense.BuildConfig
import com.example.localexpense.R
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.ocr.OcrParser
import com.example.localexpense.ocr.ScreenCaptureManager
import com.example.localexpense.parser.RuleEngine
import com.example.localexpense.parser.TransactionParser
import com.example.localexpense.ui.FloatingConfirmWindow
import com.example.localexpense.ui.MainActivity
import com.example.localexpense.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 无障碍服务 - 自动识别支付交易
 *
 * 稳定性优化：
 * 1. 前台服务模式 - 防止被系统杀死
 * 2. 全面异常捕获 - 防止服务崩溃
 * 3. 支持重新绑定 - onUnbind 返回 true
 * 4. 看门狗机制 - 检测 ANR 并恢复
 * 5. 内存优化 - 及时释放资源
 * 6. 限流保护 - 防止过度消耗资源
 */
class ExpenseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ExpenseService"
        private const val FOREGROUND_NOTIFICATION_ID = 10001

        // 监听的App包名
        private val MONITORED_PACKAGES = PackageNames.MONITORED_PACKAGES

        // 看门狗检查间隔
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        // 服务状态（用于外部查询）
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var lastEventTime = 0L
            private set
    }

    // 初始化状态
    private val isInitialized = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    // 核心组件（使用 lazy 延迟初始化，减少启动时间）
    private var repository: TransactionRepository? = null
    private var floatingWindow: FloatingConfirmWindow? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    // 防重复检测器
    private val duplicateChecker by lazy { DuplicateChecker.getInstance() }

    // 通知ID计数器
    private val notifyIdCounter = AtomicInteger(100)

    // OCR 冷却时间
    private val lastOcrTime = AtomicLong(0)

    // 事件计数器（用于监控）
    private val eventCounter = AtomicInteger(0)

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 后台工作线程
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    // 看门狗
    private var watchdogRunnable: Runnable? = null

    // ==================== 生命周期 ====================

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "onCreate")

        // 初始化工作线程
        initWorkerThread()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.i(TAG, "onServiceConnected")

        if (isDestroyed.get()) {
            Logger.w(TAG, "服务已标记为销毁，跳过初始化")
            return
        }

        try {
            // 启动前台服务（关键：防止被系统杀死）
            startForegroundServiceSafely()

            // 异步初始化组件
            workerHandler?.post {
                initializeComponents()
            }

            // 启动看门狗
            startWatchdog()

            isRunning = true
            Logger.i(TAG, "无障碍服务已连接")

        } catch (e: Exception) {
            Logger.e(TAG, "onServiceConnected 异常", e)
            CrashReporter.logException(e, TAG)
        }
    }

    /**
     * 启动前台服务
     */
    private fun startForegroundServiceSafely() {
        try {
            val notification = buildForegroundNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }

            Logger.d(TAG) { "前台服务已启动" }
        } catch (e: Exception) {
            Logger.e(TAG, "启动前台服务失败", e)
            // 前台服务启动失败不影响核心功能，继续运行
        }
    }

    /**
     * 构建前台服务通知
     */
    private fun buildForegroundNotification(): Notification {
        // 确保通知渠道已创建
        NotificationHelper.createNotificationChannels(this)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationHelper.buildForegroundNotification(this, pendingIntent)
    }

    /**
     * 初始化组件（在工作线程执行）
     */
    private fun initializeComponents() {
        try {
            // 1. 初始化 Repository
            repository = TransactionRepository.getInstance(applicationContext)
            Logger.d(TAG) { "Repository 初始化完成" }

            // 2. 初始化规则引擎
            RuleEngine.init(applicationContext)
            Logger.d(TAG) { "规则引擎初始化完成" }

            // 3. 初始化悬浮窗（在主线程）
            mainHandler.post {
                try {
                    if (!isDestroyed.get()) {
                        floatingWindow = FloatingConfirmWindow(this)
                        Logger.d(TAG) { "悬浮窗初始化完成" }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "悬浮窗初始化失败", e)
                }
            }

            // 4. 初始化截图管理器
            try {
                screenCaptureManager = ScreenCaptureManager(this)
                if (screenCaptureManager?.isSupported() == true) {
                    Logger.d(TAG) { "OCR 备用方案已启用" }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "截图管理器初始化失败", e)
            }

            // 5. 自动开启监听
            if (!MonitorSettings.isMonitorEnabled(applicationContext)) {
                MonitorSettings.setMonitorEnabled(applicationContext, true)
            }

            isInitialized.set(true)
            Logger.i(TAG, "所有组件初始化完成")

        } catch (e: Exception) {
            Logger.e(TAG, "组件初始化失败", e)
            CrashReporter.logException(e, TAG)
            isInitialized.set(false)
        }
    }

    /**
     * 初始化工作线程
     */
    private fun initWorkerThread() {
        workerThread = HandlerThread("AccessibilityWorker", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
        }
        workerHandler = Handler(workerThread!!.looper)
    }

    /**
     * 启动看门狗
     */
    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed.get()) return

                try {
                    // 检查服务状态
                    val eventCount = eventCounter.get()
                    Logger.d(TAG) { "看门狗检查: 事件计数=$eventCount, 已初始化=${isInitialized.get()}" }

                    // 检查是否需要重新初始化
                    if (!isInitialized.get() && !isDestroyed.get()) {
                        Logger.w(TAG, "检测到未初始化状态，尝试重新初始化")
                        workerHandler?.post { initializeComponents() }
                    }

                } catch (e: Exception) {
                    Logger.e(TAG, "看门狗异常", e)
                }

                // 继续下一次检查
                if (!isDestroyed.get()) {
                    mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                }
            }
        }
        mainHandler.postDelayed(watchdogRunnable!!, WATCHDOG_INTERVAL_MS)
    }

    // ==================== 事件处理 ====================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 最外层异常保护
        try {
            handleEventSafely(event)
        } catch (e: Exception) {
            Logger.e(TAG, "onAccessibilityEvent 严重异常", e)
            CrashReporter.logException(e, TAG)
        }
    }

    private fun handleEventSafely(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isInitialized.get()) return
        if (isDestroyed.get()) return

        // 更新最后事件时间
        lastEventTime = System.currentTimeMillis()
        eventCounter.incrementAndGet()

        // 检查监听开关
        if (!MonitorSettings.isMonitorEnabled(applicationContext)) {
            return
        }

        val pkg = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // 限流检查
        if (!RateLimiter.allowAccessibilityEvent()) {
            return
        }

        // 处理通知事件
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (pkg in MONITORED_PACKAGES) {
                handleNotificationSafely(event, pkg)
            }
            return
        }

        // 只处理目标App的窗口事件
        if (pkg !in MONITORED_PACKAGES) return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        // 在工作线程处理窗口事件
        workerHandler?.post {
            handleWindowEventSafely(pkg)
        }
    }

    /**
     * 安全处理窗口事件
     */
    private fun handleWindowEventSafely(pkg: String) {
        var root: AccessibilityNodeInfo? = null

        try {
            root = rootInActiveWindow ?: return

            // 快速检查
            if (!quickCheckForTransaction(root)) {
                return
            }

            // 收集文本
            val texts = collectAllText(root)
            if (texts.isEmpty()) return

            // 解析交易
            val transaction = TransactionParser.parse(texts, pkg)

            if (transaction != null) {
                handleTransactionFound(transaction)
            } else {
                // OCR 备用方案
                tryOcrFallbackSafely(pkg)
            }

        } catch (e: Exception) {
            Logger.e(TAG, "处理窗口事件异常", e)
        } finally {
            recycleNodeSafely(root)
        }
    }

    /**
     * 安全处理通知事件
     */
    private fun handleNotificationSafely(event: AccessibilityEvent, pkg: String) {
        try {
            val text = event.text?.joinToString(" ") ?: return
            val transaction = TransactionParser.parseNotification(text, pkg) ?: return

            if (!duplicateChecker.shouldProcess(transaction.amount, transaction.merchant, transaction.type)) {
                return
            }

            saveTransaction(transaction, "记账成功(通知)")

        } catch (e: Exception) {
            Logger.e(TAG, "处理通知异常", e)
        }
    }

    /**
     * 处理找到的交易
     */
    private fun handleTransactionFound(transaction: ExpenseEntity) {
        // 防重复
        if (!duplicateChecker.shouldProcess(transaction.amount, transaction.merchant, transaction.type)) {
            return
        }

        // 限流
        if (!RateLimiter.allowFloatingWindowShow()) {
            // 限流时直接保存
            saveTransaction(transaction, "记账成功")
            return
        }

        PerformanceMonitor.increment(PerformanceMonitor.Counters.TRANSACTIONS_RECORDED)

        Logger.d(TAG) { "识别到交易: ${transaction.type} ¥${Logger.maskAmount(transaction.amount)}" }

        // 在主线程显示悬浮窗
        mainHandler.post {
            showFloatingWindowSafely(transaction)
        }
    }

    /**
     * 安全显示悬浮窗
     */
    private fun showFloatingWindowSafely(transaction: ExpenseEntity) {
        try {
            if (isDestroyed.get()) return

            if (FloatingConfirmWindow.hasPermission(this)) {
                floatingWindow?.show(
                    transaction = transaction,
                    onConfirm = { confirmed ->
                        workerHandler?.post {
                            saveTransaction(confirmed, "记账成功")
                        }
                    },
                    onDismiss = {
                        Logger.d(TAG) { "用户取消记账" }
                    }
                )
            } else {
                // 无悬浮窗权限，直接保存
                workerHandler?.post {
                    saveTransaction(transaction, "记账成功", showMerchant = true)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "显示悬浮窗异常", e)
            // 降级处理
            workerHandler?.post {
                saveTransaction(transaction, "记账成功")
            }
        }
    }

    /**
     * 安全执行 OCR 备用方案
     */
    private fun tryOcrFallbackSafely(pkg: String) {
        try {
            val capture = screenCaptureManager
            if (capture == null || !capture.isSupported()) return

            // 冷却检查
            val now = System.currentTimeMillis()
            val lastTime = lastOcrTime.get()
            if (now - lastTime < Constants.OCR_COOLDOWN_MS) {
                return
            }
            if (!lastOcrTime.compareAndSet(lastTime, now)) {
                return // CAS 失败，其他线程正在处理
            }

            Logger.d(TAG) { "尝试 OCR 备用方案" }

            capture.captureScreen { bitmap ->
                if (bitmap != null && !isDestroyed.get()) {
                    try {
                        OcrParser.parseFromBitmap(bitmap, pkg) { ocrTransaction ->
                            if (ocrTransaction != null) {
                                handleTransactionFound(ocrTransaction)
                            }
                            // 释放 Bitmap
                            if (!bitmap.isRecycled) {
                                bitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "OCR 处理异常", e)
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "OCR 备用方案异常", e)
        }
    }

    /**
     * 保存交易
     */
    private fun saveTransaction(transaction: ExpenseEntity, title: String, showMerchant: Boolean = false) {
        try {
            val repo = repository
            if (repo == null) {
                Logger.w(TAG, "Repository 不可用")
                return
            }

            repo.insertTransaction(transaction) { error ->
                Logger.e(TAG, "保存交易失败: $error")
                showNotificationSafely("记账失败", error)
            }

            val typeText = if (transaction.type == "income") "收入" else "支出"
            val content = if (showMerchant) {
                "$typeText ¥${transaction.amount} - ${transaction.merchant}"
            } else {
                "$typeText ¥${transaction.amount}"
            }
            showNotificationSafely(title, content)

        } catch (e: Exception) {
            Logger.e(TAG, "保存交易异常", e)
        }
    }

    /**
     * 安全显示通知
     */
    private fun showNotificationSafely(title: String, content: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            NotificationHelper.Builder(this, NotificationHelper.Channels.TRANSACTION)
                .setTitle(title)
                .setText(content)
                .setContentIntent(pendingIntent)
                .show(notifyIdCounter.incrementAndGet())

        } catch (e: Exception) {
            Logger.e(TAG, "显示通知异常", e)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 快速检查是否可能是交易页面
     */
    private fun quickCheckForTransaction(root: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("￥", "¥", "元", "红包", "转账", "支付")
        for (keyword in keywords) {
            try {
                val nodes = root.findAccessibilityNodeInfosByText(keyword)
                if (nodes.isNotEmpty()) {
                    // 回收节点
                    nodes.forEach { recycleNodeSafely(it) }
                    return true
                }
            } catch (e: Exception) {
                // 忽略
            }
        }
        return false
    }

    /**
     * 收集所有文本
     */
    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        try {
            collectTextRecursive(node, result, 0)
        } catch (e: Exception) {
            Logger.e(TAG, "收集文本异常", e)
        }
        return result
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, list: MutableList<String>, depth: Int) {
        if (node == null) return
        if (depth > Constants.MAX_NODE_COLLECT_DEPTH) return
        if (list.size >= Constants.MAX_COLLECTED_TEXT_COUNT) return

        try {
            node.text?.toString()?.trim()?.takeIf {
                it.isNotEmpty() && it.length < Constants.MAX_SINGLE_TEXT_LENGTH
            }?.let { list.add(it) }

            node.contentDescription?.toString()?.trim()?.takeIf {
                it.isNotEmpty() && it.length < Constants.MAX_SINGLE_TEXT_LENGTH
            }?.let { list.add(it) }

            val childCount = node.childCount.coerceAtMost(Constants.MAX_CHILD_NODE_COUNT)
            for (i in 0 until childCount) {
                if (list.size >= Constants.MAX_COLLECTED_TEXT_COUNT) break

                var child: AccessibilityNodeInfo? = null
                try {
                    child = node.getChild(i)
                    collectTextRecursive(child, list, depth + 1)
                } finally {
                    recycleNodeSafely(child)
                }
            }
        } catch (e: Exception) {
            // 忽略单个节点的异常
        }
    }

    /**
     * 安全回收节点
     */
    private fun recycleNodeSafely(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return

        try {
            node.recycle()
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ==================== 生命周期回调 ====================

    override fun onInterrupt() {
        Logger.w(TAG, "服务被中断")
    }

    /**
     * 关键：返回 true 允许重新绑定
     * 这样系统断开连接后可以重新绑定，而不是标记为故障
     */
    override fun onUnbind(intent: Intent?): Boolean {
        Logger.w(TAG, "onUnbind - 允许重新绑定")
        return true // 返回 true 允许 onRebind
    }

    /**
     * 重新绑定时调用
     */
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Logger.i(TAG, "onRebind - 服务重新绑定")

        // 重新初始化
        if (!isInitialized.get() && !isDestroyed.get()) {
            workerHandler?.post { initializeComponents() }
        }

        isRunning = true
    }

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy")
        isDestroyed.set(true)
        isRunning = false

        // 停止看门狗
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null

        // 清理悬浮窗
        try {
            floatingWindow?.onServiceDestroy()
            floatingWindow = null
        } catch (e: Exception) {
            Logger.e(TAG, "清理悬浮窗失败", e)
        }

        // 清理截图管理器
        try {
            screenCaptureManager?.release()
            screenCaptureManager = null
        } catch (e: Exception) {
            Logger.e(TAG, "清理截图管理器失败", e)
        }

        // 清理 OCR
        try {
            OcrParser.release(permanent = true)
        } catch (e: Exception) {
            Logger.e(TAG, "清理 OCR 失败", e)
        }

        // 关闭工作线程
        try {
            workerThread?.quitSafely()
            workerThread = null
            workerHandler = null
        } catch (e: Exception) {
            Logger.e(TAG, "关闭工作线程失败", e)
        }

        // 关闭监听
        try {
            MonitorSettings.setMonitorEnabled(applicationContext, false)
        } catch (e: Exception) {
            // 忽略
        }

        repository = null
        isInitialized.set(false)

        // 停止前台服务
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // 忽略
        }

        super.onDestroy()
        Logger.i(TAG, "服务已销毁")
    }
}
