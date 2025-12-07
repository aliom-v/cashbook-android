package com.example.localexpense.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 主线程守护工具
 *
 * 功能：
 * 1. ANR 检测 - 监控主线程是否被阻塞
 * 2. 安全执行 - 提供带超时的主线程执行方法
 * 3. 线程检查 - 确保代码在正确的线程执行
 *
 * ANR 阈值说明：
 * - Android 系统 ANR 阈值：5秒（前台）/ 200秒（后台广播）
 * - 本工具预警阈值：3秒（提前发现潜在问题）
 */
object MainThreadGuard {

    @PublishedApi
    internal const val TAG = "MainThreadGuard"

    // ANR 预警阈值（毫秒）- 低于系统 5 秒阈值，提前预警
    private const val ANR_WARNING_THRESHOLD_MS = 3000L

    // 心跳检测间隔（毫秒）
    private const val HEARTBEAT_INTERVAL_MS = 1000L

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 监控协程作用域
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 监控任务
    private var monitorJob: Job? = null

    // 上次心跳时间
    private val lastHeartbeatTime = AtomicLong(SystemClock.elapsedRealtime())

    // 是否正在监控
    private val isMonitoring = AtomicBoolean(false)

    // ANR 回调
    private var onAnrDetected: ((Long) -> Unit)? = null

    // ========== 线程检查 ==========

    /**
     * 检查当前是否在主线程
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * 确保在主线程执行，否则抛出异常
     */
    fun ensureMainThread() {
        if (!isMainThread()) {
            throw IllegalStateException("必须在主线程调用此方法，当前线程: ${Thread.currentThread().name}")
        }
    }

    /**
     * 确保不在主线程执行，否则抛出异常
     */
    fun ensureNotMainThread() {
        if (isMainThread()) {
            throw IllegalStateException("不能在主线程调用此方法")
        }
    }

    // ========== 安全执行 ==========

    /**
     * 在主线程执行代码
     * 如果当前就在主线程，直接执行
     */
    fun runOnMainThread(action: () -> Unit) {
        if (isMainThread()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    /**
     * 在主线程延迟执行
     */
    fun postDelayed(delayMs: Long, action: () -> Unit) {
        mainHandler.postDelayed(action, delayMs)
    }

    /**
     * 移除待执行的任务
     */
    fun removeCallbacks(action: Runnable) {
        mainHandler.removeCallbacks(action)
    }

    /**
     * 安全地在主线程执行，带异常捕获
     */
    fun safeRunOnMainThread(action: () -> Unit) {
        runOnMainThread {
            try {
                action()
            } catch (e: Exception) {
                Logger.e(TAG, "主线程执行异常", e)
            }
        }
    }

    // ========== ANR 监控 ==========

    /**
     * 开始 ANR 监控
     * 仅在 Debug 模式下有效
     *
     * @param onAnr ANR 检测回调，参数为阻塞时长（毫秒）
     */
    fun startMonitoring(onAnr: ((Long) -> Unit)? = null) {
        if (!Logger.isDebug) return
        if (isMonitoring.getAndSet(true)) return

        onAnrDetected = onAnr
        lastHeartbeatTime.set(SystemClock.elapsedRealtime())

        // 启动心跳
        startHeartbeat()

        // 启动监控
        monitorJob = monitorScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                checkHeartbeat()
            }
        }

        Logger.i(TAG, "ANR 监控已启动")
    }

    /**
     * 停止 ANR 监控
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) return

        monitorJob?.cancel()
        monitorJob = null
        mainHandler.removeCallbacksAndMessages(null)

        Logger.i(TAG, "ANR 监控已停止")
    }

    /**
     * 启动心跳（在主线程定期更新时间戳）
     */
    private fun startHeartbeat() {
        val heartbeatRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring.get()) return

                lastHeartbeatTime.set(SystemClock.elapsedRealtime())
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS / 2)
            }
        }
        mainHandler.post(heartbeatRunnable)
    }

    /**
     * 检查心跳（在后台线程检测主线程是否响应）
     */
    private fun checkHeartbeat() {
        val now = SystemClock.elapsedRealtime()
        val lastBeat = lastHeartbeatTime.get()
        val blockTime = now - lastBeat

        if (blockTime > ANR_WARNING_THRESHOLD_MS) {
            Logger.e(TAG, "⚠️ ANR 预警！主线程已阻塞 ${blockTime}ms")
            onAnrDetected?.invoke(blockTime)

            // 尝试获取主线程堆栈
            dumpMainThreadStack()
        }
    }

    /**
     * 打印主线程堆栈
     */
    private fun dumpMainThreadStack() {
        try {
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace

            val sb = StringBuilder()
            sb.appendLine("主线程堆栈:")
            stackTrace.take(20).forEach { element ->
                sb.appendLine("    at $element")
            }

            Logger.e(TAG, sb.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "获取主线程堆栈失败", e)
        }
    }

    // ========== 耗时操作检测 ==========

    /**
     * 包装可能耗时的操作，检测执行时间
     * 如果执行时间超过阈值，记录警告
     *
     * @param operationName 操作名称
     * @param thresholdMs 警告阈值（毫秒）
     * @param action 要执行的操作
     */
    inline fun <T> measureOperation(
        operationName: String,
        thresholdMs: Long = 100,
        action: () -> T
    ): T {
        val startTime = SystemClock.elapsedRealtime()
        try {
            return action()
        } finally {
            val duration = SystemClock.elapsedRealtime() - startTime
            if (duration > thresholdMs) {
                val threadInfo = if (isMainThread()) "[主线程]" else "[${Thread.currentThread().name}]"
                Logger.w(TAG, "$threadInfo 操作 '$operationName' 耗时 ${duration}ms (阈值: ${thresholdMs}ms)")

                // 如果在主线程且耗时较长，记录堆栈
                if (isMainThread() && duration > 500) {
                    val stackTrace = Thread.currentThread().stackTrace
                        .drop(2) // 跳过 getStackTrace 和 measureOperation
                        .take(10)
                        .joinToString("\n    at ")
                    Logger.w(TAG, "调用堆栈:\n    at $stackTrace")
                }
            }
        }
    }

    // ========== 调试工具 ==========

    /**
     * 获取当前线程信息
     */
    fun getThreadInfo(): String {
        val thread = Thread.currentThread()
        return buildString {
            append("线程: ${thread.name}")
            append(", ID: ${thread.id}")
            append(", 优先级: ${thread.priority}")
            append(", 是否主线程: ${isMainThread()}")
        }
    }
}
