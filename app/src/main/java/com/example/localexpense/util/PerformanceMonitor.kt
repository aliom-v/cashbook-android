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
 */
object PerformanceMonitor {

    private const val TAG = "PerfMonitor"

    // 是否启用详细监控（仅 Debug 模式）
    private val enabled: Boolean = Logger.isDebug

    // 操作计时器
    private val timers = ConcurrentHashMap<String, TimerData>()

    // 操作计数器
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    // 慢操作阈值（毫秒）
    private const val SLOW_THRESHOLD_MS = 100L

    /**
     * 计时器数据
     */
    private data class TimerData(
        var startTime: Long = 0,
        var totalTime: Long = 0,
        var count: Long = 0,
        var maxTime: Long = 0,
        var minTime: Long = Long.MAX_VALUE
    )

    // ========== 计时相关 ==========

    /**
     * 开始计时
     * @param operation 操作名称
     * @return 计时 ID（用于结束计时）
     */
    fun startTimer(operation: String): Long {
        if (!enabled) return 0L

        val startTime = SystemClock.elapsedRealtime()
        timers.getOrPut(operation) { TimerData() }.startTime = startTime
        return startTime
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

        timers[operation]?.let { data ->
            synchronized(data) {
                data.totalTime += duration
                data.count++
                if (duration > data.maxTime) data.maxTime = duration
                if (duration < data.minTime) data.minTime = duration
            }
        }

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

        // 计时统计
        if (timers.isNotEmpty()) {
            sb.appendLine("⏱️ 操作耗时统计:")
            timers.entries
                .sortedByDescending { it.value.totalTime }
                .forEach { (name, data) ->
                    val avgTime = if (data.count > 0) data.totalTime / data.count else 0
                    sb.appendLine("   $name:")
                    sb.appendLine("      调用次数: ${data.count}")
                    sb.appendLine("      总耗时: ${data.totalTime}ms")
                    sb.appendLine("      平均耗时: ${avgTime}ms")
                    sb.appendLine("      最大耗时: ${data.maxTime}ms")
                    if (data.minTime != Long.MAX_VALUE) {
                        sb.appendLine("      最小耗时: ${data.minTime}ms")
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
    }
}
