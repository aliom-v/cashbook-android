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
 * 7. 自动恢复 - 服务异常时自动重启
 * 8. 心跳检测 - 定期检查服务健康状态
 */
class ExpenseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ExpenseService"
        private const val FOREGROUND_NOTIFICATION_ID = 10001

        // 监听的App包名
        private val MONITORED_PACKAGES = PackageNames.MONITORED_PACKAGES

        // 看门狗检查间隔
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        // 心跳检测间隔
        private const val HEARTBEAT_INTERVAL_MS = 60_000L

        // 最大连续异常次数（超过则重新初始化）
        private const val MAX_CONSECUTIVE_ERRORS = 5

        // 重新初始化冷却时间（毫秒）
        private const val REINIT_COOLDOWN_MS = 60_000L

        // 最大重新初始化尝试次数
        private const val MAX_REINIT_ATTEMPTS = 3

        // 文本收集超时时间（毫秒）
        private const val TEXT_COLLECT_TIMEOUT_MS = 2000L

        // 服务状态（用于外部查询）
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var lastEventTime = 0L
            private set

        @Volatile
        var lastHeartbeatTime = 0L
            private set

        /**
         * 获取服务健康状态报告
         */
        fun getHealthReport(): ServiceHealthReport {
            return ServiceHealthReport(
                isRunning = isRunning,
                lastEventTime = lastEventTime,
                lastHeartbeatTime = lastHeartbeatTime,
                timeSinceLastEvent = if (lastEventTime > 0) System.currentTimeMillis() - lastEventTime else -1,
                timeSinceLastHeartbeat = if (lastHeartbeatTime > 0) System.currentTimeMillis() - lastHeartbeatTime else -1
            )
        }
    }

    /**
     * 服务健康状态报告
     */
    data class ServiceHealthReport(
        val isRunning: Boolean,
        val lastEventTime: Long,
        val lastHeartbeatTime: Long,
        val timeSinceLastEvent: Long,
        val timeSinceLastHeartbeat: Long
    ) {
        val isHealthy: Boolean
            get() = isRunning && (timeSinceLastHeartbeat < HEARTBEAT_INTERVAL_MS * 2)

        val statusMessage: String
            get() = when {
                !isRunning -> "服务未运行"
                timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * 3 -> "服务可能已停止响应"
                timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * 2 -> "服务响应较慢"
                else -> "服务运行正常"
            }
    }

    // 初始化状态
    private val isInitialized = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    // 重新初始化控制
    private val reinitAttempts = AtomicInteger(0)
    private val lastReinitTime = AtomicLong(0)

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

    // 连续异常计数器
    private val consecutiveErrors = AtomicInteger(0)

    // 最后一次成功处理时间
    private val lastSuccessTime = AtomicLong(System.currentTimeMillis())

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 后台工作线程
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    // 看门狗
    private var watchdogRunnable: Runnable? = null

    // 心跳检测
    private var heartbeatRunnable: Runnable? = null

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

            // 启动心跳检测
            startHeartbeat()

            isRunning = true
            lastHeartbeatTime = System.currentTimeMillis()
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
                    val errors = consecutiveErrors.get()
                    Logger.d(TAG) { "看门狗检查: 事件计数=$eventCount, 已初始化=${isInitialized.get()}, 连续错误=$errors" }

                    // 检查是否需要重新初始化（带冷却时间保护）
                    val now = System.currentTimeMillis()
                    val shouldReinit = !isInitialized.get() || errors >= MAX_CONSECUTIVE_ERRORS

                    if (shouldReinit && !isDestroyed.get()) {
                        val timeSinceLastReinit = now - lastReinitTime.get()
                        val attempts = reinitAttempts.get()

                        if (attempts >= MAX_REINIT_ATTEMPTS) {
                            // 达到最大尝试次数，等待更长的冷却时间
                            if (timeSinceLastReinit > REINIT_COOLDOWN_MS * 5) {
                                Logger.w(TAG, "重置重新初始化计数器")
                                reinitAttempts.set(0)
                            } else {
                                Logger.w(TAG, "已达最大重新初始化次数($attempts)，等待冷却")
                            }
                        } else if (timeSinceLastReinit > REINIT_COOLDOWN_MS) {
                            Logger.w(TAG, "尝试重新初始化 (第${attempts + 1}次)")
                            lastReinitTime.set(now)
                            reinitAttempts.incrementAndGet()
                            consecutiveErrors.set(0)
                            workerHandler?.post { reinitializeComponents() }
                        } else {
                            Logger.d(TAG) { "等待冷却时间: ${(REINIT_COOLDOWN_MS - timeSinceLastReinit) / 1000}秒" }
                        }
                    }

                    // 检查是否长时间没有成功处理
                    val timeSinceLastSuccess = now - lastSuccessTime.get()
                    if (timeSinceLastSuccess > 5 * 60 * 1000 && isInitialized.get()) {
                        Logger.w(TAG, "长时间无成功处理(${timeSinceLastSuccess/1000}秒)，检查服务状态")
                        // 重置规则引擎
                        try {
                            RuleEngine.init(applicationContext)
                        } catch (e: Exception) {
                            Logger.e(TAG, "重新初始化规则引擎失败", e)
                        }
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

    /**
     * 启动心跳检测
     */
    private fun startHeartbeat() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed.get()) return

                try {
                    lastHeartbeatTime = System.currentTimeMillis()
                    Logger.d(TAG) { "心跳检测: 服务正常运行中" }

                    // 检查关键组件状态
                    if (repository == null && isInitialized.get()) {
                        Logger.w(TAG, "检测到 Repository 为空，尝试恢复")
                        repository = TransactionRepository.getInstance(applicationContext)
                    }

                    // 检查事件处理延迟
                    val timeSinceLastEvent = System.currentTimeMillis() - lastEventTime
                    if (lastEventTime > 0 && timeSinceLastEvent > 5 * 60 * 1000) {
                        // 超过5分钟没有事件，可能需要检查
                        Logger.d(TAG) { "长时间未收到事件 (${timeSinceLastEvent / 1000}秒)" }
                    }

                    // 检查规则引擎状态
                    if (!RuleEngine.isInitialized()) {
                        Logger.w(TAG, "规则引擎未初始化，尝试重新初始化")
                        try {
                            RuleEngine.init(applicationContext)
                        } catch (e: Exception) {
                            Logger.e(TAG, "规则引擎重新初始化失败", e)
                        }
                    }

                    // 注意：移除 System.gc() 调用，依赖系统自动GC管理
                    // 频繁调用 GC 会导致性能问题和电量消耗

                } catch (e: Exception) {
                    Logger.e(TAG, "心跳检测异常", e)
                }

                // 继续下一次检查
                if (!isDestroyed.get()) {
                    mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
                }
            }
        }
        mainHandler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * 重新初始化组件（用于恢复异常状态）
     */
    private fun reinitializeComponents() {
        Logger.i(TAG, "开始重新初始化组件")
        try {
            // 清理旧资源
            repository = null

            // 重新初始化
            initializeComponents()

            Logger.i(TAG, "重新初始化完成")
        } catch (e: Exception) {
            Logger.e(TAG, "重新初始化失败", e)
            CrashReporter.logException(e, TAG)
        }
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
            // 添加空值检查
            if (isDestroyed.get()) return

            root = try {
                rootInActiveWindow
            } catch (e: Exception) {
                Logger.w(TAG, "获取根节点失败: ${e.message}")
                null
            }

            if (root == null) return

            // 快速检查
            if (!quickCheckForTransaction(root)) {
                return
            }

            // 收集文本
            val texts = collectAllText(root)
            if (texts.isEmpty()) return

            // 解析交易
            val transaction = try {
                TransactionParser.parse(texts, pkg)
            } catch (e: Exception) {
                Logger.e(TAG, "解析交易异常", e)
                null
            }

            if (transaction != null) {
                handleTransactionFound(transaction)
                // 重置错误计数，更新成功时间
                consecutiveErrors.set(0)
                lastSuccessTime.set(System.currentTimeMillis())
            } else {
                // OCR 备用方案
                tryOcrFallbackSafely(pkg)
            }

        } catch (e: Exception) {
            Logger.e(TAG, "处理窗口事件异常", e)
            consecutiveErrors.incrementAndGet()
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
        // 防重复（使用渠道信息和原始文本进行差异化去重）
        if (!duplicateChecker.shouldProcess(
                transaction.amount,
                transaction.merchant,
                transaction.type,
                transaction.channel,
                transaction.rawText
            )) {
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
     * 收集所有文本（带超时保护）
     */
    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        val startTime = System.currentTimeMillis()

        try {
            collectTextRecursiveWithTimeout(node, result, 0, startTime)
        } catch (e: Exception) {
            Logger.e(TAG, "收集文本异常", e)
        }

        return result
    }

    private fun collectTextRecursiveWithTimeout(
        node: AccessibilityNodeInfo?,
        list: MutableList<String>,
        depth: Int,
        startTime: Long
    ) {
        if (node == null) return
        if (depth > Constants.MAX_NODE_COLLECT_DEPTH) return
        if (list.size >= Constants.MAX_COLLECTED_TEXT_COUNT) return

        // 超时检查
        if (System.currentTimeMillis() - startTime > TEXT_COLLECT_TIMEOUT_MS) {
            Logger.w(TAG, "文本收集超时，已收集 ${list.size} 条")
            return
        }

        // 内存压力检查（提前触发，避免OOM）
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        if (usedMemory > maxMemory * 0.75) {  // 75%阈值，提前响应
            Logger.w(TAG, "内存压力过大(${usedMemory * 100 / maxMemory}%)，停止收集")
            return
        }

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

                // 再次检查超时
                if (System.currentTimeMillis() - startTime > TEXT_COLLECT_TIMEOUT_MS) {
                    break
                }

                var child: AccessibilityNodeInfo? = null
                try {
                    child = node.getChild(i)
                    collectTextRecursiveWithTimeout(child, list, depth + 1, startTime)
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

        // 停止心跳检测
        heartbeatRunnable?.let { mainHandler.removeCallbacks(it) }
        heartbeatRunnable = null

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
