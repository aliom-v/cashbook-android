package com.example.localexpense.accessibility

import com.example.localexpense.util.Logger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 交易诊断工具
 * v1.9.2 新增：用于诊断漏记/多记问题
 *
 * 功能：
 * 1. 记录交易检测的完整流程
 * 2. 统计各阶段的成功/失败率
 * 3. 提供诊断报告帮助定位问题
 */
object TransactionDiagnostics {

    private const val TAG = "TxDiagnostics"

    // 最大保存的诊断记录数
    private const val MAX_RECORDS = 50

    // 诊断记录
    data class DiagnosticRecord(
        val timestamp: Long = System.currentTimeMillis(),
        val stage: Stage,
        val packageName: String,
        val result: Result,
        val reason: String = "",
        val amount: Double? = null,
        val merchant: String? = null
    )

    // 处理阶段
    enum class Stage {
        EVENT_RECEIVED,       // 收到事件
        QUICK_CHECK,          // 快速检查
        EXCLUDE_CHECK,        // 排除关键词检查
        RULE_ENGINE_MATCH,    // 规则引擎匹配
        LEGACY_PARSE,         // 传统解析
        DUPLICATE_CHECK,      // 去重检查
        FLOATING_WINDOW,      // 悬浮窗显示
        SAVE_TRANSACTION      // 保存交易
    }

    // 处理结果
    enum class Result {
        PASSED,   // 通过此阶段
        BLOCKED,  // 被此阶段阻止
        ERROR     // 此阶段出错
    }

    // 诊断记录队列
    private val records = ConcurrentLinkedQueue<DiagnosticRecord>()

    // 统计计数器
    private val eventReceivedCount = AtomicInteger(0)
    private val quickCheckPassedCount = AtomicInteger(0)
    private val quickCheckBlockedCount = AtomicInteger(0)
    private val excludeCheckBlockedCount = AtomicInteger(0)
    private val ruleEngineMatchedCount = AtomicInteger(0)
    private val legacyParseSuccessCount = AtomicInteger(0)
    private val duplicateBlockedCount = AtomicInteger(0)
    private val transactionSavedCount = AtomicInteger(0)
    private val transactionFailedCount = AtomicInteger(0)

    // 最后一次诊断时间
    private val lastDiagnosticTime = AtomicLong(0)

    /**
     * 记录诊断信息
     */
    fun record(
        stage: Stage,
        packageName: String,
        result: Result,
        reason: String = "",
        amount: Double? = null,
        merchant: String? = null
    ) {
        // 更新统计计数器
        updateCounters(stage, result)

        // 保存记录
        val record = DiagnosticRecord(
            stage = stage,
            packageName = packageName,
            result = result,
            reason = reason,
            amount = amount,
            merchant = merchant
        )
        records.offer(record)

        // 限制队列大小
        while (records.size > MAX_RECORDS) {
            records.poll()
        }

        // 打印日志
        if (result == Result.BLOCKED || result == Result.ERROR) {
            Logger.d(TAG) { "[${stage.name}] $result - $reason" }
        }
    }

    /**
     * 更新统计计数器
     */
    private fun updateCounters(stage: Stage, result: Result) {
        when (stage) {
            Stage.EVENT_RECEIVED -> eventReceivedCount.incrementAndGet()
            Stage.QUICK_CHECK -> {
                if (result == Result.PASSED) quickCheckPassedCount.incrementAndGet()
                else quickCheckBlockedCount.incrementAndGet()
            }
            Stage.EXCLUDE_CHECK -> {
                if (result == Result.BLOCKED) excludeCheckBlockedCount.incrementAndGet()
            }
            Stage.RULE_ENGINE_MATCH -> {
                if (result == Result.PASSED) ruleEngineMatchedCount.incrementAndGet()
            }
            Stage.LEGACY_PARSE -> {
                if (result == Result.PASSED) legacyParseSuccessCount.incrementAndGet()
            }
            Stage.DUPLICATE_CHECK -> {
                if (result == Result.BLOCKED) duplicateBlockedCount.incrementAndGet()
            }
            Stage.SAVE_TRANSACTION -> {
                if (result == Result.PASSED) transactionSavedCount.incrementAndGet()
                else transactionFailedCount.incrementAndGet()
            }
            else -> { /* 其他阶段暂不统计 */ }
        }
    }

