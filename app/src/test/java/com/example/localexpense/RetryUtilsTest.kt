package com.example.localexpense

import com.example.localexpense.util.RetryUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * RetryUtils 单元测试
 *
 * 测试覆盖：
 * - 成功执行（无需重试）
 * - 重试后成功
 * - 达到最大重试次数后失败
 * - shouldRetry 条件判断
 * - 指数退避延迟
 * - isRetryableDbException 判断
 * - runCatchingWithDefault 默认值返回
 */
class RetryUtilsTest {

    // ==================== withRetry 测试 ====================

    @Test
    fun `withRetry - 首次成功无需重试`() = runTest {
        var callCount = 0

        val result = RetryUtils.withRetry {
            callCount++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `withRetry - 第二次尝试成功`() = runTest {
        var callCount = 0

        val result = RetryUtils.withRetry(
            maxRetries = 3,
            initialDelayMs = 1L  // 使用最小延迟加速测试
        ) {
            callCount++
            if (callCount < 2) {
                throw RuntimeException("临时错误")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `withRetry - 第三次尝试成功`() = runTest {
        var callCount = 0

        val result = RetryUtils.withRetry(
            maxRetries = 3,
            initialDelayMs = 1L
        ) {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("临时错误")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `withRetry - 达到最大重试次数后抛出异常`() = runTest {
        var callCount = 0

        try {
            RetryUtils.withRetry<String>(
                maxRetries = 2,
                initialDelayMs = 1L
            ) {
                callCount++
                throw RuntimeException("持续错误")
            }
            fail("应该抛出异常")
        } catch (e: RuntimeException) {
            assertEquals("持续错误", e.message)
        }

        // 首次尝试 + 2次重试 = 3次
        assertEquals(3, callCount)
    }

    @Test
    fun `withRetry - shouldRetry 返回 false 时不重试`() = runTest {
        var callCount = 0

        try {
            RetryUtils.withRetry<String>(
                maxRetries = 3,
                initialDelayMs = 1L,
                shouldRetry = { false }  // 不允许重试
            ) {
                callCount++
                throw RuntimeException("错误")
            }
            fail("应该抛出异常")
        } catch (e: RuntimeException) {
            assertEquals("错误", e.message)
        }

        // 只执行一次，不重试
        assertEquals(1, callCount)
    }

    @Test
    fun `withRetry - shouldRetry 根据异常类型判断`() = runTest {
        var callCount = 0

        try {
            RetryUtils.withRetry<String>(
                maxRetries = 3,
                initialDelayMs = 1L,
                shouldRetry = { it is IOException }  // 只重试 IOException
            ) {
                callCount++
                if (callCount == 1) {
                    throw IOException("IO错误")  // 会重试
                }
                throw IllegalStateException("状态错误")  // 不会重试
            }
            fail("应该抛出异常")
        } catch (e: IllegalStateException) {
            assertEquals("状态错误", e.message)
        }

        // 首次 IOException 重试，第二次 IllegalStateException 不重试
        assertEquals(2, callCount)
    }

    @Test
    fun `withRetry - onRetry 回调被调用`() = runTest {
        var callCount = 0
        val retryAttempts = mutableListOf<Int>()

        val result = RetryUtils.withRetry(
            maxRetries = 3,
            initialDelayMs = 1L,
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        ) {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("临时错误")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(listOf(1, 2), retryAttempts)
    }

    @Test
    fun `withRetry - maxRetries 为 0 时不重试`() = runTest {
        var callCount = 0

        try {
            RetryUtils.withRetry<String>(
                maxRetries = 0,
                initialDelayMs = 1L
            ) {
                callCount++
                throw RuntimeException("错误")
            }
            fail("应该抛出异常")
        } catch (e: RuntimeException) {
            assertEquals("错误", e.message)
        }

        assertEquals(1, callCount)
    }

    // ==================== isRetryableDbException 测试 ====================

    @Test
    fun `isRetryableDbException - database is locked 返回 true`() {
        val exception = RuntimeException("database is locked")
        assertTrue(RetryUtils.isRetryableDbException(exception))
    }

    @Test
    fun `isRetryableDbException - SQLITE_BUSY 返回 true`() {
        val exception = RuntimeException("SQLITE_BUSY error")
        assertTrue(RetryUtils.isRetryableDbException(exception))
    }

    @Test
    fun `isRetryableDbException - disk io error 返回 true`() {
        val exception = RuntimeException("disk i/o error")
        assertTrue(RetryUtils.isRetryableDbException(exception))
    }

    @Test
    fun `isRetryableDbException - IOException 返回 true`() {
        val exception = IOException("网络错误")
        assertTrue(RetryUtils.isRetryableDbException(exception))
    }

    @Test
    fun `isRetryableDbException - 普通异常返回 false`() {
        val exception = RuntimeException("普通错误")
        assertFalse(RetryUtils.isRetryableDbException(exception))
    }

    @Test
    fun `isRetryableDbException - 空消息返回 false`() {
        val exception = RuntimeException()
        assertFalse(RetryUtils.isRetryableDbException(exception))
    }

    // ==================== runCatchingWithDefault 测试 ====================

    @Test
    fun `runCatchingWithDefault - 成功时返回结果`() {
        val result = RetryUtils.runCatchingWithDefault("default") {
            "success"
        }
        assertEquals("success", result)
    }

    @Test
    fun `runCatchingWithDefault - 异常时返回默认值`() {
        val result = RetryUtils.runCatchingWithDefault("default") {
            throw RuntimeException("错误")
        }
        assertEquals("default", result)
    }

    @Test
    fun `runCatchingWithDefault - 数值类型默认值`() {
        val result = RetryUtils.runCatchingWithDefault(0) {
            throw RuntimeException("错误")
        }
        assertEquals(0, result)
    }

    @Test
    fun `runCatchingWithDefault - 列表类型默认值`() {
        val result = RetryUtils.runCatchingWithDefault(emptyList<String>()) {
            throw RuntimeException("错误")
        }
        assertTrue(result.isEmpty())
    }

    // ==================== runCatchingWithDefaultSuspend 测试 ====================

    @Test
    fun `runCatchingWithDefaultSuspend - 成功时返回结果`() = runTest {
        val result = RetryUtils.runCatchingWithDefaultSuspend("default") {
            "success"
        }
        assertEquals("success", result)
    }

    @Test
    fun `runCatchingWithDefaultSuspend - 异常时返回默认值`() = runTest {
        val result = RetryUtils.runCatchingWithDefaultSuspend("default") {
            throw RuntimeException("错误")
        }
        assertEquals("default", result)
    }

    // ==================== Defaults 常量测试 ====================

    @Test
    fun `Defaults - 常量值正确`() {
        assertEquals(3, RetryUtils.Defaults.MAX_RETRIES)
        assertEquals(100L, RetryUtils.Defaults.INITIAL_DELAY_MS)
        assertEquals(2000L, RetryUtils.Defaults.MAX_DELAY_MS)
        assertEquals(2.0, RetryUtils.Defaults.BACKOFF_MULTIPLIER, 0.001)
    }
}
