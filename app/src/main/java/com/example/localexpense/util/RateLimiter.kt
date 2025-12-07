package com.example.localexpense.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 操作限流器
 *
 * 功能：
 * 1. 防止短时间内重复操作
 * 2. 滑动窗口限流
 * 3. 令牌桶限流
 * 4. 防抖和节流
 *
 * 使用场景：
 * - 防止重复点击
 * - 控制 API 调用频率
 * - 限制数据库操作频率
 * - 防止刷屏/刷数据
 */
object RateLimiter {

    private const val TAG = "RateLimiter"

    // 存储各个 key 的限流器实例
    private val limiters = ConcurrentHashMap<String, Limiter>()
    private val debouncers = ConcurrentHashMap<String, Debouncer>()
    private val throttlers = ConcurrentHashMap<String, Throttler>()

    /**
     * 检查是否允许操作（简单时间窗口）
     *
     * @param key 操作标识
     * @param intervalMs 最小间隔时间（毫秒）
     * @return 是否允许执行
     */
    fun allowAction(key: String, intervalMs: Long): Boolean {
        val limiter = limiters.getOrPut(key) { SimpleLimiter(intervalMs) }
        return (limiter as SimpleLimiter).tryAcquire()
    }

    /**
     * 滑动窗口限流
     *
     * @param key 操作标识
     * @param maxRequests 窗口内最大请求数
     * @param windowMs 窗口大小（毫秒）
     * @return 是否允许执行
     */
    fun allowInWindow(key: String, maxRequests: Int, windowMs: Long): Boolean {
        val limiter = limiters.getOrPut(key) {
            SlidingWindowLimiter(maxRequests, windowMs)
        }
        return (limiter as SlidingWindowLimiter).tryAcquire()
    }

    /**
     * 令牌桶限流
     *
     * @param key 操作标识
     * @param capacity 桶容量
     * @param refillRate 每秒补充的令牌数
     * @return 是否获取到令牌
     */
    fun acquireToken(key: String, capacity: Int, refillRate: Double): Boolean {
        val limiter = limiters.getOrPut(key) {
            TokenBucketLimiter(capacity, refillRate)
        }
        return (limiter as TokenBucketLimiter).tryAcquire()
    }

    /**
     * 防抖：在指定时间内只执行最后一次
     *
     * @param key 操作标识
     * @param delayMs 延迟时间
     * @param action 要执行的操作
     */
    fun debounce(key: String, delayMs: Long, action: () -> Unit) {
        val debouncer = debouncers.getOrPut(key) { Debouncer(delayMs) }
        debouncer.submit(action)
    }

    /**
     * 节流：在指定时间内只执行第一次
     *
     * @param key 操作标识
     * @param intervalMs 间隔时间
     * @param action 要执行的操作
     * @return 是否执行了操作
     */
    fun throttle(key: String, intervalMs: Long, action: () -> Unit): Boolean {
        val throttler = throttlers.getOrPut(key) { Throttler(intervalMs) }
        return throttler.execute(action)
    }

    /**
     * 重置指定 key 的限流器
     */
    fun reset(key: String) {
        limiters.remove(key)
        debouncers.remove(key)
        throttlers.remove(key)
    }

    /**
     * 重置所有限流器
     */
    fun resetAll() {
        limiters.clear()
        debouncers.clear()
        throttlers.clear()
    }

    /**
     * 获取统计信息
     */
    fun getStats(): Map<String, LimiterStats> {
        return limiters.mapValues { (_, limiter) ->
            when (limiter) {
                is SimpleLimiter -> LimiterStats(
                    type = "Simple",
                    totalRequests = limiter.totalRequests.get(),
                    allowedRequests = limiter.allowedRequests.get(),
                    rejectedRequests = limiter.totalRequests.get() - limiter.allowedRequests.get()
                )
                is SlidingWindowLimiter -> LimiterStats(
                    type = "SlidingWindow",
                    totalRequests = limiter.totalRequests.get(),
                    allowedRequests = limiter.allowedRequests.get(),
                    rejectedRequests = limiter.totalRequests.get() - limiter.allowedRequests.get()
                )
                is TokenBucketLimiter -> LimiterStats(
                    type = "TokenBucket",
                    totalRequests = limiter.totalRequests.get(),
                    allowedRequests = limiter.allowedRequests.get(),
                    rejectedRequests = limiter.totalRequests.get() - limiter.allowedRequests.get()
                )
                else -> LimiterStats("Unknown", 0, 0, 0)
            }
        }
    }

    // ==================== 限流器实现 ====================

    private interface Limiter

    /**
     * 简单时间间隔限流器
     */
    private class SimpleLimiter(private val intervalMs: Long) : Limiter {
        private val lastActionTime = AtomicLong(0)
        val totalRequests = AtomicInteger(0)
        val allowedRequests = AtomicInteger(0)

        fun tryAcquire(): Boolean {
            totalRequests.incrementAndGet()
            val now = System.currentTimeMillis()
            val last = lastActionTime.get()

            if (now - last >= intervalMs) {
                if (lastActionTime.compareAndSet(last, now)) {
                    allowedRequests.incrementAndGet()
                    return true
                }
            }
            return false
        }
    }

