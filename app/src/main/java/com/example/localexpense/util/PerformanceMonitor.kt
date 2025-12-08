package com.example.localexpense.util

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能监控工具类
 *
 * 功能：
 * 1. 方法执行时间追踪
 * 2. 操作计数统计
 * 3. 内存使用监控
 * 4. 性能报告生成
 *
 * 仅在 Debug 模式下收集详细数据，Release 模式下最小化开销
 *
 * 优化特性：
 * - 使用原子操作替代 synchronized，提升并发性能
 * - 使用 ThreadLocal 存储计时起始时间，避免并发问题
 * - ConcurrentHashMap 实现无锁并发访问
 */
object PerformanceMonitor {

    private const val TAG = "PerfMonitor"

    // 是否启用详细监控（仅 Debug 模式）
    private val enabled: Boolean = Logger.isDebug

    // 操作计时器（使用原子类实现线程安全）
    private val timers = ConcurrentHashMap<String, AtomicTimerData>()

    // 操作计数器
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    // 慢操作阈值（毫秒）
    private const val SLOW_THRESHOLD_MS = 100L

    /**
     * 原子计时器数据（使用原子操作替代 synchronized）
     */
    private class AtomicTimerData {
        val totalTime = AtomicLong(0)
        val count = AtomicLong(0)
        val maxTime = AtomicLong(0)
        val minTime = AtomicLong(Long.MAX_VALUE)

        fun record(duration: Long) {
            totalTime.addAndGet(duration)
            count.incrementAndGet()
            // CAS 更新最大值
            var currentMax = maxTime.get()
            while (duration > currentMax) {
                if (maxTime.compareAndSet(currentMax, duration)) break
                currentMax = maxTime.get()
            }
            // CAS 更新最小值
            var currentMin = minTime.get()
            while (duration < currentMin) {
                if (minTime.compareAndSet(currentMin, duration)) break
                currentMin = minTime.get()
            }
        }

        fun getStats(): TimerStats {
            val cnt = count.get()
            return TimerStats(
                totalTime = totalTime.get(),
                count = cnt,
                maxTime = maxTime.get(),
                minTime = if (minTime.get() == Long.MAX_VALUE) 0 else minTime.get(),
                avgTime = if (cnt > 0) totalTime.get() / cnt else 0
            )
        }
    }

    /**
     * 计时器统计数据（只读快照）
     */
    data class TimerStats(
        val totalTime: Long,
        val count: Long,
        val maxTime: Long,
        val minTime: Long,
        val avgTime: Long
    )

    // ========== 计时相关 ==========

    /**
     * 开始计时
     * @param operation 操作名称
     * @return 计时 ID（用于结束计时）
     */
    fun startTimer(operation: String): Long {
        if (!enabled) return 0L
        // 确保计时器存在
        timers.getOrPut(operation) { AtomicTimerData() }
        return SystemClock.elapsedRealtime()
    }

    /**
     * 结束计时
     * @param operation 操作名称
     * @param startTime startTimer 返回的时间戳
     */
    fun endTimer(operation: String, startTime: Long) {
        if (!enabled || startTime == 0L) return

        val endTime = SystemClock.elapsedRealtime()
        val duration = endTime - startTime

        timers[operation]?.record(duration)

        // 记录慢操作
        if (duration > SLOW_THRESHOLD_MS) {
            Logger.w(TAG, "慢操作: $operation 耗时 ${duration}ms")
        }
    }

    /**
     * 计时执行代码块
     */
    inline fun <T> measure(operation: String, block: () -> T): T {
        val start = startTimer(operation)
        try {
            return block()
        } finally {
            endTimer(operation, start)
        }
    }

    /**
     * 计时执行挂起代码块
     */
    suspend inline fun <T> measureSuspend(operation: String, block: suspend () -> T): T {
        val start = startTimer(operation)
        try {
            return block()
        } finally {
            endTimer(operation, start)
        }
    }

    // ========== 计数相关 ==========

