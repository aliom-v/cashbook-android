package com.example.localexpense

import com.example.localexpense.util.RateLimiter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * RateLimiter 单元测试
 *
 * 测试覆盖：
 * - 简单时间间隔限流 (allowAction)
 * - 滑动窗口限流 (allowInWindow)
 * - 令牌桶限流 (acquireToken)
 * - 节流 (throttle)
 * - 统计信息 (getStats)
 * - 预定义便捷方法
 */
class RateLimiterTest {

    @Before
    fun setUp() {
        RateLimiter.resetAll()
    }

    @After
    fun tearDown() {
        RateLimiter.resetAll()
    }

    // ==================== allowAction 测试 ====================

    @Test
    fun `allowAction - 首次调用返回 true`() {
        assertTrue(RateLimiter.allowAction("test_key", 1000))
    }

    @Test
    fun `allowAction - 间隔内重复调用返回 false`() {
        val key = "test_interval"
        assertTrue(RateLimiter.allowAction(key, 1000))
        assertFalse(RateLimiter.allowAction(key, 1000))
        assertFalse(RateLimiter.allowAction(key, 1000))
    }

    @Test
    fun `allowAction - 不同 key 互不影响`() {
        assertTrue(RateLimiter.allowAction("key1", 1000))
        assertTrue(RateLimiter.allowAction("key2", 1000))
        assertTrue(RateLimiter.allowAction("key3", 1000))
    }

    @Test
    fun `allowAction - 间隔过后允许再次调用`() {
        val key = "test_after_interval"
        assertTrue(RateLimiter.allowAction(key, 10)) // 10ms 间隔
        Thread.sleep(20) // 等待超过间隔
        assertTrue(RateLimiter.allowAction(key, 10))
    }

    @Test
    fun `allowAction - 零间隔总是允许`() {
        val key = "zero_interval"
        assertTrue(RateLimiter.allowAction(key, 0))
        assertTrue(RateLimiter.allowAction(key, 0))
    }

    // ==================== allowInWindow 测试 ====================

    @Test
    fun `allowInWindow - 窗口内未超限返回 true`() {
        val key = "window_test"
        assertTrue(RateLimiter.allowInWindow(key, 5, 1000))
        assertTrue(RateLimiter.allowInWindow(key, 5, 1000))
        assertTrue(RateLimiter.allowInWindow(key, 5, 1000))
    }

    @Test
    fun `allowInWindow - 窗口内超限返回 false`() {
        val key = "window_limit"
        // 允许 3 次
        assertTrue(RateLimiter.allowInWindow(key, 3, 10000))
        assertTrue(RateLimiter.allowInWindow(key, 3, 10000))
        assertTrue(RateLimiter.allowInWindow(key, 3, 10000))
        // 第 4 次被拒绝
        assertFalse(RateLimiter.allowInWindow(key, 3, 10000))
    }

    @Test
    fun `allowInWindow - 窗口过期后重置`() {
        val key = "window_expire"
        // 填满窗口
        assertTrue(RateLimiter.allowInWindow(key, 2, 50))
        assertTrue(RateLimiter.allowInWindow(key, 2, 50))
        assertFalse(RateLimiter.allowInWindow(key, 2, 50))

        // 等待窗口过期
        Thread.sleep(60)

        // 应该可以再次请求
        assertTrue(RateLimiter.allowInWindow(key, 2, 50))
    }

    // ==================== acquireToken 测试 ====================

    @Test
    fun `acquireToken - 有令牌时返回 true`() {
        val key = "token_test"
        assertTrue(RateLimiter.acquireToken(key, 5, 1.0))
    }

    @Test
    fun `acquireToken - 令牌耗尽返回 false`() {
        val key = "token_exhaust"
        // 消耗所有令牌（容量为 2）
        assertTrue(RateLimiter.acquireToken(key, 2, 0.1))
        assertTrue(RateLimiter.acquireToken(key, 2, 0.1))
        // 令牌耗尽
        assertFalse(RateLimiter.acquireToken(key, 2, 0.1))
    }

    @Test
    fun `acquireToken - 令牌会补充`() {
        val key = "token_refill"
        // 消耗令牌
        assertTrue(RateLimiter.acquireToken(key, 1, 100.0)) // 每秒补充 100 个
        assertFalse(RateLimiter.acquireToken(key, 1, 100.0))

        // 等待令牌补充
        Thread.sleep(20) // 20ms 应该补充约 2 个令牌

        assertTrue(RateLimiter.acquireToken(key, 1, 100.0))
    }

    // ==================== throttle 测试 ====================

    @Test
    fun `throttle - 首次执行返回 true`() {
        var executed = false
        val result = RateLimiter.throttle("throttle_test", 1000) {
            executed = true
        }
        assertTrue(result)
        assertTrue(executed)
    }

    @Test
    fun `throttle - 间隔内不执行`() {
        var count = 0
        RateLimiter.throttle("throttle_interval", 1000) { count++ }
        RateLimiter.throttle("throttle_interval", 1000) { count++ }
        RateLimiter.throttle("throttle_interval", 1000) { count++ }

        assertEquals(1, count) // 只执行一次
    }

