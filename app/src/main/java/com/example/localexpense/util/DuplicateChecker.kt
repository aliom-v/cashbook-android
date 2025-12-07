package com.example.localexpense.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.LinkedHashMap

/**
 * 交易去重检测器
 *
 * 功能：
 * 1. 基于金额+商户+类型的组合判断重复
 * 2. 使用 LRU 缓存记录最近的多笔交易
 * 3. 支持配置去重时间窗口
 * 4. 线程安全
 *
 * 使用场景：
 * - 无障碍服务检测到交易时，防止重复记录
 * - 通知和窗口事件可能同时触发同一笔交易
 */
class DuplicateChecker(
    private val timeWindowMs: Long = Constants.DUPLICATE_CHECK_INTERVAL_MS,
    private val maxCacheSize: Int = 20
) {
    private val lock = Any()

    // LRU 缓存：key -> 时间戳
    private val cache = object : LinkedHashMap<String, Long>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > maxCacheSize
        }
    }

    // 统计信息
    @Volatile
    private var totalChecks: Long = 0
    @Volatile
    private var duplicatesFound: Long = 0

    /**
     * 检查交易是否应该被处理（非重复）
     *
     * @param amount 交易金额
     * @param merchant 商户名称
     * @param type 交易类型 (expense/income)
     * @return true 表示应该处理，false 表示重复应跳过
     */
    fun shouldProcess(amount: Double, merchant: String, type: String): Boolean {
        val key = generateKey(amount, merchant, type)
        return shouldProcessByKey(key)
    }

    /**
     * 使用预生成的 key 检查重复
     */
    fun shouldProcessByKey(key: String): Boolean = synchronized(lock) {
        totalChecks++
        val now = System.currentTimeMillis()

        // 清理过期记录
        cleanExpired(now)

        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < timeWindowMs) {
            // 重复交易
            duplicatesFound++
            PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
            Logger.d(TAG) { "跳过重复交易: ${Logger.maskText(key)}" }
            false
        } else {
            // 新交易，记录到缓存
            cache[key] = now
            true
        }
    }

    /**
     * 生成交易唯一标识
     * 使用 BigDecimal 确保金额精度
     */
    fun generateKey(amount: Double, merchant: String, type: String): String {
        // 使用 BigDecimal 格式化金额，确保精度一致
        val normalizedAmount = BigDecimal(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .toString()

        // 商户名称标准化：去除空格，转小写
        val normalizedMerchant = merchant.trim().lowercase()

        return "$normalizedAmount|$normalizedMerchant|$type"
    }

    /**
     * 清理过期记录
     */
    private fun cleanExpired(currentTime: Long) {
        val expiredKeys = cache.entries
            .filter { currentTime - it.value >= timeWindowMs }
            .map { it.key }

        expiredKeys.forEach { cache.remove(it) }
    }

    /**
     * 手动标记交易已处理
     * 用于在交易保存成功后立即标记，防止并发重复
     */
    fun markProcessed(amount: Double, merchant: String, type: String) {
        val key = generateKey(amount, merchant, type)
        synchronized(lock) {
            cache[key] = System.currentTimeMillis()
        }
    }

    /**
     * 检查是否为重复交易（不更新缓存）
     */
    fun isDuplicate(amount: Double, merchant: String, type: String): Boolean {
        val key = generateKey(amount, merchant, type)
        return synchronized(lock) {
            val now = System.currentTimeMillis()
            val lastTime = cache[key]
            lastTime != null && now - lastTime < timeWindowMs
        }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): Stats {
        return synchronized(lock) {
            Stats(
                totalChecks = totalChecks,
                duplicatesFound = duplicatesFound,
                cacheSize = cache.size,
                duplicateRate = if (totalChecks > 0) {
                    (duplicatesFound * 100.0 / totalChecks)
                } else 0.0
            )
        }
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        synchronized(lock) {
            totalChecks = 0
            duplicatesFound = 0
        }
    }

    /**
     * 清空缓存
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    /**
     * 统计信息数据类
     */
    data class Stats(
        val totalChecks: Long,
        val duplicatesFound: Long,
        val cacheSize: Int,
        val duplicateRate: Double
    )

    companion object {
        private const val TAG = "DuplicateChecker"

        // 全局单例（用于无障碍服务）
        @Volatile
        private var instance: DuplicateChecker? = null

        /**
         * 获取全局单例
         */
        fun getInstance(): DuplicateChecker {
            return instance ?: synchronized(this) {
                instance ?: DuplicateChecker().also { instance = it }
            }
        }

        /**
         * 重置全局单例（用于测试）
         */
        fun resetInstance() {
            synchronized(this) {
                instance?.clear()
                instance = null
            }
        }
    }
}