    /**
     * 获取诊断报告
     */
    fun getDiagnosticReport(): DiagnosticReport {
        lastDiagnosticTime.set(System.currentTimeMillis())

        val total = eventReceivedCount.get()
        val saved = transactionSavedCount.get()

        return DiagnosticReport(
            totalEvents = total,
            quickCheckPassed = quickCheckPassedCount.get(),
            quickCheckBlocked = quickCheckBlockedCount.get(),
            excludeCheckBlocked = excludeCheckBlockedCount.get(),
            ruleEngineMatched = ruleEngineMatchedCount.get(),
            legacyParseSuccess = legacyParseSuccessCount.get(),
            duplicateBlocked = duplicateBlockedCount.get(),
            transactionSaved = saved,
            transactionFailed = transactionFailedCount.get(),
            successRate = if (total > 0) saved * 100.0 / total else 0.0,
            recentRecords = records.toList().takeLast(10)
        )
    }

    /**
     * 获取最近的失败记录
     */
    fun getRecentFailures(): List<DiagnosticRecord> {
        return records.filter { it.result != Result.PASSED }.takeLast(10)
    }

    /**
     * 重置统计
     */
    fun reset() {
        records.clear()
        eventReceivedCount.set(0)
        quickCheckPassedCount.set(0)
        quickCheckBlockedCount.set(0)
        excludeCheckBlockedCount.set(0)
        ruleEngineMatchedCount.set(0)
        legacyParseSuccessCount.set(0)
        duplicateBlockedCount.set(0)
        transactionSavedCount.set(0)
        transactionFailedCount.set(0)
    }

    /**
     * 诊断报告
     */
    data class DiagnosticReport(
        val totalEvents: Int,
        val quickCheckPassed: Int,
        val quickCheckBlocked: Int,
        val excludeCheckBlocked: Int,
        val ruleEngineMatched: Int,
        val legacyParseSuccess: Int,
        val duplicateBlocked: Int,
        val transactionSaved: Int,
        val transactionFailed: Int,
        val successRate: Double,
        val recentRecords: List<DiagnosticRecord>
    ) {
        /**
         * 生成可读的报告文本
         */
        fun toReadableString(): String {
            return buildString {
                appendLine("=== 交易诊断报告 ===")
                appendLine("总事件数: $totalEvents")
                appendLine()
                appendLine("【各阶段统计】")
                appendLine("  快速检查通过: $quickCheckPassed")
                appendLine("  快速检查拦截: $quickCheckBlocked")
                appendLine("  排除关键词拦截: $excludeCheckBlocked")
                appendLine("  规则引擎匹配: $ruleEngineMatched")
                appendLine("  传统解析成功: $legacyParseSuccess")
                appendLine("  重复交易拦截: $duplicateBlocked")
                appendLine()
                appendLine("【最终结果】")
                appendLine("  成功记录: $transactionSaved")
                appendLine("  记录失败: $transactionFailed")
                appendLine("  成功率: ${String.format("%.1f", successRate)}%")
                appendLine()

                if (recentRecords.isNotEmpty()) {
                    appendLine("【最近10条记录】")
                    recentRecords.forEachIndexed { index, record ->
                        val status = when (record.result) {
                            Result.PASSED -> "✓"
                            Result.BLOCKED -> "✗"
                            Result.ERROR -> "!"
                        }
                        appendLine("  $status [${record.stage.name}] ${record.reason}")
                    }
                }
            }
        }

        /**
         * 获取可能的问题原因
         */
        fun getPossibleIssues(): List<String> {
            val issues = mutableListOf<String>()

            // 快速检查拦截率过高
            if (quickCheckBlocked > quickCheckPassed * 2) {
                issues.add("快速检查拦截率过高，可能遗漏有效交易页面")
            }

            // 排除关键词拦截率过高
            if (excludeCheckBlocked > totalEvents * 0.3) {
                issues.add("排除关键词拦截率过高（${excludeCheckBlocked * 100 / totalEvents}%），可能需要调整规则")
            }

            // 规则引擎匹配率低
            if (ruleEngineMatched < quickCheckPassed * 0.1 && quickCheckPassed > 10) {
                issues.add("规则引擎匹配率低，可能需要更新规则配置")
            }

            // 重复交易拦截率高
            if (duplicateBlocked > transactionSaved * 2) {
                issues.add("重复交易拦截较多，如果漏记请检查去重时间窗口设置")
            }

            // 保存失败率高
            if (transactionFailed > transactionSaved * 0.2 && transactionFailed > 0) {
                issues.add("交易保存失败率较高，请检查数据库状态")
            }

            return issues
        }
    }
}
