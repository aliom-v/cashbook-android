package com.example.localexpense.parser

import android.content.Context
import android.util.Log
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.Constants
import com.example.localexpense.util.SafeRegexMatcher
import com.example.localexpense.util.SecurePreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

/**
 * 规则引擎 - 支持热更新的交易识别规则
 *
 * 设计思路:
 * 1. 将匹配规则从代码中抽离到JSON配置文件
 * 2. 支持从本地assets或网络加载规则
 * 3. 规则可以动态更新,无需发版
 * 4. 支持金额黑名单配置化 (v1.8.1)
 * 5. 支持版本兼容性检查 (v1.8.1)
 *
 * 性能优化 v1.8.2:
 * - 关键词匹配结果缓存
 * - 预计算关键词索引加速匹配
 * - 使用只读引用减少同步开销
 */
object RuleEngine {

    private const val TAG = "RuleEngine"
    private const val RULES_FILE = "transaction_rules.json"

    // 规则缓存 - 使用 @Volatile 保证多线程可见性
    @Volatile
    private var appRules: Map<String, List<TransactionRule>> = emptyMap()
    @Volatile
    private var currentVersion: String = "0.0.0"
    @Volatile
    private var minAppVersion: Int = 0

    // 金额黑名单缓存 (v1.8.1)
    @Volatile
    private var blacklistExact: Set<Double> = Constants.BLACKLIST_AMOUNTS
    @Volatile
    private var blacklistPrefixes: Set<Int> = Constants.BLACKLIST_INTEGER_PREFIXES
    @Volatile
    private var exactMatchOnlyAmounts: Set<Int> = setOf(10000)

    // 关键词索引缓存 (v1.8.2) - 用于快速判断是否需要进行规则匹配
    @Volatile
    private var allTriggerKeywords: Set<String> = emptySet()

    // v1.9.3: 预编译的关键词正则（用于快速多模式匹配）
    @Volatile
    private var keywordPattern: Regex? = null

    // v1.9.3: 关键词到规则的倒排索引
    @Volatile
    private var keywordToRulesIndex: Map<String, List<Int>> = emptyMap()

    // 初始化状态标志
    @Volatile
    private var isInitializing = false
    @Volatile
    private var initializationComplete = false

    // 规则更新锁，保护写操作的线程安全
    private val rulesLock = Any()

    /**
     * 交易规则数据类
     */
    data class TransactionRule(
        val type: String,  // "income" or "expense"
        val triggerKeywords: List<String>,  // 触发关键词
        val excludeKeywords: List<String>,  // 排除关键词（匹配时跳过）
        val amountPatterns: List<Pattern>,  // 金额匹配正则
        val merchantPatterns: List<Pattern>?,  // 商户匹配正则
        val category: String,  // 默认分类
        val priority: Int = 0  // 优先级（数字越大优先级越高）
    )

    /**
     * 初始化规则引擎
     * 优先从本地缓存加载,如果没有则从assets加载默认规则
     * 优化：防止重复初始化，保证线程安全
     */
    fun init(context: Context) {
        // 快速检查：已完成初始化则跳过
        if (initializationComplete && appRules.isNotEmpty()) {
            Log.d(TAG, "规则引擎已初始化，跳过")
            return
        }

        synchronized(rulesLock) {
            // 双重检查：防止并发初始化
            if (isInitializing) {
                Log.d(TAG, "规则引擎正在初始化中，跳过")
                return
            }
            if (initializationComplete && appRules.isNotEmpty()) {
                return
            }

            isInitializing = true
        }

        try {
            // 1. 尝试从本地缓存加载
            val cacheFile = File(context.filesDir, RULES_FILE)
            val rulesJson = if (cacheFile.exists()) {
                Log.i(TAG, "从缓存加载规则")
                cacheFile.readText()
            } else {
                // 2. 从assets加载默认规则
                Log.i(TAG, "从assets加载默认规则")
                context.assets.open(RULES_FILE).bufferedReader().use { it.readText() }
            }

            parseRules(rulesJson)
            initializationComplete = true
            Log.i(TAG, "规则引擎初始化成功, 版本: $currentVersion")
        } catch (e: Exception) {
            Log.e(TAG, "规则引擎初始化失败: ${e.message}")
            // 降级到硬编码规则
            loadFallbackRules()
            initializationComplete = true
        } finally {
            synchronized(rulesLock) {
                isInitializing = false
            }
        }
    }

