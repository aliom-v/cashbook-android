package com.example.localexpense.util

import android.util.Log
import kotlinx.coroutines.delay

/**
 * 重试工具类
 *
 * 提供带指数退避的重试机制，用于处理临时性故障
 * 适用于数据库操作、网络请求等可能暂时失败的场景
 */
object RetryUtils {

    @PublishedApi
    internal const val TAG = "RetryUtils"

    /**
     * 默认重试配置
     */
    object Defaults {
        const val MAX_RETRIES = 3
        const val INITIAL_DELAY_MS = 100L
        const val MAX_DELAY_MS = 2000L
        const val BACKOFF_MULTIPLIER = 2.0
    }

    /**
     * 带重试的挂起函数执行器
     *
     * @param maxRetries 最大重试次数（不包括首次尝试）
     * @param initialDelayMs 首次重试前的延迟（毫秒）
     * @param maxDelayMs 最大延迟时间（毫秒）
     * @param backoffMultiplier 退避倍数
     * @param shouldRetry 判断是否应该重试的函数（基于异常类型）
     * @param onRetry 重试时的回调（可选，用于日志记录）
     * @param block 要执行的操作
     * @return 操作结果
     * @throws Exception 如果所有重试都失败
     */
    suspend fun <T> withRetry(
        maxRetries: Int = Defaults.MAX_RETRIES,
        initialDelayMs: Long = Defaults.INITIAL_DELAY_MS,
        maxDelayMs: Long = Defaults.MAX_DELAY_MS,
        backoffMultiplier: Double = Defaults.BACKOFF_MULTIPLIER,
        shouldRetry: (Exception) -> Boolean = { true },
        onRetry: ((attempt: Int, exception: Exception) -> Unit)? = null,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e

                // 检查是否应该重试
                if (attempt >= maxRetries || !shouldRetry(e)) {
                    throw e
                }

                // 回调通知
                onRetry?.invoke(attempt + 1, e)
                Log.w(TAG, "操作失败，准备重试 (${attempt + 1}/$maxRetries): ${e.message}")

                // 延迟后重试
                delay(currentDelay)

                // 计算下次延迟（指数退避）
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        // 理论上不会执行到这里，但为了编译器满意
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    /**
     * 带重试的操作执行器（同步版本，用于非协程环境）
     *
     * 警告：此方法使用 Thread.sleep() 会阻塞当前线程
     * 请优先使用 withRetry() 挂起函数版本
     *
     * @param maxRetries 最大重试次数
     * @param initialDelayMs 首次重试前的延迟
     * @param shouldRetry 判断是否应该重试的函数
     * @param block 要执行的操作
     * @return 操作结果
     */
    @Deprecated(
        message = "请使用 withRetry() 挂起函数版本，此方法会阻塞线程",
        replaceWith = ReplaceWith("withRetry(maxRetries, initialDelayMs, maxDelayMs, backoffMultiplier, shouldRetry) { block() }")
    )
    fun <T> withRetryBlocking(
        maxRetries: Int = Defaults.MAX_RETRIES,
        initialDelayMs: Long = Defaults.INITIAL_DELAY_MS,
        maxDelayMs: Long = Defaults.MAX_DELAY_MS,
        backoffMultiplier: Double = Defaults.BACKOFF_MULTIPLIER,
        shouldRetry: (Exception) -> Boolean = { true },
        block: () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e

                if (attempt >= maxRetries || !shouldRetry(e)) {
                    throw e
                }

                Log.w(TAG, "操作失败，准备重试 (${attempt + 1}/$maxRetries): ${e.message}")
                Thread.sleep(currentDelay)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    /**
     * 判断异常是否为临时性故障（适合重试）
     * 用于数据库操作
     */
    fun isRetryableDbException(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return when {
            // SQLite 锁定错误
            message.contains("database is locked") -> true
            message.contains("sqlite_busy") -> true
            // 临时 IO 错误
            message.contains("disk i/o error") -> true
            // 其他临时错误
            e is java.io.IOException -> true
            else -> false
        }
    }

    /**
     * 安全执行操作，失败时返回默认值
     *
     * @param default 失败时的默认值
     * @param block 要执行的操作
     * @return 操作结果或默认值
     */
    inline fun <T> runCatchingWithDefault(default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "操作失败，返回默认值: ${e.message}")
            default
        }
    }

    /**
     * 安全执行挂起操作，失败时返回默认值
     */
    suspend inline fun <T> runCatchingWithDefaultSuspend(default: T, crossinline block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "操作失败，返回默认值: ${e.message}")
            default
        }
    }
}
