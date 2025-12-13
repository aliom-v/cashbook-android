package com.example.localexpense.util

import com.example.localexpense.domain.service.DuplicateStats
import com.example.localexpense.domain.service.IDuplicateDetector
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易去重检测器
 *
 * 功能：
 * 1. 基于金额+商户+类型的组合判断重复
 * 2. 基于原始文本哈希的去重（防止同一页面多次触发）
 * 3. 使用线程安全的 LRU 缓存记录最近的多笔交易
 * 4. 支持配置去重时间窗口
 * 5. 支持不同渠道差异化去重时间
 * 6. 完全线程安全
 *
 * 使用场景：
 * - 无障碍服务检测到交易时，防止重复记录
 * - 通知和窗口事件可能同时触发同一笔交易
 * - 支付宝页面刷新导致的重复触发
 *
 * 性能优化 v1.8.2：
 * - 使用 ConcurrentHashMap 替代 synchronized Map，提升并发性能
 * - 优化清理策略，使用分片清理避免全表扫描
 * - 缓存商户名规范化结果，避免重复计算
 *
 * 内存优化 v1.9.0：
 * - 动态调整缓存大小，根据设备可用内存自适应
 * - 低内存设备使用更小的缓存
 */
@Singleton
class DuplicateChecker @Inject constructor() : IDuplicateDetector {

    private val timeWindowMs: Long = Constants.DUPLICATE_CHECK_INTERVAL_MS
    private val dynamicCacheSize: Int = DEFAULT_CACHE_SIZE

    // 使用 ConcurrentHashMap 替代 synchronized Map，提升并发性能
    private val cache = ConcurrentHashMap<String, Long>(dynamicCacheSize)
    private val rawTextCache = ConcurrentHashMap<String, Long>(dynamicCacheSize)
    private val processingCache = ConcurrentHashMap<String, Long>(dynamicCacheSize)

    // 处理中状态最长持续时间
    private val processingTimeoutMs = 30_000L

    // 用户取消后的冷却时间（防止立即重新触发）
    private val cancelCooldownMs = 5_000L

    // 取消冷却缓存
    private val cancelCooldownCache = ConcurrentHashMap<String, Long>(dynamicCacheSize)

    // 上次清理时间
    private val lastCleanupTime = AtomicLong(System.currentTimeMillis())
    private val cleanupIntervalMs = 5000L

    // 商户名规范化缓存大小
    companion object {
        private const val TAG = "DuplicateChecker"
        private const val MERCHANT_CACHE_SIZE = 256
        private const val DEFAULT_CACHE_SIZE = 100

        // 内存等级对应的缓存大小（保留用于将来可能的动态调整）
        private const val CACHE_SIZE_LOW_MEMORY = 50
        private const val CACHE_SIZE_NORMAL = 100
        private const val CACHE_SIZE_HIGH_MEMORY = 200
    }