    /**
     * 解析JSON规则（线程安全）
     * 优化：同时构建关键词索引和倒排索引
     */
    private fun parseRules(json: String) {
        synchronized(rulesLock) {
            val root = JSONObject(json)
            currentVersion = root.optString("version", "0.0.0")
            minAppVersion = root.optInt("minAppVersion", 0)

            // 解析金额黑名单配置 (v1.8.1)
            parseAmountBlacklist(root)

            val appsArray = root.getJSONArray("apps")
            val rulesMap = mutableMapOf<String, List<TransactionRule>>()
            val keywordsSet = mutableSetOf<String>()

            for (i in 0 until appsArray.length()) {
                val app = appsArray.getJSONObject(i)
                val packageName = app.getString("packageName")
                val rulesArray = app.getJSONArray("rules")

                val rules = mutableListOf<TransactionRule>()
                for (j in 0 until rulesArray.length()) {
                    val ruleJson = rulesArray.getJSONObject(j)
                    val rule = parseRule(ruleJson)
                    rules.add(rule)
                    // 收集所有触发关键词用于快速预检查
                    keywordsSet.addAll(rule.triggerKeywords)
                }

                // 按优先级排序（优先级高的先匹配）
                rulesMap[packageName] = rules.sortedByDescending { it.priority }
            }

            appRules = rulesMap
            allTriggerKeywords = keywordsSet

            // v1.9.3: 构建预编译的关键词正则
            buildKeywordPattern(keywordsSet)

            // v1.9.3: 构建倒排索引
            buildKeywordIndex(rulesMap)
        }
    }

    /**
     * v1.9.3: 构建预编译的关键词正则
     * 用于快速检查文本是否包含任何关键词
     */
    private fun buildKeywordPattern(keywords: Set<String>) {
        if (keywords.isEmpty()) {
            keywordPattern = null
            return
        }

        try {
            // 按长度降序排序，确保长关键词优先匹配
            val sortedKeywords = keywords.sortedByDescending { it.length }
            val pattern = sortedKeywords.joinToString("|") { Regex.escape(it) }
            keywordPattern = Regex(pattern)
            Log.d(TAG, "关键词正则构建成功，包含 ${keywords.size} 个关键词")
        } catch (e: Exception) {
            Log.e(TAG, "构建关键词正则失败: ${e.message}")
            keywordPattern = null
        }
    }

    /**
     * v1.9.3: 构建关键词到规则的倒排索引
     * 用于快速定位可能匹配的规则
     */
    private fun buildKeywordIndex(rulesMap: Map<String, List<TransactionRule>>) {
        val index = mutableMapOf<String, MutableList<Int>>()

        rulesMap.forEach { (_, rules) ->
            rules.forEachIndexed { ruleIndex, rule ->
                rule.triggerKeywords.forEach { keyword ->
                    index.getOrPut(keyword) { mutableListOf() }.add(ruleIndex)
                }
            }
        }

        keywordToRulesIndex = index
        Log.d(TAG, "倒排索引构建成功，包含 ${index.size} 个关键词映射")
    }

    /**
     * 解析金额黑名单配置 (v1.8.1)
     */
    private fun parseAmountBlacklist(root: JSONObject) {
        val blacklistObj = root.optJSONObject("amountBlacklist") ?: return

        try {
            // 解析精确匹配黑名单
            blacklistObj.optJSONArray("exact")?.let { array ->
                val exactSet = mutableSetOf<Double>()
                for (i in 0 until array.length()) {
                    exactSet.add(array.getDouble(i))
                }
                if (exactSet.isNotEmpty()) {
                    blacklistExact = exactSet
                }
            }

            // 解析整数前缀黑名单
            blacklistObj.optJSONArray("integerPrefixes")?.let { array ->
                val prefixSet = mutableSetOf<Int>()
                for (i in 0 until array.length()) {
                    prefixSet.add(array.getInt(i))
                }
                if (prefixSet.isNotEmpty()) {
                    blacklistPrefixes = prefixSet
                }
            }

            // 解析特殊规则
            blacklistObj.optJSONObject("specialRules")?.let { specialRules ->
                val exactMatchOnly = mutableSetOf<Int>()
                specialRules.keys().forEach { key ->
                    val rule = specialRules.optJSONObject(key)
                    if (rule?.optBoolean("exactMatchOnly", false) == true) {
                        key.toIntOrNull()?.let { exactMatchOnly.add(it) }
                    }
                }
                if (exactMatchOnly.isNotEmpty()) {
                    exactMatchOnlyAmounts = exactMatchOnly
                }
            }

            Log.i(TAG, "金额黑名单已加载: ${blacklistExact.size} 个精确值, ${blacklistPrefixes.size} 个前缀")
        } catch (e: Exception) {
            Log.e(TAG, "解析金额黑名单失败: ${e.message}")
            // 保持使用默认值
        }
    }

