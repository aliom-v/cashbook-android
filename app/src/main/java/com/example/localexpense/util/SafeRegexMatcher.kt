package com.example.localexpense.util

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 安全的正则表达式匹配器
 *
 * 功能：
 * 1. 超时保护 - 防止 ReDoS（正则表达式拒绝服务攻击）
 * 2. 危险模式检测 - 在编译时检查潜在危险的正则
 * 3. 输入长度限制 - 防止超长输入导致的性能问题
 * 4. 线程池复用 - 避免频繁创建线程
 */
object SafeRegexMatcher {

    private const val TAG = "SafeRegexMatcher"

    // 默认超时时间（毫秒）
    private const val DEFAULT_TIMEOUT_MS = 100L

    // 最大输入长度
    private const val MAX_INPUT_LENGTH = 10000

    // 超时计数器（用于监控）
    private val timeoutCount = AtomicInteger(0)

    // 单线程执行器（避免创建过多线程）
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SafeRegexMatcher").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    /**
     * 安全地执行正则匹配，带超时保护
     *
     * @param pattern 正则模式
     * @param input 输入文本
     * @param timeoutMs 超时时间（毫秒）
     * @return MatchResult 如果匹配成功且在超时前完成，否则返回 null
     */
    fun findWithTimeout(
        pattern: Pattern,
        input: CharSequence,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): MatchResult? {
        // 输入长度检查
        if (input.length > MAX_INPUT_LENGTH) {
            Logger.w(TAG, "输入过长(${input.length})，截断处理")
            return findWithTimeoutInternal(pattern, input.subSequence(0, MAX_INPUT_LENGTH), timeoutMs)
        }
        return findWithTimeoutInternal(pattern, input, timeoutMs)
    }

    private fun findWithTimeoutInternal(
        pattern: Pattern,
        input: CharSequence,
        timeoutMs: Long
    ): MatchResult? {
        val future: Future<MatchResult?> = executor.submit(Callable {
            try {
                val matcher = pattern.matcher(input)
                if (matcher.find()) {
                    // 返回匹配结果的快照（因为 Matcher 不是线程安全的）
                    MatchResult(
                        matched = true,
                        start = matcher.start(),
                        end = matcher.end(),
                        groups = (0..matcher.groupCount()).map {
                            try { matcher.group(it) } catch (e: Exception) { null }
                        }
                    )
                } else {
                    MatchResult(matched = false)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "正则匹配异常: ${e.message}")
                null
            }
        })

        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            timeoutCount.incrementAndGet()
            future.cancel(true)
            Logger.w(TAG, "正则匹配超时(${timeoutMs}ms)")
            PerformanceMonitor.increment(PerformanceMonitor.Counters.REGEX_TIMEOUTS)
            null
        } catch (e: Exception) {
            Logger.w(TAG, "正则匹配异常: ${e.message}")
            null
        }
    }

    /**
     * 安全地执行正则全匹配，带超时保护
     */
    fun matchesWithTimeout(
        pattern: Pattern,
        input: CharSequence,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        if (input.length > MAX_INPUT_LENGTH) {
            return false
        }

        val future: Future<Boolean> = executor.submit(Callable {
            try {
                pattern.matcher(input).matches()
            } catch (e: Exception) {
                false
            }
        })

        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            timeoutCount.incrementAndGet()
            future.cancel(true)
            Logger.w(TAG, "正则全匹配超时(${timeoutMs}ms)")
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 安全编译正则表达式，检测潜在危险模式
     *
     * @param regex 正则表达式字符串
     * @return Pattern 如果安全，否则返回 null
     */
    fun safeCompile(regex: String): Pattern? {
        // 检查危险模式
        val dangerCheck = checkDangerousPattern(regex)
        if (dangerCheck.isDangerous) {
            Logger.w(TAG, "检测到危险正则模式: ${dangerCheck.reason}")
            return null
        }

        return try {
            Pattern.compile(regex)
        } catch (e: PatternSyntaxException) {
            Logger.w(TAG, "正则编译失败: ${e.description}")
            null
        }
    }

    /**
     * 检查正则表达式是否包含危险模式
     *
     * 危险模式包括：
     * 1. 嵌套量词：(a+)+, (a*)*
     * 2. 重叠交替：(a|a)+
     * 3. 过度回溯风险
     */
    fun checkDangerousPattern(regex: String): DangerCheckResult {
        // 检查嵌套量词
        val nestedQuantifier = Regex("\\([^)]*[+*][^)]*\\)[+*?]|\\([^)]*\\{[^}]+\\}[^)]*\\)[+*?{]")
        if (nestedQuantifier.containsMatchIn(regex)) {
            return DangerCheckResult(true, "嵌套量词可能导致指数级回溯")
        }

        // 检查重复量词
        val repeatedQuantifier = Regex("[+*]{2,}|\\{\\d+,?\\d*\\}[+*]")
        if (repeatedQuantifier.containsMatchIn(regex)) {
            return DangerCheckResult(true, "重复量词可能导致性能问题")
        }

        // 检查过长的正则
        if (regex.length > 500) {
            return DangerCheckResult(true, "正则表达式过长")
        }

        // 检查过多的分组
        val groupCount = regex.count { it == '(' }
        if (groupCount > 20) {
            return DangerCheckResult(true, "分组过多(${groupCount})")
        }

        return DangerCheckResult(false, null)
    }

    /**
     * 获取超时统计
     */
    fun getTimeoutCount(): Int = timeoutCount.get()

    /**
     * 重置统计
     */
    fun resetStats() {
        timeoutCount.set(0)
    }

    /**
     * 关闭执行器（App 退出时调用）
     */
    fun shutdown() {
        try {
            executor.shutdownNow()
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 匹配结果
     */
    data class MatchResult(
        val matched: Boolean,
        val start: Int = -1,
        val end: Int = -1,
        val groups: List<String?> = emptyList()
    ) {
        fun group(index: Int): String? = groups.getOrNull(index)
    }

    /**
     * 危险检查结果
     */
    data class DangerCheckResult(
        val isDangerous: Boolean,
        val reason: String?
    )
}
