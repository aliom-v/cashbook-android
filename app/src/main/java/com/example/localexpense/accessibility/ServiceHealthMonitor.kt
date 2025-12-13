package com.example.localexpense.accessibility

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.localexpense.util.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 服务健康监控器
 *
 * 职责：
 * 1. 看门狗机制 - 检测服务异常并触发恢复
 * 2. 心跳检测 - 定期检查服务健康状态
 * 3. 统计信息 - 记录事件处理情况
 * 4. 内存监控 - 检测内存压力（v1.9.0）
 * 5. 性能指标 - 记录处理延迟（v1.9.0）
 *
 * 从 ExpenseAccessibilityService 中提取，提高代码可维护性
 *
 * 优化 v1.9.0：
 * - 添加内存压力监控
 * - 添加处理延迟统计
 * - 优化健康报告内容
 */
class ServiceHealthMonitor(
    private val context: Context? = null,
    private val onReinitRequest: () -> Unit,
    private val onHealthCheck: () -> Unit,
    private val onMemoryWarning: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "ServiceHealthMonitor"

        // 看门狗检查间隔
        private const val WATCHDOG_INTERVAL_MS = 20_000L

        // 心跳检测间隔
        private const val HEARTBEAT_INTERVAL_MS = 60_000L

        // 最大连续异常次数
        private const val MAX_CONSECUTIVE_ERRORS = 3

        // 重新初始化冷却时间
        private const val REINIT_COOLDOWN_MS = 30_000L

        // 最大重新初始化尝试次数
        private const val MAX_REINIT_ATTEMPTS = 5

        // 最大冷却时间（5分钟）
        private const val MAX_COOLDOWN_MS = 300_000L

        // 重置计数器的等待时间（10分钟）
        private const val RESET_WAIT_MS = 600_000L

        // 内存警告阈值
        private const val MEMORY_WARNING_THRESHOLD = 0.80
        private const val MEMORY_CRITICAL_THRESHOLD = 0.90

        // 处理延迟警告阈值（毫秒）
        private const val PROCESSING_DELAY_WARNING_MS = 500L
    }

    // 状态标志
    private val isDestroyed = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)

    // 重新初始化控制
    private val reinitAttempts = AtomicInteger(0)
    private val lastReinitTime = AtomicLong(0)

    // 统计计数器
    private val eventCounter = AtomicInteger(0)
    private val consecutiveErrors = AtomicInteger(0)
    private val lastSuccessTime = AtomicLong(System.currentTimeMillis())
    private val lastEventTime = AtomicLong(0)
    private val lastHeartbeatTime = AtomicLong(0)

    // 性能指标（v1.9.0）
    private val totalProcessingTime = AtomicLong(0)
    private val processedTransactions = AtomicInteger(0)
    private val slowProcessingCount = AtomicInteger(0)
    private val memoryWarningCount = AtomicInteger(0)

    // Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var heartbeatRunnable: Runnable? = null

    /**
     * 启动监控
     */
    fun start() {
        if (isDestroyed.get()) return

        startWatchdog()
        startHeartbeat()

        lastHeartbeatTime.set(System.currentTimeMillis())
        Logger.i(TAG, "健康监控已启动")
    }

    /**
     * 停止监控
     */
    fun stop() {
        isDestroyed.set(true)

        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null

        heartbeatRunnable?.let { mainHandler.removeCallbacks(it) }
        heartbeatRunnable = null

        Logger.i(TAG, "健康监控已停止")
    }

    /**
     * 标记初始化完成
     */
    fun markInitialized() {
        isInitialized.set(true)
    }

    /**
     * 标记初始化失败
     */
    fun markInitializationFailed() {
        isInitialized.set(false)
    }

    /**
     * 记录事件
     */
    fun recordEvent() {
        lastEventTime.set(System.currentTimeMillis())
        eventCounter.incrementAndGet()
    }

    /**
     * 记录成功处理
     */
    fun recordSuccess() {
        consecutiveErrors.set(0)
        lastSuccessTime.set(System.currentTimeMillis())
    }

    /**
     * 记录错误
     */
    fun recordError() {
        consecutiveErrors.incrementAndGet()
    }

    /**
     * 记录处理时间（v1.9.0）
     * @param processingTimeMs 处理耗时（毫秒）
     */
    fun recordProcessingTime(processingTimeMs: Long) {
        totalProcessingTime.addAndGet(processingTimeMs)
        processedTransactions.incrementAndGet()

        if (processingTimeMs > PROCESSING_DELAY_WARNING_MS) {
            slowProcessingCount.incrementAndGet()
            Logger.w(TAG, "处理延迟过高: ${processingTimeMs}ms")
        }
    }

    /**
     * 获取是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized.get()

    /**
     * 获取连续错误次数
     */
    fun getConsecutiveErrors(): Int = consecutiveErrors.get()

    /**
     * 获取平均处理时间（v1.9.0）
     */
    fun getAverageProcessingTime(): Long {
        val count = processedTransactions.get()
        return if (count > 0) totalProcessingTime.get() / count else 0
    }

    /**
     * 检查内存压力（v1.9.0）
     * @return 内存使用率 (0.0 - 1.0)
     */
    fun checkMemoryPressure(): Float {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            usedMemory.toFloat() / maxMemory
        } catch (e: Exception) {
            Logger.e(TAG, "检查内存压力失败", e)
            0f
        }
    }

    /**
     * 获取系统内存信息（v1.9.0）
     */
    fun getSystemMemoryInfo(): MemoryInfo? {
        return try {
            context?.let { ctx ->
                val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                MemoryInfo(
                    availableMemoryMB = memInfo.availMem / (1024 * 1024),
                    totalMemoryMB = memInfo.totalMem / (1024 * 1024),
                    lowMemory = memInfo.lowMemory,
                    threshold = memInfo.threshold / (1024 * 1024)
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "获取系统内存信息失败", e)
            null
        }
    }

    /**
     * 获取健康报告
     */
    fun getHealthReport(): HealthReport {
        val now = System.currentTimeMillis()
        val memoryPressure = checkMemoryPressure()

        return HealthReport(
            isInitialized = isInitialized.get(),
            eventCount = eventCounter.get(),
            consecutiveErrors = consecutiveErrors.get(),
            lastEventTime = lastEventTime.get(),
            lastHeartbeatTime = lastHeartbeatTime.get(),
            timeSinceLastEvent = if (lastEventTime.get() > 0) now - lastEventTime.get() else -1,
            timeSinceLastHeartbeat = if (lastHeartbeatTime.get() > 0) now - lastHeartbeatTime.get() else -1,
            timeSinceLastSuccess = now - lastSuccessTime.get(),
            reinitAttempts = reinitAttempts.get(),
            // 新增字段（v1.9.0）
            averageProcessingTime = getAverageProcessingTime(),
            processedTransactions = processedTransactions.get(),
            slowProcessingCount = slowProcessingCount.get(),
            memoryUsage = memoryPressure,
            memoryWarningCount = memoryWarningCount.get()
        )
    }

    /**
     * 重置性能统计（v1.9.0）
     */
    fun resetPerformanceStats() {
        totalProcessingTime.set(0)
        processedTransactions.set(0)
        slowProcessingCount.set(0)
    }

    /**
     * 启动看门狗
     */
    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed.get()) return

                try {
                    checkAndRecover()
                    checkMemoryHealth()
                } catch (e: Exception) {
                    Logger.e(TAG, "看门狗异常", e)
                }

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
                    lastHeartbeatTime.set(System.currentTimeMillis())
                    Logger.d(TAG) { "心跳检测: 服务正常运行中" }

                    // 执行健康检查回调
                    onHealthCheck()
                } catch (e: Exception) {
                    Logger.e(TAG, "心跳检测异常", e)
                }

                if (!isDestroyed.get()) {
                    mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
                }
            }
        }
        mainHandler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * 检查并恢复
     */
    private fun checkAndRecover() {
        val errors = consecutiveErrors.get()
        val eventCount = eventCounter.get()

        Logger.d(TAG) { "看门狗检查: 事件计数=$eventCount, 已初始化=${isInitialized.get()}, 连续错误=$errors" }

        val now = System.currentTimeMillis()
        val shouldReinit = !isInitialized.get() || errors >= MAX_CONSECUTIVE_ERRORS

        if (shouldReinit && !isDestroyed.get()) {
            val timeSinceLastReinit = now - lastReinitTime.get()
            val attempts = reinitAttempts.get()

            // 分级冷却机制
            val currentCooldown = minOf(
                REINIT_COOLDOWN_MS * (1L shl attempts.coerceAtMost(4)),
                MAX_COOLDOWN_MS
            )

            if (attempts >= MAX_REINIT_ATTEMPTS) {
                if (timeSinceLastReinit > RESET_WAIT_MS) {
                    Logger.w(TAG, "重置重新初始化计数器")
                    reinitAttempts.set(0)
                } else {
                    Logger.w(TAG, "已达最大重新初始化次数($attempts)，等待冷却")
                }
            } else if (timeSinceLastReinit > currentCooldown) {
                Logger.w(TAG, "尝试重新初始化 (第${attempts + 1}次，冷却时间=${currentCooldown/1000}秒)")
                lastReinitTime.set(now)
                reinitAttempts.incrementAndGet()
                consecutiveErrors.set(0)
                onReinitRequest()
            } else {
                val remainingCooldown = (currentCooldown - timeSinceLastReinit) / 1000
                Logger.d(TAG) { "等待冷却时间: ${remainingCooldown}秒" }
            }
        }

        // 检查是否长时间没有成功处理
        val timeSinceLastSuccess = now - lastSuccessTime.get()
        if (timeSinceLastSuccess > 3 * 60 * 1000 && isInitialized.get()) {
            Logger.w(TAG, "长时间无成功处理(${timeSinceLastSuccess/1000}秒)")
        }
    }

    /**
     * 检查内存健康状态（v1.9.0）
     */
    private fun checkMemoryHealth() {
        val memoryPressure = checkMemoryPressure()

        when {
            memoryPressure > MEMORY_CRITICAL_THRESHOLD -> {
                memoryWarningCount.incrementAndGet()
                Logger.w(TAG, "内存使用严重过高: ${(memoryPressure * 100).toInt()}%")
                onMemoryWarning?.invoke()
            }
            memoryPressure > MEMORY_WARNING_THRESHOLD -> {
                Logger.w(TAG, "内存使用偏高: ${(memoryPressure * 100).toInt()}%")
            }
        }
    }

    /**
     * 内存信息（v1.9.0）
     */
    data class MemoryInfo(
        val availableMemoryMB: Long,
        val totalMemoryMB: Long,
        val lowMemory: Boolean,
        val threshold: Long
    )

    /**
     * 健康报告
     */
    data class HealthReport(
        val isInitialized: Boolean,
        val eventCount: Int,
        val consecutiveErrors: Int,
        val lastEventTime: Long,
        val lastHeartbeatTime: Long,
        val timeSinceLastEvent: Long,
        val timeSinceLastHeartbeat: Long,
        val timeSinceLastSuccess: Long,
        val reinitAttempts: Int,
        // 新增字段（v1.9.0）
        val averageProcessingTime: Long = 0,
        val processedTransactions: Int = 0,
        val slowProcessingCount: Int = 0,
        val memoryUsage: Float = 0f,
        val memoryWarningCount: Int = 0
    ) {
        val isHealthy: Boolean
            get() = isInitialized &&
                    consecutiveErrors < MAX_CONSECUTIVE_ERRORS &&
                    memoryUsage < MEMORY_CRITICAL_THRESHOLD

        val statusMessage: String
            get() = when {
                !isInitialized -> "服务未初始化"
                consecutiveErrors >= MAX_CONSECUTIVE_ERRORS -> "服务异常，连续错误: $consecutiveErrors"
                memoryUsage > MEMORY_CRITICAL_THRESHOLD -> "内存使用严重过高: ${(memoryUsage * 100).toInt()}%"
                timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * 3 -> "服务可能已停止响应"
                timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * 2 -> "服务响应较慢"
                else -> "服务运行正常"
            }

        val performanceSummary: String
            get() = buildString {
                append("已处理: $processedTransactions 笔")
                if (processedTransactions > 0) {
                    append(", 平均耗时: ${averageProcessingTime}ms")
                }
                if (slowProcessingCount > 0) {
                    append(", 慢处理: $slowProcessingCount 次")
                }
            }
    }
}