    /**
     * 检查金额是否在黑名单中 (v1.8.1)
     * 供外部调用，替代 Constants 中的硬编码检查
     */
    fun isAmountBlacklisted(amount: Double): Boolean {
        // 精确匹配黑名单
        if (amount in blacklistExact) return true

        // 检查整数部分是否在黑名单前缀中
        val integerPart = amount.toInt()
        if (integerPart in blacklistPrefixes) {
            // 检查是否是仅精确匹配的金额
            if (integerPart in exactMatchOnlyAmounts) {
                // 仅精确匹配时过滤（如 10000.0 过滤，10000.50 不过滤）
                return amount == integerPart.toDouble()
            }
            // 其他黑名单号码直接过滤
            return true
        }

        return false
    }

    // 记录无效规则数量，用于诊断
    @Volatile
    private var invalidPatternCount = 0

    /**
     * 解析单条规则
     * 对无效的正则表达式进行容错处理，跳过无效的pattern而非抛出异常
     */
    private fun parseRule(json: JSONObject): TransactionRule {
        val type = json.getString("type")
        val category = json.getString("category")
        val triggerKeywords = jsonArrayToList(json.getJSONArray("triggerKeywords"))
        // 解析排除关键词（可选）
        val excludeKeywords = json.optJSONArray("excludeKeywords")?.let {
            jsonArrayToList(it)
        } ?: emptyList()

        // 解析金额正则（支持多个），跳过无效或危险的正则
        val amountRegexList = jsonArrayToList(json.getJSONArray("amountRegex"))
        val amountPatterns = amountRegexList.mapNotNull { regex ->
            // 先检查危险模式
            val dangerCheck = SafeRegexMatcher.checkDangerousPattern(regex)
            if (dangerCheck.isDangerous) {
                invalidPatternCount++
                Log.w(TAG, "⚠️ 规则[$category]包含危险正则模式: ${dangerCheck.reason}")
                return@mapNotNull null
            }
            try {
                Pattern.compile(regex)
            } catch (e: java.util.regex.PatternSyntaxException) {
                invalidPatternCount++
                Log.e(TAG, "⚠️ 规则[$category]包含无效的金额正则: $regex")
                null
            }
        }

        // 如果所有金额正则都无效，记录警告
        if (amountPatterns.isEmpty() && amountRegexList.isNotEmpty()) {
            Log.e(TAG, "⚠️ 规则[$category]的所有金额正则都无效，该规则将无法匹配任何交易！")
        }

        // 解析商户正则（可选），跳过无效或危险的正则
        val merchantPatterns = json.optJSONArray("merchantRegex")?.let {
            jsonArrayToList(it).mapNotNull { regex ->
                // 先检查危险模式
                val dangerCheck = SafeRegexMatcher.checkDangerousPattern(regex)
                if (dangerCheck.isDangerous) {
                    invalidPatternCount++
                    Log.w(TAG, "⚠️ 规则[$category]包含危险商户正则: ${dangerCheck.reason}")
                    return@mapNotNull null
                }
                try {
                    Pattern.compile(regex)
                } catch (e: java.util.regex.PatternSyntaxException) {
                    invalidPatternCount++
                    Log.e(TAG, "⚠️ 规则[$category]包含无效的商户正则: $regex")
                    null
                }
            }.takeIf { it.isNotEmpty() }
        }

        val priority = json.optInt("priority", 0)

        return TransactionRule(type, triggerKeywords, excludeKeywords, amountPatterns, merchantPatterns, category, priority)
    }