    /**
     * 滑动窗口限流器
     */
    private class SlidingWindowLimiter(
        private val maxRequests: Int,
        private val windowMs: Long
    ) : Limiter {
        private val timestamps = mutableListOf<Long>()
        private val lock = Any()
        val totalRequests = AtomicInteger(0)
        val allowedRequests = AtomicInteger(0)

        fun tryAcquire(): Boolean {
            totalRequests.incrementAndGet()
            val now = System.currentTimeMillis()

            synchronized(lock) {
                // 移除过期的时间戳
                val cutoff = now - windowMs
                timestamps.removeAll { it < cutoff }

                // 检查是否超过限制
                if (timestamps.size < maxRequests) {
                    timestamps.add(now)
                    allowedRequests.incrementAndGet()
                    return true
                }
            }
            return false
        }
    }

    /**
     * 令牌桶限流器
     */
    private class TokenBucketLimiter(
        private val capacity: Int,
        private val refillRate: Double // 每秒补充的令牌数
    ) : Limiter {
        private var tokens: Double = capacity.toDouble()
        private var lastRefillTime = System.currentTimeMillis()
        private val lock = Any()
        val totalRequests = AtomicInteger(0)
        val allowedRequests = AtomicInteger(0)

        fun tryAcquire(): Boolean {
            totalRequests.incrementAndGet()

            synchronized(lock) {
                refillTokens()

                if (tokens >= 1.0) {
                    tokens -= 1.0
                    allowedRequests.incrementAndGet()
                    return true
                }
            }
            return false
        }

        private fun refillTokens() {
            val now = System.currentTimeMillis()
            val elapsed = (now - lastRefillTime) / 1000.0 // 转换为秒
            val newTokens = elapsed * refillRate

            tokens = minOf(capacity.toDouble(), tokens + newTokens)
            lastRefillTime = now
        }
    }

    /**
     * 防抖器
     */
    private class Debouncer(private val delayMs: Long) {
        private var pendingAction: (() -> Unit)? = null
        private var scheduledTime = 0L
        private val lock = Any()

        fun submit(action: () -> Unit) {
            synchronized(lock) {
                pendingAction = action
                scheduledTime = System.currentTimeMillis() + delayMs
            }

            // 使用 Handler 实现延迟执行
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                executeIfReady()
            }, delayMs)
        }

        private fun executeIfReady() {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                if (now >= scheduledTime && pendingAction != null) {
                    pendingAction?.invoke()
                    pendingAction = null
                }
            }
        }
    }

    /**
     * 节流器
     */
    private class Throttler(private val intervalMs: Long) {
        private val lastExecutionTime = AtomicLong(0)

        fun execute(action: () -> Unit): Boolean {
            val now = System.currentTimeMillis()
            val last = lastExecutionTime.get()

            if (now - last >= intervalMs) {
                if (lastExecutionTime.compareAndSet(last, now)) {
                    action()
                    return true
                }
            }
            return false
        }
    }

    // ==================== 数据类 ====================

    data class LimiterStats(
        val type: String,
        val totalRequests: Int,
        val allowedRequests: Int,
        val rejectedRequests: Int
    ) {
        val allowRate: Float
            get() = if (totalRequests > 0) allowedRequests * 100f / totalRequests else 0f
    }

    // ==================== 预定义的限流 Key ====================

    object Keys {
        const val TRANSACTION_SAVE = "transaction_save"
        const val TRANSACTION_DELETE = "transaction_delete"
        const val SEARCH = "search"
        const val EXPORT = "export"
        const val BACKUP = "backup"
        const val OCR_RECOGNIZE = "ocr_recognize"
        const val ACCESSIBILITY_EVENT = "accessibility_event"
        const val FLOATING_WINDOW_SHOW = "floating_window_show"
        const val DB_OPTIMIZE = "db_optimize"
    }

    // ==================== 便捷方法 ====================

    /**
     * 检查交易保存是否允许（防止重复保存）
     */
    fun allowTransactionSave(): Boolean {
        return allowAction(Keys.TRANSACTION_SAVE, 1000) // 1秒内只允许一次
    }

    /**
     * 检查搜索是否允许（防止频繁搜索）
     */
    fun allowSearch(): Boolean {
        return allowInWindow(Keys.SEARCH, 10, 5000) // 5秒内最多10次
    }

    /**
     * 检查导出是否允许（防止频繁导出）
     */
    fun allowExport(): Boolean {
        return allowAction(Keys.EXPORT, 5000) // 5秒内只允许一次
    }

    /**
     * 检查无障碍事件处理是否允许
     */
    fun allowAccessibilityEvent(): Boolean {
        return allowInWindow(Keys.ACCESSIBILITY_EVENT, 5, 1000) // 1秒内最多5个事件
    }

    /**
     * 检查悬浮窗显示是否允许
     */
    fun allowFloatingWindowShow(): Boolean {
        return allowAction(Keys.FLOATING_WINDOW_SHOW, 2000) // 2秒内只允许一次
    }
}
