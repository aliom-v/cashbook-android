package com.example.localexpense.util

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 安全的正则表达式匹配器
 *
 * 功能：
 * 1. 超时保护 - 防止 ReDoS（正则表达式拒绝服务攻击）
 * 2. 危险模式检测 - 在编译时检查潜在危险的正则
 * 3. 输入长度限制 - 防止超长输入导致的性能问题
 * 4. 协程并发 - 使用协程替代单线程执行器，提高并发性能
 *
 * 优化说明：
 * - v1.8.1: 改用协程 + 固定线程池，解决单线程串行瓶颈
 * - v1.8.2: 添加结果缓存，避免重复匹配相同的 pattern+input 组合
 * - 支持多个正则匹配并行执行，提升页面解析速度
 */
object SafeRegexMatcher {

    private const val TAG = "SafeRegexMatcher"

    // 默认超时时间（毫秒）
    private const val DEFAULT_TIMEOUT_MS = 100L

    // 最大输入长度
    private const val MAX_INPUT_LENGTH = 10000

    // 线程池大小（根据 CPU 核心数动态调整，最少2个，最多4个）
    private val POOL_SIZE = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    // 缓存大小
    private const val CACHE_SIZE = 128

    // 超时计数器（用于监控）
    private val timeoutCount = AtomicInteger(0)

    // 成功匹配计数器（用于监控）
    private val successCount = AtomicInteger(0)

    // 缓存命中计数器
    private val cacheHitCount = AtomicInteger(0)