    /**
     * 获取无效正则数量（用于诊断）
     */
    fun getInvalidPatternCount(): Int = invalidPatternCount

    /**
     * 使用规则匹配交易信息
     * 优化 v1.8.2: 添加快速预检查，减少不必要的规则遍历
     *
     * @param texts 页面文本列表
     * @param packageName 应用包名
     * @return 匹配的规则 + 提取的数据
     */
    fun match(texts: List<String>, packageName: String): MatchResult? {
        // 快速检查：如果文本为空，直接返回
        if (texts.isEmpty()) return null

        // 读取规则引用（避免在循环中重复同步）
        val rules = appRules[packageName] ?: return null
        val pattern = keywordPattern

        // 快速检查：如果规则为空，直接返回
        if (rules.isEmpty()) return null

        val joinedText = texts.joinToString(" | ")

        // v1.9.3: 使用预编译正则进行快速预检查
        // 如果没有预编译正则，降级到普通检查
        val matchedKeywords: Set<String>
        if (pattern != null) {
            // 使用预编译正则一次性找出所有匹配的关键词
            val matches = pattern.findAll(joinedText).map { it.value }.toSet()
            if (matches.isEmpty()) return null
            matchedKeywords = matches
        } else {
            // 降级：使用传统方式检查
            val keywords = allTriggerKeywords
            val hasAnyKeyword = keywords.any { joinedText.contains(it) }
            if (!hasAnyKeyword) return null
            matchedKeywords = emptySet() // 降级时无法预先知道匹配的关键词
        }

        // 遍历规则（按优先级从高到低）
        for (rule in rules) {
            // 1. 快速检查是否包含触发关键词
            // v1.9.3: 如果有预匹配的关键词集合，用交集判断更快
            val hasTriggerKeyword = if (matchedKeywords.isNotEmpty()) {
                rule.triggerKeywords.any { it in matchedKeywords }
            } else {
                rule.triggerKeywords.any { joinedText.contains(it) }
            }
            if (!hasTriggerKeyword) {
                continue
            }

            // 2. 检查排除关键词
            val hasExcludeKeyword = rule.excludeKeywords.isNotEmpty() &&
                rule.excludeKeywords.any { joinedText.contains(it) }

            // 如果有排除关键词，需要检查是否有明确的"成功"类触发词
            if (hasExcludeKeyword) {
                val hasStrongTriggerInText = rule.triggerKeywords.any { keyword ->
                    val isStrongKeyword = keyword.contains("成功") ||
                                          keyword.contains("完成") ||
                                          keyword.contains("到账") ||
                                          (keyword.contains("已") && (keyword.contains("付") || keyword.contains("收") || keyword.contains("转") || keyword.contains("扣")))
                    isStrongKeyword && joinedText.contains(keyword)
                }
                if (!hasStrongTriggerInText) {
                    continue
                }
            }

            // 3. 提取金额
            if (rule.amountPatterns.isEmpty()) continue
            val amount = extractAmount(texts, rule.amountPatterns) ?: continue

            // 4. 提取商户
            val merchant = rule.merchantPatterns?.let { patterns ->
                extractMerchant(texts, patterns)
            } ?: getDefaultMerchant(rule.category)

            // 5. 匹配成功
            return MatchResult(
                rule = rule,
                amount = amount,
                merchant = merchant
            )
        }

        return null
    }

    /**
     * 根据分类获取默认商户名
     */
    private fun getDefaultMerchant(category: String): String = when {
        category.contains("红包") -> "红包"
        category.contains("转账") -> "转账"
        category.contains("微信") -> "微信支付"
        category.contains("支付宝") -> "支付宝"
        category.contains("云闪付") -> "云闪付"
        else -> "未知商户"
    }

    /**
     * 匹配结果
     */
    data class MatchResult(
        val rule: TransactionRule,
        val amount: Double,
        val merchant: String
    )