    @Test
    fun `throttle - 间隔后可再次执行`() {
        var count = 0
        RateLimiter.throttle("throttle_after", 10) { count++ }
        Thread.sleep(20)
        RateLimiter.throttle("throttle_after", 10) { count++ }

        assertEquals(2, count)
    }

    // ==================== reset 测试 ====================

    @Test
    fun `reset - 重置指定 key`() {
        val key = "reset_test"
        assertTrue(RateLimiter.allowAction(key, 10000))
        assertFalse(RateLimiter.allowAction(key, 10000))

        RateLimiter.reset(key)

        assertTrue(RateLimiter.allowAction(key, 10000))
    }

    @Test
    fun `resetAll - 重置所有限流器`() {
        assertTrue(RateLimiter.allowAction("key1", 10000))
        assertTrue(RateLimiter.allowAction("key2", 10000))
        assertFalse(RateLimiter.allowAction("key1", 10000))

        RateLimiter.resetAll()

        assertTrue(RateLimiter.allowAction("key1", 10000))
        assertTrue(RateLimiter.allowAction("key2", 10000))
    }

    // ==================== getStats 测试 ====================

    @Test
    fun `getStats - 返回统计信息`() {
        val key = "stats_test"
        RateLimiter.allowAction(key, 1000)
        RateLimiter.allowAction(key, 1000)
        RateLimiter.allowAction(key, 1000)

        val stats = RateLimiter.getStats()
        assertTrue(stats.containsKey(key))

        val stat = stats[key]!!
        assertEquals("Simple", stat.type)
        assertEquals(3, stat.totalRequests)
        assertEquals(1, stat.allowedRequests)
        assertEquals(2, stat.rejectedRequests)
    }

    @Test
    fun `LimiterStats - allowRate 计算正确`() {
        val key = "rate_test"
        repeat(10) { RateLimiter.allowAction(key, 10000) }

        val stats = RateLimiter.getStats()[key]!!
        // 10 次请求，1 次允许
        assertEquals(10f, stats.allowRate, 0.1f)
    }

    // ==================== Keys 常量测试 ====================

    @Test
    fun `Keys - 常量值正确`() {
        assertEquals("transaction_save", RateLimiter.Keys.TRANSACTION_SAVE)
        assertEquals("transaction_delete", RateLimiter.Keys.TRANSACTION_DELETE)
        assertEquals("search", RateLimiter.Keys.SEARCH)
        assertEquals("export", RateLimiter.Keys.EXPORT)
        assertEquals("backup", RateLimiter.Keys.BACKUP)
        assertEquals("ocr_recognize", RateLimiter.Keys.OCR_RECOGNIZE)
        assertEquals("accessibility_event", RateLimiter.Keys.ACCESSIBILITY_EVENT)
        assertEquals("floating_window_show", RateLimiter.Keys.FLOATING_WINDOW_SHOW)
        assertEquals("db_optimize", RateLimiter.Keys.DB_OPTIMIZE)
    }

    // ==================== 便捷方法测试 ====================

    @Test
    fun `allowTransactionSave - 首次返回 true`() {
        assertTrue(RateLimiter.allowTransactionSave())
    }

    @Test
    fun `allowTransactionSave - 1秒内重复返回 false`() {
        assertTrue(RateLimiter.allowTransactionSave())
        assertFalse(RateLimiter.allowTransactionSave())
    }

    @Test
    fun `allowSearch - 窗口内多次允许`() {
        // 5秒内最多10次
        repeat(10) {
            assertTrue(RateLimiter.allowSearch())
        }
        // 第11次被拒绝
        assertFalse(RateLimiter.allowSearch())
    }

    @Test
    fun `allowExport - 首次返回 true`() {
        assertTrue(RateLimiter.allowExport())
    }

    @Test
    fun `allowExport - 5秒内重复返回 false`() {
        assertTrue(RateLimiter.allowExport())
        assertFalse(RateLimiter.allowExport())
    }

    @Test
    fun `allowAccessibilityEvent - 窗口内多次允许`() {
        // 1秒内最多5个事件
        repeat(5) {
            assertTrue(RateLimiter.allowAccessibilityEvent())
        }
        assertFalse(RateLimiter.allowAccessibilityEvent())
    }

    @Test
    fun `allowFloatingWindowShow - 首次返回 true`() {
        assertTrue(RateLimiter.allowFloatingWindowShow())
    }

    @Test
    fun `allowFloatingWindowShow - 2秒内重复返回 false`() {
        assertTrue(RateLimiter.allowFloatingWindowShow())
        assertFalse(RateLimiter.allowFloatingWindowShow())
    }

    // ==================== 并发安全测试 ====================

    @Test
    fun `allowAction - 并发调用安全`() {
        val key = "concurrent_test"
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = (1..10).map {
            Thread {
                if (RateLimiter.allowAction(key, 10000)) {
                    successCount.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 只有一个线程应该成功
        assertEquals(1, successCount.get())
    }

    @Test
    fun `allowInWindow - 并发调用安全`() {
        val key = "concurrent_window"
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = (1..20).map {
            Thread {
                if (RateLimiter.allowInWindow(key, 5, 10000)) {
                    successCount.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 最多 5 个线程应该成功
        assertTrue(successCount.get() <= 5)
    }
}