    /**
     * 增加计数
     */
    fun increment(name: String, delta: Long = 1) {
        if (!enabled) return
        counters.getOrPut(name) { AtomicLong(0) }.addAndGet(delta)
    }

    /**
     * 获取计数
     */
    fun getCount(name: String): Long {
        return counters[name]?.get() ?: 0
    }

    // ========== 内存监控 ==========

    /**
     * 获取当前内存使用情况
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()

        return MemoryInfo(
            usedMB = usedMemory / (1024 * 1024),
            maxMB = maxMemory / (1024 * 1024),
            usagePercent = (usedMemory * 100 / maxMemory).toInt()
        )
    }

    data class MemoryInfo(
        val usedMB: Long,
        val maxMB: Long,
        val usagePercent: Int
    )

    /**
     * 检查内存压力
     * @return true 表示内存紧张
     */
    fun isMemoryPressure(): Boolean {
        val info = getMemoryInfo()
        return info.usagePercent > 80
    }

    // ========== 报告生成 ==========

    /**
     * 生成性能报告
     */
    fun generateReport(): String {
        if (!enabled) return "性能监控未启用（Release 模式）"

        val sb = StringBuilder()
        sb.appendLine("===== 性能报告 =====")
        sb.appendLine()

        // 内存信息
        val memInfo = getMemoryInfo()
        sb.appendLine("📊 内存使用:")
        sb.appendLine("   已用: ${memInfo.usedMB}MB / ${memInfo.maxMB}MB (${memInfo.usagePercent}%)")
        sb.appendLine()

        // 计时统计（使用新的 AtomicTimerData）
        if (timers.isNotEmpty()) {
            sb.appendLine("⏱️ 操作耗时统计:")
            timers.entries
                .map { (name, data) -> name to data.getStats() }
                .sortedByDescending { it.second.totalTime }
                .forEach { (name, stats) ->
                    sb.appendLine("   $name:")
                    sb.appendLine("      调用次数: ${stats.count}")
                    sb.appendLine("      总耗时: ${stats.totalTime}ms")
                    sb.appendLine("      平均耗时: ${stats.avgTime}ms")
                    sb.appendLine("      最大耗时: ${stats.maxTime}ms")
                    if (stats.minTime > 0) {
                        sb.appendLine("      最小耗时: ${stats.minTime}ms")
                    }
                }
            sb.appendLine()
        }

        // 计数统计
        if (counters.isNotEmpty()) {
            sb.appendLine("📈 计数统计:")
            counters.entries
                .sortedByDescending { it.value.get() }
                .forEach { (name, count) ->
                    sb.appendLine("   $name: ${count.get()}")
                }
            sb.appendLine()
        }

        sb.appendLine("===== 报告结束 =====")
        return sb.toString()
    }

    /**
     * 重置所有统计数据
     */
    fun reset() {
        timers.clear()
        counters.clear()
    }

    /**
     * 打印性能报告到日志
     */
    fun logReport() {
        if (!enabled) return
        Logger.i(TAG, generateReport())
    }

    // ========== 预定义的操作名称 ==========

    object Operations {
        const val PARSE_TRANSACTION = "解析交易"
        const val DB_INSERT = "数据库插入"
        const val DB_QUERY = "数据库查询"
        const val RULE_MATCH = "规则匹配"
        const val OCR_RECOGNIZE = "OCR识别"
        const val ENCRYPT = "加密"
        const val DECRYPT = "解密"
        const val ACCESSIBILITY_EVENT = "无障碍事件处理"
        const val UI_RENDER = "UI渲染"
        const val BACKUP_EXPORT = "备份导出"
        const val BACKUP_IMPORT = "备份导入"
    }

    object Counters {
        const val TRANSACTIONS_RECORDED = "已记录交易数"
        const val DUPLICATES_SKIPPED = "跳过重复数"
        const val PARSE_FAILURES = "解析失败数"
        const val ACCESSIBILITY_EVENTS = "无障碍事件数"
        const val OCR_INVOCATIONS = "OCR调用数"
        const val REGEX_TIMEOUTS = "正则超时数"
    }
}