    // 时间格式正则（用于排除时间被误识别为金额）
    private val timePattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}$")
    private val timeDotPattern = Pattern.compile("^([01]?\\d|2[0-3])\\.([0-5]\\d)$")

    /**
     * 提取金额
     * 使用 AmountUtils.parseAmount 进行精确解析，与 TransactionParser 保持一致
     * 优化：使用 SafeRegexMatcher 防止 ReDoS 攻击
     */
    private fun extractAmount(texts: List<String>, patterns: List<Pattern>): Double? {
        // 1. 优先使用规则定义的正则匹配（带超时保护）
        for (text in texts) {
            for (pattern in patterns) {
                val result = SafeRegexMatcher.findWithTimeout(pattern, text)
                if (result != null && result.matched) {
                    val amountStr = result.group(1) ?: continue
                    // 使用 BigDecimal 进行精确解析
                    val amount = AmountUtils.parseAmount(amountStr)
                    if (amount != null && AmountUtils.isValidAmount(amount)) {
                        return amount
                    }
                }
            }
        }

        // 2. 兜底：查找独立的纯数字（微信红包可能只显示数字）
        val numPattern = Pattern.compile("^([0-9]+(?:\\.[0-9]{1,2})?)$")
        for (text in texts) {
            val trimmed = text.trim()
            // 跳过太长的文本（可能是订单号）
            if (trimmed.length > 15) continue
            // 跳过时间格式（使用 SafeRegexMatcher）
            if (SafeRegexMatcher.matchesWithTimeout(timePattern, trimmed)) continue
            if (SafeRegexMatcher.matchesWithTimeout(timeDotPattern, trimmed)) continue

            val result = SafeRegexMatcher.findWithTimeout(numPattern, trimmed)
            if (result != null && result.matched) {
                val amount = AmountUtils.parseAmount(result.group(1))
                if (amount != null && AmountUtils.isValidAmount(amount)) {
                    return amount
                }
            }
        }

        return null
    }

    /**
     * 提取商户
     * 优化：使用 SafeRegexMatcher 防止 ReDoS 攻击
     */
    private fun extractMerchant(texts: List<String>, patterns: List<Pattern>): String? {
        for (text in texts) {
            for (pattern in patterns) {
                val result = SafeRegexMatcher.findWithTimeout(pattern, text)
                if (result != null && result.matched) {
                    return result.group(1)?.trim()?.take(Constants.MAX_MERCHANT_NAME_LENGTH)
                }
            }
        }
        return null
    }

    /**
     * 加载降级规则（当JSON解析失败时使用，线程安全）
     * 降级规则更加严格，只匹配明确的支付成功页面
     */
    private fun loadFallbackRules() {
        synchronized(rulesLock) {
            Log.w(TAG, "使用硬编码降级规则")

            // 支付确认页面的特征词，用于排除
            val confirmPageKeywords = listOf(
                "确认付款", "立即支付", "确认支付", "输入密码",
                "待支付", "去支付", "支付方式", "付款方式"
            )

            val wechatRules = listOf(
                TransactionRule(
                    type = "expense",
                    triggerKeywords = listOf("支付成功", "付款成功"),
                    excludeKeywords = confirmPageKeywords,
                    amountPatterns = listOf(Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")),
                    merchantPatterns = null,
                    category = "微信支付",
                    priority = 10
                ),
                TransactionRule(
                    type = "income",
                    triggerKeywords = listOf("收到转账", "已领取", "红包已存入"),
                    excludeKeywords = emptyList(),
                    amountPatterns = listOf(Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")),
                    merchantPatterns = null,
                    category = "微信收入",
                    priority = 10
                )
            )

            appRules = mapOf(com.example.localexpense.util.PackageNames.WECHAT to wechatRules)
        }
    }

    /**
     * 更新规则（从网络下载新规则）
     * v1.8.1: 添加版本兼容性检查
     *
     * @param context 上下文
     * @param newRulesJson 新规则 JSON
     * @param appVersionCode 当前应用版本号（用于兼容性检查）
     * @return UpdateResult 更新结果
     */
    fun updateRules(context: Context, newRulesJson: String, appVersionCode: Int = Int.MAX_VALUE): UpdateResult {
        return try {
            // 0. 预检查：解析最低版本要求
            val root = JSONObject(newRulesJson)
            val requiredMinVersion = root.optInt("minAppVersion", 0)
            val newVersion = root.optString("version", "0.0.0")

            // 版本兼容性检查
            if (appVersionCode < requiredMinVersion) {
                Log.w(TAG, "规则版本 $newVersion 需要 App 版本 >= $requiredMinVersion，当前版本: $appVersionCode")
                return UpdateResult.IncompatibleVersion(
                    rulesVersion = newVersion,
                    requiredAppVersion = requiredMinVersion,
                    currentAppVersion = appVersionCode
                )
            }

            // 1. 解析新规则
            parseRules(newRulesJson)

            // 2. 保存到本地缓存
            val cacheFile = File(context.filesDir, RULES_FILE)
            cacheFile.writeText(newRulesJson)

            // 3. 保存版本号（使用加密存储）
            SecurePreferences.putString(context, SecurePreferences.Keys.RULES_VERSION, currentVersion)
            SecurePreferences.putLong(context, SecurePreferences.Keys.RULES_LAST_UPDATE, System.currentTimeMillis())

            Log.i(TAG, "规则更新成功: $currentVersion")
            UpdateResult.Success(currentVersion)
        } catch (e: Exception) {
            Log.e(TAG, "规则更新失败: ${e.message}")
            UpdateResult.Failed(e.message ?: "未知错误")
        }
    }

    /**
     * 规则更新结果 (v1.8.1)
     */
    sealed class UpdateResult {
        data class Success(val version: String) : UpdateResult()
        data class Failed(val error: String) : UpdateResult()
        data class IncompatibleVersion(
            val rulesVersion: String,
            val requiredAppVersion: Int,
            val currentAppVersion: Int
        ) : UpdateResult() {
            val message: String
                get() = "规则版本 $rulesVersion 需要 App 版本 >= $requiredAppVersion"
        }
    }

    /**
     * 兼容旧版本的更新方法
     */
    @Deprecated("Use updateRules with appVersionCode parameter", ReplaceWith("updateRules(context, newRulesJson, appVersionCode)"))
    fun updateRulesLegacy(context: Context, newRulesJson: String): Boolean {
        return when (updateRules(context, newRulesJson)) {
            is UpdateResult.Success -> true
            else -> false
        }
    }

    /**
     * 获取当前规则版本
     */
    fun getVersion(): String = currentVersion

    /**
     * 检查规则引擎是否已初始化
     */
    fun isInitialized(): Boolean = synchronized(rulesLock) {
        appRules.isNotEmpty()
    }

    /**
     * 获取规则引擎统计信息
     */
    fun getStats(): RuleStats = synchronized(rulesLock) {
        val appCount = appRules.size
        val ruleCount = appRules.values.sumOf { it.size }
        RuleStats(
            appCount = appCount,
            ruleCount = ruleCount,
            version = currentVersion,
            invalidPatternCount = invalidPatternCount
        )
    }

    /**
     * 规则统计信息
     */
    data class RuleStats(
        val appCount: Int,
        val ruleCount: Int,
        val version: String,
        val invalidPatternCount: Int
    )

    /**
     * 清理缓存（用于内存压力或应用退出时）
     */
    fun clearCache() {
        synchronized(rulesLock) {
            // 只清理关键词索引缓存，保留规则
            // 规则是从文件加载的，不需要清理
            invalidPatternCount = 0
            Log.d(TAG, "RuleEngine 缓存已清理")
        }
    }

    /**
     * 重置引擎状态（用于测试）
     */
    fun reset() {
        synchronized(rulesLock) {
            appRules = emptyMap()
            allTriggerKeywords = emptySet()
            // v1.9.3: 清理预编译缓存
            keywordPattern = null
            keywordToRulesIndex = emptyMap()
            blacklistExact = Constants.BLACKLIST_AMOUNTS
            blacklistPrefixes = Constants.BLACKLIST_INTEGER_PREFIXES
            exactMatchOnlyAmounts = setOf(10000)
            currentVersion = "0.0.0"
            minAppVersion = 0
            initializationComplete = false
            isInitializing = false
            invalidPatternCount = 0
            Log.d(TAG, "RuleEngine 已重置")
        }
    }

    /**
     * 辅助方法: JSONArray -> List<String>
     */
    private fun jsonArrayToList(array: JSONArray): List<String> {
        return (0 until array.length()).map { array.getString(it) }
    }
}
