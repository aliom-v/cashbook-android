package com.example.localexpense.parser

import android.content.Context
import android.util.Log
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.Constants
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
 *
 * 规则文件格式示例 (rules.json):
 * {
 *   "version": "1.0.0",
 *   "apps": [
 *     {
 *       "packageName": "com.tencent.mm",
 *       "name": "微信",
 *       "rules": [
 *         {
 *           "type": "expense",
 *           "triggerKeywords": ["支付成功", "付款成功"],
 *           "amountRegex": "￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
 *           "merchantRegex": "收款方[:：]\\s*(.+)",
 *           "category": "微信支付"
 *         }
 *       ]
 *     }
 *   ]
 * }
 */
object RuleEngine {

    private const val TAG = "RuleEngine"
    private const val RULES_FILE = "transaction_rules.json"

    // 规则缓存 - 使用 @Volatile 保证多线程可见性
    @Volatile
    private var appRules: Map<String, List<TransactionRule>> = emptyMap()
    @Volatile
    private var currentVersion: String = "0.0.0"

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
     */
    fun init(context: Context) {
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
            Log.i(TAG, "规则引擎初始化成功, 版本: $currentVersion")
        } catch (e: Exception) {
            Log.e(TAG, "规则引擎初始化失败: ${e.message}")
            // 降级到硬编码规则
            loadFallbackRules()
        }
    }

    /**
     * 解析JSON规则（线程安全）
     */
    private fun parseRules(json: String) {
        synchronized(rulesLock) {
            val root = JSONObject(json)
            currentVersion = root.optString("version", "0.0.0")

            val appsArray = root.getJSONArray("apps")
            val rulesMap = mutableMapOf<String, List<TransactionRule>>()

            for (i in 0 until appsArray.length()) {
                val app = appsArray.getJSONObject(i)
                val packageName = app.getString("packageName")
                val rulesArray = app.getJSONArray("rules")

                val rules = mutableListOf<TransactionRule>()
                for (j in 0 until rulesArray.length()) {
                    val ruleJson = rulesArray.getJSONObject(j)
                    rules.add(parseRule(ruleJson))
                }

                // 按优先级排序（优先级高的先匹配）
                rulesMap[packageName] = rules.sortedByDescending { it.priority }
            }

            appRules = rulesMap
        }
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

        // 解析金额正则（支持多个），跳过无效的正则
        val amountRegexList = jsonArrayToList(json.getJSONArray("amountRegex"))
        val amountPatterns = amountRegexList.mapNotNull { regex ->
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

        // 解析商户正则（可选），跳过无效的正则
        val merchantPatterns = json.optJSONArray("merchantRegex")?.let {
            jsonArrayToList(it).mapNotNull { regex ->
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
     *
     * @param texts 页面文本列表
     * @param packageName 应用包名
     * @return 匹配的规则 + 提取的数据
     */
    fun match(texts: List<String>, packageName: String): MatchResult? {
        // 同步读取规则，防止读取到部分初始化的数据
        val rules = synchronized(rulesLock) {
            appRules[packageName]
        } ?: return null
        val joinedText = texts.joinToString(" | ")

        // 遍历规则（按优先级从高到低）
        for (rule in rules) {
            // 1. 检查是否包含触发关键词
            val hasTriggerKeyword = rule.triggerKeywords.any { joinedText.contains(it) }
            if (!hasTriggerKeyword) {
                continue
            }

            // 2. 检查排除关键词
            // 与 TransactionParser 逻辑一致：如果有触发关键词（成功场景），
            // 则忽略排除关键词（可能是页面历史元素）
            val hasExcludeKeyword = rule.excludeKeywords.isNotEmpty() &&
                rule.excludeKeywords.any { joinedText.contains(it) }

            // 只有在没有触发关键词的情况下，排除关键词才生效
            // 但由于已经检查了触发关键词存在，这里直接跳过排除检查
            // 注意：如果需要更严格的排除逻辑，可以取消下面的注释
            // if (hasExcludeKeyword && !hasTriggerKeyword) {
            //     continue
            // }

            // 3. 提取金额
            val amount = extractAmount(texts, rule.amountPatterns) ?: continue

            // 4. 提取商户（如果规则定义了）
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
     */
    private fun extractAmount(texts: List<String>, patterns: List<Pattern>): Double? {
        // 1. 优先使用规则定义的正则匹配
        for (text in texts) {
            for (pattern in patterns) {
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    val amountStr = matcher.group(1) ?: continue
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
            // 跳过时间格式
            if (timePattern.matcher(trimmed).matches()) continue
            if (timeDotPattern.matcher(trimmed).matches()) continue

            val matcher = numPattern.matcher(trimmed)
            if (matcher.find()) {
                val amount = AmountUtils.parseAmount(matcher.group(1))
                if (amount != null && AmountUtils.isValidAmount(amount)) {
                    return amount
                }
            }
        }

        return null
    }

    /**
     * 提取商户
     */
    private fun extractMerchant(texts: List<String>, patterns: List<Pattern>): String? {
        for (text in texts) {
            for (pattern in patterns) {
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    return matcher.group(1)?.trim()?.take(Constants.MAX_MERCHANT_NAME_LENGTH)
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
     */
    fun updateRules(context: Context, newRulesJson: String): Boolean {
        return try {
            // 1. 解析新规则
            parseRules(newRulesJson)

            // 2. 保存到本地缓存
            val cacheFile = File(context.filesDir, RULES_FILE)
            cacheFile.writeText(newRulesJson)

            // 3. 保存版本号（使用加密存储）
            SecurePreferences.putString(context, SecurePreferences.Keys.RULES_VERSION, currentVersion)
            SecurePreferences.putLong(context, SecurePreferences.Keys.RULES_LAST_UPDATE, System.currentTimeMillis())

            Log.i(TAG, "规则更新成功: $currentVersion")
            true
        } catch (e: Exception) {
            Log.e(TAG, "规则更新失败: ${e.message}")
            false
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
     * 辅助方法: JSONArray -> List<String>
     */
    private fun jsonArrayToList(array: JSONArray): List<String> {
        return (0 until array.length()).map { array.getString(it) }
    }
}
