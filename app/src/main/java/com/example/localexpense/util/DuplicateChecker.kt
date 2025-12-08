package com.example.localexpense.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * 交易去重检测器
 *
 * 功能：
 * 1. 基于金额+商户+类型的组合判断重复
 * 2. 基于原始文本哈希的去重（防止同一页面多次触发）
 * 3. 使用 LRU 缓存记录最近的多笔交易
 * 4. 支持配置去重时间窗口
 * 5. 支持不同渠道差异化去重时间
 * 6. 线程安全
 *
 * 使用场景：
 * - 无障碍服务检测到交易时，防止重复记录
 * - 通知和窗口事件可能同时触发同一笔交易
 * - 支付宝页面刷新导致的重复触发
 */
class DuplicateChecker(
    private val timeWindowMs: Long = Constants.DUPLICATE_CHECK_INTERVAL_MS,
    private val maxCacheSize: Int = 50
) {
    private val lock = Any()

    // LRU 缓存：key -> 时间戳
    private val cache = object : LinkedHashMap<String, Long>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > maxCacheSize
        }
    }

    // 原始文本哈希缓存（用于检测同一页面的多次触发）
    private val rawTextCache = object : LinkedHashMap<String, Long>(maxCacheSize, 0.75f, true) {
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
     * @param channel 渠道（可选，用于差异化去重时间）
     * @param rawText 原始文本（可选，用于检测同一页面）
     * @return true 表示应该处理，false 表示重复应跳过
     */
    fun shouldProcess(
        amount: Double,
        merchant: String,
        type: String,
        channel: String? = null,
        rawText: String? = null
    ): Boolean {
        val key = generateKey(amount, merchant, type)
        val effectiveWindow = getEffectiveTimeWindow(channel)
        return shouldProcessInternal(key, effectiveWindow, rawText)
    }

    /**
     * 获取有效的去重时间窗口
     */
    private fun getEffectiveTimeWindow(channel: String?): Long {
        return when (channel) {
            Channel.ALIPAY -> Constants.ALIPAY_DUPLICATE_CHECK_INTERVAL_MS
            else -> timeWindowMs
        }
    }

    /**
     * 内部处理逻辑
     */
    private fun shouldProcessInternal(key: String, effectiveWindow: Long, rawText: String?): Boolean = synchronized(lock) {
        totalChecks++
        val now = System.currentTimeMillis()

        // 清理过期记录
        cleanExpired(now, effectiveWindow)

        // 1. 首先检查金额+商户+类型组合（优先级最高）
        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < effectiveWindow) {
            // 重复交易
            duplicatesFound++
            PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
            Logger.d(TAG) { "跳过重复交易(金额+商户+类型): ${Logger.maskText(key)}" }
            return false
        }

        // 2. 检查原始文本哈希（如果提供）
        if (!rawText.isNullOrBlank()) {
            // 提取核心内容进行哈希，忽略时间戳等变化部分
            val normalizedText = normalizeRawText(rawText)
            val textHash = hashRawText(normalizedText)
            val lastTextTime = rawTextCache[textHash]
            if (lastTextTime != null && now - lastTextTime < effectiveWindow) {
                // 同一页面内容，跳过
                duplicatesFound++
                PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
                Logger.d(TAG) { "跳过重复交易(原始文本相同)" }
                return false
            }
            // 记录原始文本哈希
            rawTextCache[textHash] = now
        }

        // 新交易，同时记录到两个缓存
        cache[key] = now
        true
    }

    /**
     * 标准化原始文本，移除时间戳等变化部分
     */
    private fun normalizeRawText(rawText: String): String {
        return rawText
            // 移除时间戳格式 (如 12:30, 2024-01-01)
            .replace(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"), "")
            .replace(Regex("\\d{4}-\\d{2}-\\d{2}"), "")
            // 移除多余空格
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 使用预生成的 key 检查重复（向后兼容）
     */
    fun shouldProcessByKey(key: String): Boolean = synchronized(lock) {
        totalChecks++
        val now = System.currentTimeMillis()

        // 清理过期记录
        cleanExpired(now, timeWindowMs)

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
     * 生成原始文本的哈希值
     */
    private fun hashRawText(text: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(text.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 降级到简单哈希
            text.hashCode().toString()
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
    private fun cleanExpired(currentTime: Long, window: Long) {
        val expiredKeys = cache.entries
            .filter { currentTime - it.value >= window }
            .map { it.key }
        expiredKeys.forEach { cache.remove(it) }

        val expiredTextKeys = rawTextCache.entries
            .filter { currentTime - it.value >= window }
            .map { it.key }
        expiredTextKeys.forEach { rawTextCache.remove(it) }
    }

    /**
     * 手动标记交易已处理
     * 用于在交易保存成功后立即标记，防止并发重复
     */
    fun markProcessed(amount: Double, merchant: String, type: String, rawText: String? = null) {
        val key = generateKey(amount, merchant, type)
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cache[key] = now
            if (!rawText.isNullOrBlank()) {
                rawTextCache[hashRawText(rawText)] = now
            }
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
                rawTextCacheSize = rawTextCache.size,
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
            rawTextCache.clear()
        }
    }

    /**
     * 统计信息数据类
     */
    data class Stats(
        val totalChecks: Long,
        val duplicatesFound: Long,
        val cacheSize: Int,
        val rawTextCacheSize: Int = 0,
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