    // 固定大小线程池的协程调度器（替代单线程执行器）
    private val regexDispatcher = Executors.newFixedThreadPool(POOL_SIZE) { r ->
        Thread(r, "SafeRegexMatcher").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()

    // ExecutorService 用于同步方法的超时控制（避免 runBlocking）
    private val executorService = Executors.newFixedThreadPool(POOL_SIZE) { r ->
        Thread(r, "SafeRegexSync").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + regexDispatcher)

    // 结果缓存（LRU）
    private val resultCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, MatchResult?>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, MatchResult?>?): Boolean {
                return size > CACHE_SIZE
            }
        }
    )

    /**
     * 生成缓存 key
     */
    private fun cacheKey(pattern: Pattern, input: CharSequence): String {
        return "${pattern.pattern()}:${input.hashCode()}"
    }

    /**
     * 安全地执行正则匹配，带超时保护（同步版本）
     * 在非协程上下文中使用
     * 优化 v1.9.4：
     * - 添加结果缓存
     * - 主线程检测警告（避免 ANR）
     * - 使用 Future 替代 runBlocking（主线程场景更安全）
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
        val safeInput = if (input.length > MAX_INPUT_LENGTH) {
            Logger.w(TAG, "输入过长(${input.length})，截断处理")
            input.subSequence(0, MAX_INPUT_LENGTH)
        } else {
            input
        }

        // 检查缓存
        val key = cacheKey(pattern, safeInput)
        resultCache[key]?.let {
            cacheHitCount.incrementAndGet()
            return it
        }

        // 主线程检测：使用 Future 方式避免 runBlocking 可能导致的问题
        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        if (isMainThread) {
            // 主线程警告（仅 Debug 模式）
            Logger.d(TAG) { "⚠️ 主线程调用 findWithTimeout，建议使用 findWithTimeoutSuspend" }
        }

        return try {
            // 使用 Future 方式执行，比 runBlocking 更安全
            val future = executorService.submit(Callable {
                try {
                    val matcher = pattern.matcher(safeInput)
                    if (matcher.find()) {
                        successCount.incrementAndGet()
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

            future.get(timeoutMs, TimeUnit.MILLISECONDS)?.also {
                // 缓存结果
                resultCache[key] = it
            }
        } catch (e: TimeoutException) {
            timeoutCount.incrementAndGet()
            Logger.w(TAG, "正则匹配超时(${timeoutMs}ms)")
            PerformanceMonitor.increment(PerformanceMonitor.Counters.REGEX_TIMEOUTS)
            null
        } catch (e: Exception) {
            Logger.w(TAG, "正则匹配异常: ${e.message}")
            null
        }
    }

    /**
     * 安全地执行正则匹配，带超时保护（协程版本）
     * 在协程上下文中使用，性能更好
     *
     * @param pattern 正则模式
     * @param input 输入文本
     * @param timeoutMs 超时时间（毫秒）
     * @return MatchResult 如果匹配成功且在超时前完成，否则返回 null
     */
    suspend fun findWithTimeoutSuspend(
        pattern: Pattern,
        input: CharSequence,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): MatchResult? {
        // 输入长度检查
        val safeInput = if (input.length > MAX_INPUT_LENGTH) {
            Logger.w(TAG, "输入过长(${input.length})，截断处理")
            input.subSequence(0, MAX_INPUT_LENGTH)
        } else {
            input
        }

        return try {
            withTimeout(timeoutMs) {
                withContext(regexDispatcher) {
                    try {
                        val matcher = pattern.matcher(safeInput)
                        if (matcher.find()) {
                            successCount.incrementAndGet()
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
                }
            }
        } catch (e: TimeoutCancellationException) {
            timeoutCount.incrementAndGet()
            Logger.w(TAG, "正则匹配超时(${timeoutMs}ms)")
            PerformanceMonitor.increment(PerformanceMonitor.Counters.REGEX_TIMEOUTS)
            null
        } catch (e: Exception) {
            Logger.w(TAG, "正则匹配异常: ${e.message}")
            null
        }
    }

    /**
     * 安全地执行正则全匹配，带超时保护（同步版本）
     * 优化 v1.9.4：使用 Future 替代 runBlocking
     */
    fun matchesWithTimeout(
        pattern: Pattern,
        input: CharSequence,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        if (input.length > MAX_INPUT_LENGTH) {
            return false
        }

        return try {
            val future = executorService.submit(Callable {
                try {
                    pattern.matcher(input).matches()
                } catch (e: Exception) {
                    false
                }
            })
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            timeoutCount.incrementAndGet()
            Logger.w(TAG, "正则全匹配超时(${timeoutMs}ms)")
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 安全地执行正则全匹配，带超时保护（协程版本）
     */
    suspend fun matchesWithTimeoutSuspend(
        pattern: Pattern,
        input: CharSequence,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        if (input.length > MAX_INPUT_LENGTH) {
            return false
        }

        return try {
            withTimeout(timeoutMs) {
                withContext(regexDispatcher) {
                    try {
                        pattern.matcher(input).matches()
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            timeoutCount.incrementAndGet()
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
     * 4. 交替分支过多
     * 5. 灾难性量词组合
     */
    fun checkDangerousPattern(regex: String): DangerCheckResult {
        // 检查嵌套量词（最危险的 ReDoS 模式）
        val nestedQuantifier = Regex("\\([^)]*[+*][^)]*\\)[+*?]|\\([^)]*\\{[^}]+\\}[^)]*\\)[+*?{]")
        if (nestedQuantifier.containsMatchIn(regex)) {
            return DangerCheckResult(true, "嵌套量词可能导致指数级回溯")
        }

        // 检查重复量词
        val repeatedQuantifier = Regex("[+*]{2,}|\\{\\d+,?\\d*\\}[+*]")
        if (repeatedQuantifier.containsMatchIn(regex)) {
            return DangerCheckResult(true, "重复量词可能导致性能问题")
        }

        // 检查灾难性的量词组合（如 .*.*，.+.+ 等）
        val catastrophicPattern = Regex("\\.\\*\\.\\*|\\.\\+\\.\\+|\\.[*+]\\s*\\.[*+]")
        if (catastrophicPattern.containsMatchIn(regex)) {
            return DangerCheckResult(true, "灾难性量词组合可能导致性能问题")
        }

        // 检查过长的正则（降低阈值）
        if (regex.length > 300) {
            return DangerCheckResult(true, "正则表达式过长(${regex.length})")
        }

        // 检查过多的分组
        val groupCount = regex.count { it == '(' }
        if (groupCount > 15) {
            return DangerCheckResult(true, "分组过多(${groupCount})")
        }

        // 检查过多的交替分支（如 (a|b|c|d|e|f|g|h|...) ）
        val maxAlternatives = 20
        val alternativePattern = Regex("\\([^)]*\\|[^)]*\\)")
        alternativePattern.findAll(regex).forEach { match ->
            val altCount = match.value.count { it == '|' } + 1
            if (altCount > maxAlternatives) {
                return DangerCheckResult(true, "交替分支过多($altCount)")
            }
        }

        // 检查回溯边界不清晰的模式
        val ambiguousBacktrack = Regex("\\.[*+].*\\.[*+]")
        if (ambiguousBacktrack.containsMatchIn(regex)) {
            // 如果同时有锚点或明确边界，则允许
            val hasAnchor = regex.contains("^") || regex.contains("$") || regex.contains("\\b")
            if (!hasAnchor) {
                return DangerCheckResult(true, "回溯边界不清晰，建议添加锚点")
            }
        }

        return DangerCheckResult(false, null)
    }

    /**
     * 获取超时统计
     */
    fun getTimeoutCount(): Int = timeoutCount.get()

    /**
     * 获取成功匹配统计
     */
    fun getSuccessCount(): Int = successCount.get()

    /**
     * 获取完整统计信息
     */
    fun getStats(): Stats = Stats(
        timeoutCount = timeoutCount.get(),
        successCount = successCount.get(),
        cacheHitCount = cacheHitCount.get(),
        cacheSize = resultCache.size,
        poolSize = POOL_SIZE
    )

    /**
     * 重置统计
     */
    fun resetStats() {
        timeoutCount.set(0)
        successCount.set(0)
        cacheHitCount.set(0)
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        resultCache.clear()
    }

    /**
     * 关闭执行器（App 退出时调用）
     */
    fun shutdown() {
        try {
            scope.cancel()
            regexDispatcher.close()
            executorService.shutdown()
            resultCache.clear()
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 统计信息
     */
    data class Stats(
        val timeoutCount: Int,
        val successCount: Int,
        val cacheHitCount: Int,
        val cacheSize: Int,
        val poolSize: Int
    ) {
        val cacheHitRate: Double
            get() = if (successCount + cacheHitCount > 0) {
                cacheHitCount * 100.0 / (successCount + cacheHitCount)
            } else 0.0
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
