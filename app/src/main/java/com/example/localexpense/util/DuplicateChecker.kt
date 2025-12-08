package com.example.localexpense.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 交易去重检测器
 *
 * 功能：
 * 1. 基于金额+商户+类型的组合判断重复
 * 2. 基于原始文本哈希的去重（防止同一页面多次触发）
 * 3. 使用高性能缓存记录最近的多笔交易
 * 4. 支持配置去重时间窗口
 * 5. 支持不同渠道差异化去重时间
 * 6. 完全线程安全（使用 ConcurrentHashMap）
 *
 * 使用场景：
 * - 无障碍服务检测到交易时，防止重复记录
 * - 通知和窗口事件可能同时触发同一笔交易
 * - 支付宝页面刷新导致的重复触发
 *
 * 性能优化：
 * - 使用 ConcurrentHashMap 避免全局锁
 * - 延迟清理过期记录，避免每次操作都清理
 * - 增大缓存容量以支持高频交易场景
 */
class DuplicateChecker(
    private val timeWindowMs: Long = Constants.DUPLICATE_CHECK_INTERVAL_MS,
    private val maxCacheSize: Int = 100  // 增加缓存容量
) {
    // 使用 ConcurrentHashMap 提高并发性能
    private val cache = ConcurrentHashMap<String, Long>(maxCacheSize)
    private val rawTextCache = ConcurrentHashMap<String, Long>(maxCacheSize)

    // 处理中的交易（用于防止竞态条件）
    // value: 开始处理的时间戳，用于超时清理
    private val processingCache = ConcurrentHashMap<String, Long>(maxCacheSize)
    private val processingTimeoutMs = 30_000L  // 处理中状态最长持续 30 秒

    // 上次清理时间（用于延迟清理）
    private val lastCleanupTime = AtomicLong(System.currentTimeMillis())
    private val cleanupIntervalMs = 5000L  // 每 5 秒最多清理一次

    // 预编译正则表达式（避免重复编译）
    private val timeRegex = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")
    private val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val whitespaceRegex = Regex("\\s+")

    // 统计信息（使用原子类保证线程安全）
    private val totalChecks = AtomicLong(0)
    private val duplicatesFound = AtomicLong(0)

    /**
     * 检查交易是否应该被处理（非重复）
     * 增强版：支持更精确的去重，包含渠道信息
     *
     * @param amount 交易金额
     * @param merchant 商户名称
     * @param type 交易类型 (expense/income)
     * @param channel 渠道（可选，用于差异化去重时间和更精确的 key）
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
        // 生成包含渠道的 key，避免不同渠道相同金额交易被误判
        val key = if (channel != null) {
            "${generateKey(amount, merchant, type)}|$channel"
        } else {
            generateKey(amount, merchant, type)
        }
        val effectiveWindow = getEffectiveTimeWindow(channel)
        return shouldProcessInternal(key, effectiveWindow, rawText, amount)
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
     * 优化：使用无锁并发，增加金额参数用于更智能的原始文本去重
     */
    private fun shouldProcessInternal(key: String, effectiveWindow: Long, rawText: String?, amount: Double = 0.0): Boolean {
        totalChecks.incrementAndGet()
        val now = System.currentTimeMillis()

        // 延迟清理过期记录（避免每次都清理）
        maybeCleanExpired(now, effectiveWindow)

        // 1. 首先检查金额+商户+类型组合（优先级最高）
        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < effectiveWindow) {
            // 重复交易
            duplicatesFound.incrementAndGet()
            PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
            Logger.d(TAG) { "跳过重复交易(金额+商户+类型): ${Logger.maskText(key)}" }
            return false
        }

        // 2. 检查原始文本哈希（如果提供）
        // 优化：只在原始文本包含金额时才进行哈希去重，避免误判
        if (!rawText.isNullOrBlank() && amount > 0) {
            // 提取核心内容进行哈希，忽略时间戳等变化部分
            // 同时包含金额信息，确保不同金额的交易不会被误判
            val normalizedText = normalizeRawText(rawText, amount)
            val textHash = hashRawText(normalizedText)
            val lastTextTime = rawTextCache[textHash]
            if (lastTextTime != null && now - lastTextTime < effectiveWindow) {
                // 同一页面内容，跳过
                duplicatesFound.incrementAndGet()
                PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
                Logger.d(TAG) { "跳过重复交易(原始文本相同)" }
                return false
            }
            // 记录原始文本哈希
            rawTextCache[textHash] = now
        }

        // 新交易，记录到缓存
        cache[key] = now
        Logger.d(TAG) { "新交易已记录: ${Logger.maskText(key)}" }
        return true
    }

    /**
     * 标准化原始文本，移除时间戳等变化部分
     * 优化：包含金额信息，确保不同金额的交易不会被误判
     */
    private fun normalizeRawText(rawText: String, amount: Double = 0.0): String {
        val base = rawText
            // 移除时间戳格式 (如 12:30, 2024-01-01)
            .replace(timeRegex, "")
            .replace(dateRegex, "")
            // 移除多余空格
            .replace(whitespaceRegex, " ")
            .trim()

        // 如果有金额，加入哈希计算，避免不同金额的交易被误判为重复
        return if (amount > 0) {
            "$base|${BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)}"
        } else {
            base
        }
    }

    /**
     * 使用预生成的 key 检查重复（向后兼容）
     */
    fun shouldProcessByKey(key: String): Boolean {
        totalChecks.incrementAndGet()
        val now = System.currentTimeMillis()

        // 延迟清理过期记录
        maybeCleanExpired(now, timeWindowMs)

        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < timeWindowMs) {
            // 重复交易
            duplicatesFound.incrementAndGet()
            PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
            Logger.d(TAG) { "跳过重复交易: ${Logger.maskText(key)}" }
            return false
        } else {
            // 新交易，记录到缓存
            cache[key] = now
            return true
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
     * 优化：对商户名进行规范化，避免同一交易因商户名差异被重复记录
     */
    fun generateKey(amount: Double, merchant: String, type: String): String {
        // 使用 BigDecimal 格式化金额，确保精度一致
        val normalizedAmount = BigDecimal(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .toString()

        // 商户名称规范化：去除空格，转小写，并规范化常见变体
        val normalizedMerchant = normalizeMerchant(merchant)

        return "$normalizedAmount|$normalizedMerchant|$type"
    }

    /**
     * 规范化商户名称
     * 目的：确保同一交易的商户名即使略有不同也能匹配
     */
    private fun normalizeMerchant(merchant: String): String {
        var normalized = merchant.trim().lowercase()

        // 移除常见前缀/后缀，使不同解析结果能匹配
        // 例如："微信红包" 和 "红包" 应该被认为是同一类型
        val prefixesToRemove = listOf("微信", "支付宝", "云闪付", "发出", "收到")
        val suffixesToRemove = listOf("支付", "收款", "转账给", "的红包")

        for (prefix in prefixesToRemove) {
            if (normalized.startsWith(prefix) && normalized.length > prefix.length) {
                normalized = normalized.removePrefix(prefix)
            }
        }

        for (suffix in suffixesToRemove) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length) {
                normalized = normalized.removeSuffix(suffix)
            }
        }

        // 规范化常见商户名变体
        normalized = when {
            normalized.contains("红包") -> "红包"
            normalized.contains("转账") && !normalized.contains("人") -> "转账"
            normalized == "未知商户" || normalized == "未知" -> "unknown"
            else -> normalized
        }

        return normalized.trim()
    }

    /**
     * 延迟清理过期记录（避免每次操作都清理，提高性能）
     */
    private fun maybeCleanExpired(currentTime: Long, window: Long) {
        val lastCleanup = lastCleanupTime.get()
        if (currentTime - lastCleanup < cleanupIntervalMs) {
            return  // 还没到清理时间
        }

        // CAS 更新清理时间，确保只有一个线程执行清理
        if (!lastCleanupTime.compareAndSet(lastCleanup, currentTime)) {
            return  // 其他线程已经在清理
        }

        // 使用迭代器清理过期记录，避免创建中间列表
        cleanExpiredEntries(cache, currentTime, window)
        cleanExpiredEntries(rawTextCache, currentTime, window)
        cleanExpiredEntries(processingCache, currentTime, processingTimeoutMs)

        // 如果缓存过大，强制清理最旧的记录
        trimCacheIfNeeded(cache, maxCacheSize)
        trimCacheIfNeeded(rawTextCache, maxCacheSize)
    }

    /**
     * 使用迭代器清理过期条目（避免 ConcurrentModificationException）
     */
    private fun cleanExpiredEntries(map: ConcurrentHashMap<String, Long>, currentTime: Long, window: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value >= window) {
                iterator.remove()
            }
        }
    }

    /**
     * 当缓存过大时，清理最旧的记录
     */
    private fun trimCacheIfNeeded(map: ConcurrentHashMap<String, Long>, maxSize: Int) {
        if (map.size <= maxSize) return

        // 找到需要删除的数量
        val toRemoveCount = map.size - maxSize
        if (toRemoveCount <= 0) return

        // 获取最旧的 N 个 key（只在需要时排序）
        val oldestKeys = map.entries
            .sortedBy { it.value }
            .take(toRemoveCount)
            .map { it.key }

        oldestKeys.forEach { map.remove(it) }
    }

    /**
     * 手动标记交易已处理
     * 用于在交易保存成功后立即标记，防止并发重复
     *
     * @param amount 交易金额
     * @param merchant 商户名称
     * @param type 交易类型
     * @param channel 渠道（可选）
     * @param rawText 原始文本（可选）
     */
    fun markProcessed(
        amount: Double,
        merchant: String,
        type: String,
        channel: String? = null,
        rawText: String? = null
    ) {
        val baseKey = generateKey(amount, merchant, type)
        val key = if (channel != null) "$baseKey|$channel" else baseKey
        val now = System.currentTimeMillis()

        // 标记为已处理
        cache[key] = now

        // 移除处理中状态
        processingCache.remove(key)

        if (!rawText.isNullOrBlank()) {
            val normalizedText = normalizeRawText(rawText, amount)
            rawTextCache[hashRawText(normalizedText)] = now
        }

        Logger.d(TAG) { "交易已标记为已处理: ${Logger.maskText(key)}" }
    }

    /**
     * 检查是否为重复交易（不更新缓存）
     * 增强版：支持渠道和原始文本检查
     *
     * @return true 表示是重复交易，false 表示不是重复
     */
    fun isDuplicate(
        amount: Double,
        merchant: String,
        type: String,
        channel: String? = null,
        rawText: String? = null
    ): Boolean {
        val key = if (channel != null) {
            "${generateKey(amount, merchant, type)}|$channel"
        } else {
            generateKey(amount, merchant, type)
        }
        val effectiveWindow = getEffectiveTimeWindow(channel)
        val now = System.currentTimeMillis()

        // 检查主缓存
        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < effectiveWindow) {
            return true
        }

        // 检查原始文本缓存
        if (!rawText.isNullOrBlank() && amount > 0) {
            val normalizedText = normalizeRawText(rawText, amount)
            val textHash = hashRawText(normalizedText)
            val lastTextTime = rawTextCache[textHash]
            if (lastTextTime != null && now - lastTextTime < effectiveWindow) {
                return true
            }
        }

        return false
    }

    /**
     * 检查是否为重复交易（不更新缓存）- 简化版本
     */
    fun isDuplicate(amount: Double, merchant: String, type: String): Boolean {
        val key = generateKey(amount, merchant, type)
        val now = System.currentTimeMillis()
        val lastTime = cache[key]
        return lastTime != null && now - lastTime < timeWindowMs
    }

    /**
     * 原子性地尝试获取交易处理权
     * 解决竞态条件：在检查重复的同时标记为"处理中"
     *
     * @return true 表示成功获取处理权（非重复且未被其他线程处理），false 表示应跳过
     */
    fun tryAcquireForProcessing(
        amount: Double,
        merchant: String,
        type: String,
        channel: String? = null,
        rawText: String? = null
    ): Boolean {
        val baseKey = generateKey(amount, merchant, type)
        val key = if (channel != null) "$baseKey|$channel" else baseKey
        val effectiveWindow = getEffectiveTimeWindow(channel)
        val now = System.currentTimeMillis()

        // 清理过期的处理中状态
        cleanExpiredProcessing(now)

        // 1. 检查是否已经处理过（已确认的）
        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < effectiveWindow) {
            Logger.d(TAG) { "跳过重复交易(已处理): ${Logger.maskText(key)}" }
            return false
        }

        // 2. 检查是否正在处理中（使用 putIfAbsent 实现原子操作）
        val existingProcessing = processingCache.putIfAbsent(key, now)
        if (existingProcessing != null) {
            // 已经有其他线程在处理，检查是否超时
            if (now - existingProcessing < processingTimeoutMs) {
                Logger.d(TAG) { "跳过重复交易(处理中): ${Logger.maskText(key)}" }
                return false
            }
            // 处理超时，替换为新的处理时间
            processingCache[key] = now
        }

        // 3. 检查原始文本缓存
        if (!rawText.isNullOrBlank() && amount > 0) {
            val normalizedText = normalizeRawText(rawText, amount)
            val textHash = hashRawText(normalizedText)
            val lastTextTime = rawTextCache[textHash]
            if (lastTextTime != null && now - lastTextTime < effectiveWindow) {
                // 移除处理中标记
                processingCache.remove(key)
                Logger.d(TAG) { "跳过重复交易(原始文本相同)" }
                return false
            }
        }

        Logger.d(TAG) { "获取处理权成功: ${Logger.maskText(key)}" }
        return true
    }

    /**
     * 释放交易处理权（处理失败或用户取消时调用）
     * 允许该交易被再次检测和处理
     */
    fun releaseProcessing(
        amount: Double,
        merchant: String,
        type: String,
        channel: String? = null
    ) {
        val baseKey = generateKey(amount, merchant, type)
        val key = if (channel != null) "$baseKey|$channel" else baseKey
        processingCache.remove(key)
        Logger.d(TAG) { "释放处理权: ${Logger.maskText(key)}" }
    }

    /**
     * 清理过期的处理中状态
     */
    private fun cleanExpiredProcessing(currentTime: Long) {
        val expiredKeys = processingCache.entries
            .filter { currentTime - it.value >= processingTimeoutMs }
            .map { it.key }
        expiredKeys.forEach { processingCache.remove(it) }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): Stats {
        return Stats(
            totalChecks = totalChecks.get(),
            duplicatesFound = duplicatesFound.get(),
            cacheSize = cache.size,
            rawTextCacheSize = rawTextCache.size,
            processingCacheSize = processingCache.size,
            duplicateRate = if (totalChecks.get() > 0) {
                (duplicatesFound.get() * 100.0 / totalChecks.get())
            } else 0.0
        )
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        totalChecks.set(0)
        duplicatesFound.set(0)
    }

    /**
     * 清空缓存
     */
    fun clear() {
        cache.clear()
        rawTextCache.clear()
        processingCache.clear()
        resetStats()
    }

    /**
     * 统计信息数据类
     */
    data class Stats(
        val totalChecks: Long,
        val duplicatesFound: Long,
        val cacheSize: Int,
        val rawTextCacheSize: Int = 0,
        val processingCacheSize: Int = 0,
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