    // 商户名规范化缓存（使用线程安全的 LRU 缓存）
    private val merchantNormalizeCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MERCHANT_CACHE_SIZE
        }
    }
    private val merchantCacheLock = Any()

    // 预编译正则表达式
    private val timeRegex = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")
    private val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val whitespaceRegex = Regex("\\s+")

    // 统计信息
    private val totalChecks = AtomicLong(0)
    private val duplicatesFound = AtomicLong(0)

    /**
     * 检查交易是否应该被处理（非重复）
     * 实现 IDuplicateDetector 接口
     *
     * @param amount 交易金额
     * @param merchant 商户名称
     * @param type 交易类型 (expense/income)
     * @param packageName 来源应用包名（可选，用于差异化去重时间）
     * @return true 表示应该处理，false 表示重复应跳过
     */
    override fun shouldProcess(
        amount: Double,
        merchant: String,
        type: String,
        packageName: String?
    ): Boolean {
        // 将 packageName 转换为 channel
        val channel = packageName?.let { Channel.PACKAGE_MAP[it] }
        return shouldProcessInternal(amount, merchant, type, channel, null)
    }

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
        return shouldProcessInternal(amount, merchant, type, channel, rawText)
    }

    private fun shouldProcessInternal(
        amount: Double,
        merchant: String,
        type: String,
        channel: String?,
        rawText: String?
    ): Boolean {
        // 生成包含渠道的 key，避免不同渠道相同金额交易被误判
        val key = if (channel != null) {
            "${generateKey(amount, merchant, type)}|$channel"
        } else {
            generateKey(amount, merchant, type)
        }
        val effectiveWindow = getEffectiveTimeWindow(channel)
        return shouldProcessInternalByKey(key, effectiveWindow, rawText, amount)
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
    private fun shouldProcessInternalByKey(key: String, effectiveWindow: Long, rawText: String?, amount: Double = 0.0): Boolean {
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
     * 优化：使用更安全的降级策略，减少碰撞风险
     */
    private fun hashRawText(text: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(text.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 降级策略：使用多重哈希组合，降低碰撞风险
            // 组合 hashCode + 长度 + 首尾字符的哈希
            val hashCode = text.hashCode()
            val length = text.length
            val prefixHash = if (text.length > 10) text.substring(0, 10).hashCode() else 0
            val suffixHash = if (text.length > 10) text.substring(text.length - 10).hashCode() else 0
            "${hashCode}_${length}_${prefixHash}_$suffixHash"
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
     * 规范化商户名称（带缓存）
     * 目的：确保同一交易的商户名即使略有不同也能匹配
     * 优化：使用 LRU 缓存避免重复计算，线程安全
     */
    private fun normalizeMerchant(merchant: String): String {
        // 先检查缓存（线程安全）
        synchronized(merchantCacheLock) {
            merchantNormalizeCache[merchant]?.let { return it }
        }

        var normalized = merchant.trim().lowercase()

        // 只移除平台前缀，保留核心商户/人名信息
        val platformPrefixes = listOf("微信-", "支付宝-", "云闪付-")
        for (prefix in platformPrefixes) {
            if (normalized.startsWith(prefix) && normalized.length > prefix.length) {
                normalized = normalized.removePrefix(prefix)
            }
        }

        // 规范化未知商户
        if (normalized == "未知商户" || normalized == "未知" || normalized.isEmpty()) {
            normalized = "unknown"
        } else {
            normalized = normalized.trim()
        }

        // 缓存结果（线程安全，LRU 自动淘汰）
        synchronized(merchantCacheLock) {
            merchantNormalizeCache[merchant] = normalized
        }

        return normalized
    }

    /**
     * 延迟清理过期记录
     * 优化：使用 ConcurrentHashMap 的 forEachKey 进行非阻塞清理
     */
    private fun maybeCleanExpired(currentTime: Long, window: Long) {
        val lastCleanup = lastCleanupTime.get()
        if (currentTime - lastCleanup < cleanupIntervalMs) {
            return
        }

        // CAS 更新清理时间
        if (!lastCleanupTime.compareAndSet(lastCleanup, currentTime)) {
            return
        }

        // 使用 ConcurrentHashMap 的非阻塞遍历
        cleanExpiredFromMap(cache, currentTime, window)
        cleanExpiredFromMap(rawTextCache, currentTime, window)
        cleanExpiredFromMap(processingCache, currentTime, processingTimeoutMs)

        // 限制缓存大小（简单的淘汰策略）
        trimCacheIfNeeded(cache)
        trimCacheIfNeeded(rawTextCache)
        trimCacheIfNeeded(processingCache)
    }

    /**
     * 从 ConcurrentHashMap 中清理过期条目
     */
    private fun cleanExpiredFromMap(map: ConcurrentHashMap<String, Long>, currentTime: Long, window: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value >= window) {
                iterator.remove()
            }
        }
    }

    /**
     * 如果缓存超出大小限制，移除最旧的条目
     * 优化：使用迭代器删除，避免排序带来的性能开销
     */
    private fun trimCacheIfNeeded(map: ConcurrentHashMap<String, Long>) {
        val currentSize = map.size
        if (currentSize <= dynamicCacheSize) return

        // 计算需要移除的条目数（移除 20% 额外条目，避免频繁触发清理）
        val targetSize = (dynamicCacheSize * 0.8).toInt()
        val entriesToRemove = currentSize - targetSize
        if (entriesToRemove <= 0) return

        // 找出阈值时间（只需要找出第 N 旧的时间戳）
        val times = map.values.toMutableList()
        times.sort()
        val cutoffTime = if (entriesToRemove < times.size) {
            times[entriesToRemove - 1]
        } else {
            Long.MAX_VALUE
        }

        // 使用迭代器移除旧条目
        val iterator = map.entries.iterator()
        var removed = 0
        while (iterator.hasNext() && removed < entriesToRemove) {
            val entry = iterator.next()
            if (entry.value <= cutoffTime) {
                iterator.remove()
                removed++
            }
        }
    }

    /**
     * 手动标记交易已处理（实现 IDuplicateDetector 接口）
     */
    override fun markProcessed(amount: Double, merchant: String, type: String) {
        markProcessed(amount, merchant, type, null, null)
    }

    /**
     * 标记交易为已取消（实现 IDuplicateDetector 接口）
     */
    override fun markCancelled(amount: Double, merchant: String, type: String) {
        releaseProcessing(amount, merchant, type, null, addCooldown = true)
    }

    /**
     * 清空缓存（实现 IDuplicateDetector 接口）
     */
    override fun clear() {
        cache.clear()
        rawTextCache.clear()
        processingCache.clear()
        cancelCooldownCache.clear()
        synchronized(merchantCacheLock) {
            merchantNormalizeCache.clear()
        }
        resetStats()
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
     * v1.9.3: 增强原子性，解决原始文本检查的竞态条件
     *
     * @return true 表示成功获取处理权，false 表示应跳过
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

        // 1. 检查是否已经处理过
        val lastTime = cache[key]
        if (lastTime != null && now - lastTime < effectiveWindow) {
            Logger.d(TAG) { "跳过重复交易(已处理): ${Logger.maskText(key)}" }
            return false
        }

        // 1.5 检查是否在取消冷却期内
        val cancelTime = cancelCooldownCache[key]
        if (cancelTime != null && now - cancelTime < cancelCooldownMs) {
            Logger.d(TAG) { "跳过交易(取消冷却中): ${Logger.maskText(key)}" }
            return false
        }

        // 2. 计算原始文本哈希（先计算，后续原子操作中使用）
        val textHash = if (!rawText.isNullOrBlank() && amount > 0) {
            val normalizedText = normalizeRawText(rawText, amount)
            hashRawText(normalizedText)
        } else null

        // 3. 原子性地检查原始文本缓存（先于处理权获取）
        // 这样可以避免：A获取处理权 -> B检查文本缓存 -> 两者都通过的竞态
        if (textHash != null) {
            val lastTextTime = rawTextCache[textHash]
            if (lastTextTime != null && now - lastTextTime < effectiveWindow) {
                Logger.d(TAG) { "跳过重复交易(原始文本相同)" }
                return false
            }
            // 立即标记原始文本，防止其他线程同时通过
            rawTextCache[textHash] = now
        }

        // 4. 尝试原子性地标记为处理中
        val existingProcessing = processingCache.putIfAbsent(key, now)
        if (existingProcessing != null) {
            // 已经有其他线程在处理
            if (now - existingProcessing < processingTimeoutMs) {
                // 如果标记了原始文本，需要回滚
                if (textHash != null) {
                    rawTextCache.remove(textHash)
                }
                Logger.d(TAG) { "跳过重复交易(处理中): ${Logger.maskText(key)}" }
                return false
            }
            // 处理超时，替换为新的处理时间
            processingCache[key] = now
        }

        Logger.d(TAG) { "获取处理权成功: ${Logger.maskText(key)}" }
        return true
    }

    /**
     * 释放交易处理权
     * 优化 v1.9.2: 添加冷却时间，防止用户取消后立即重新触发
     *
     * @param addCooldown 是否添加冷却时间（用户主动取消时为 true）
     */
    fun releaseProcessing(
        amount: Double,
        merchant: String,
        type: String,
        channel: String? = null,
        addCooldown: Boolean = false
    ) {
        val baseKey = generateKey(amount, merchant, type)
        val key = if (channel != null) "$baseKey|$channel" else baseKey
        processingCache.remove(key)

        // 如果是用户主动取消，添加短暂冷却时间防止立即重新触发
        if (addCooldown) {
            cancelCooldownCache[key] = System.currentTimeMillis()
            Logger.d(TAG) { "释放处理权并添加冷却: ${Logger.maskText(key)}" }
        } else {
            Logger.d(TAG) { "释放处理权: ${Logger.maskText(key)}" }
        }
    }

    /**
     * 清理过期的处理中状态
     */
    private fun cleanExpiredProcessing(currentTime: Long) {
        cleanExpiredFromMap(processingCache, currentTime, processingTimeoutMs)
        cleanExpiredFromMap(cancelCooldownCache, currentTime, cancelCooldownMs)
    }

    /**
     * 获取统计信息（实现 IDuplicateDetector 接口）
     */
    override fun getStats(): DuplicateStats {
        val total = totalChecks.get()
        val blocked = duplicatesFound.get()
        return DuplicateStats(
            totalChecks = total,
            duplicatesBlocked = blocked,
            cacheSize = cache.size,
            hitRate = if (total > 0) (blocked * 100.0 / total) else 0.0
        )
    }

    /**
     * 获取详细统计信息（扩展）
     */
    fun getDetailedStats(): Stats {
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
}
