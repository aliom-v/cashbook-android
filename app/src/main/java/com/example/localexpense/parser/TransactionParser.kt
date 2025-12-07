package com.example.localexpense.parser

import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.Channel
import com.example.localexpense.util.Constants
import com.example.localexpense.util.Logger
import com.example.localexpense.util.PerformanceMonitor
import java.util.regex.Pattern

/**
 * 交易解析器
 * 负责从微信/支付宝页面文本或通知中提取交易信息
 *
 * 解析流程：
 * 1. 检查排除关键词（确认页面）
 * 2. 使用规则引擎匹配（优先）
 * 3. 降级到传统解析（兜底）
 *
 * 优化特性：
 * - 规则引擎支持热更新
 * - BigDecimal 精确金额解析
 * - 多模式金额匹配
 * - 完善的边界检查
 */
object TransactionParser {

    private const val TAG = "TransactionParser"

    // 配置常量
    private const val MAX_TEXT_LENGTH_FOR_AMOUNT = 20  // 用于金额提取的最大文本长度
    private const val MIN_MERCHANT_LENGTH = 1
    private const val MAX_PARSE_TIME_MS = 50L  // 解析耗时警告阈值

    // 金额匹配模式（按优先级排序，带货币符号的优先）
    private val amountPatterns = listOf(
        Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("¥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("共\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),  // 红包格式
        Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("金额[：:]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
    )

    // 排除时间格式
    private val timePattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}$")
    private val timeDotPattern = Pattern.compile("^([01]?\\d|2[0-3])\\.([0-5]\\d)$")

    // 独立数字模式（用于兜底提取）
    private val standaloneNumberPattern = Pattern.compile("^([0-9]+(?:\\.[0-9]{1,2})?)$")

    // 收入关键词
    private val incomeKeywords = setOf(
        // 红包收入
        "已领取", "领取了", "已存入", "存入零钱", "红包已存入",
        "红包到账", "已存入零钱", "收到红包",
        // 转账收入
        "向你转账", "给你转", "转账给你", "已收钱", "收款成功",
        "已到账", "入账成功", "收到转账", "已收款", "转账已收",
        "收钱成功", "到账成功"
    )

    // 支出关键词
    private val expenseKeywords = setOf(
        "支付成功", "付款成功", "已付款", "已支付",
        "转账成功", "已转账",
        "扫码付款", "消费"
    )

    // 排除关键词（支付确认页面特征）
    private val excludeKeywords = setOf(
        "确认付款", "立即支付", "确认支付", "输入密码",
        "待支付", "去支付", "支付方式", "付款方式",
        "确认转账", "请输入", "去付款",
        "选择红包个数", "请填写金额", "塞钱进红包", "添加表情"
    )

    // 发红包关键词（支出，优先级最高）
    private val sendRedPacketKeywords = setOf(
        "发出红包", "发了一个红包",
        "红包发送成功", "红包已发送",
        "已发送", "发送成功",
        "共发出", "个红包，共",
        "红包已塞好", "已塞钱", "塞好了",
        "发红包成功", "红包已发出", "发出了红包", "红包发出成功"
    )

    // 渠道映射
    private val channelMap = Channel.PACKAGE_MAP

    /**
     * 解析交易信息（主入口）
     *
     * @param texts 页面文本列表
     * @param packageName 应用包名
     * @return 解析出的交易实体，解析失败返回 null
     */
    fun parse(texts: List<String>, packageName: String): ExpenseEntity? {
        if (texts.isEmpty()) return null

        val startTime = PerformanceMonitor.startTimer(PerformanceMonitor.Operations.PARSE_TRANSACTION)

        try {
            return parseInternal(texts, packageName)
        } finally {
            PerformanceMonitor.endTimer(PerformanceMonitor.Operations.PARSE_TRANSACTION, startTime)
        }
    }

    /**
     * 内部解析逻辑
     */
    private fun parseInternal(texts: List<String>, packageName: String): ExpenseEntity? {
        // 合并文本用于关键词检测
        val joined = texts.joinToString(" | ")

        Logger.d(TAG) { "开始解析, 包名: $packageName, 文本数: ${texts.size}" }

        // 步骤 0: 检查排除关键词
        if (!shouldProcess(joined)) {
            Logger.d(TAG) { "检测到确认页面，跳过记录" }
            return null
        }

        // 步骤 1: 优先使用规则引擎匹配
        val ruleMatch = tryRuleEngine(texts, packageName, joined)
        if (ruleMatch != null) {
            return ruleMatch
        }

        // 步骤 2: 降级到传统解析
        return tryLegacyParse(texts, packageName, joined)
    }

    /**
     * 检查是否应该处理这个页面
     */
    private fun shouldProcess(joined: String): Boolean {
        val hasExcludeKeyword = excludeKeywords.any { joined.contains(it) }
        if (!hasExcludeKeyword) return true

        // 如果有明确的成功关键词，仍然处理
        val hasSuccessKeyword = sendRedPacketKeywords.any { joined.contains(it) } ||
                expenseKeywords.any { joined.contains(it) } ||
                incomeKeywords.any { joined.contains(it) }

        return hasSuccessKeyword
    }

    /**
     * 尝试使用规则引擎匹配
     */
    private fun tryRuleEngine(texts: List<String>, packageName: String, joined: String): ExpenseEntity? {
        val ruleMatch = RuleEngine.match(texts, packageName) ?: return null

        Logger.d(TAG) { "规则引擎匹配成功: ${ruleMatch.rule.category}" }

        return ExpenseEntity(
            id = 0,
            amount = ruleMatch.amount,
            merchant = sanitizeMerchant(ruleMatch.merchant),
            type = ruleMatch.rule.type,
            timestamp = System.currentTimeMillis(),
            channel = channelMap[packageName] ?: Channel.OTHER,
            category = ruleMatch.rule.category,
            categoryId = 0,
            note = "",
            rawText = joined.take(Constants.RAW_TEXT_MAX_LENGTH)
        )
    }

    /**
     * 传统解析逻辑（降级方案）
     */
    private fun tryLegacyParse(texts: List<String>, packageName: String, joined: String): ExpenseEntity? {
        Logger.d(TAG) { "使用传统解析" }

        // 检查交易关键词
        val hasIncomeKeyword = incomeKeywords.any { joined.contains(it) }
        val hasExpenseKeyword = expenseKeywords.any { joined.contains(it) }
        val hasSendRedPacket = sendRedPacketKeywords.any { joined.contains(it) }

        if (!hasIncomeKeyword && !hasExpenseKeyword && !hasSendRedPacket) {
            Logger.d(TAG) { "未找到交易关键词" }
            return null
        }

        // 提取金额
        val amount = extractAmount(texts)
        if (amount == null || amount <= 0) {
            Logger.d(TAG) { "未找到有效金额" }
            PerformanceMonitor.increment(PerformanceMonitor.Counters.PARSE_FAILURES)
            return null
        }

        // 判断交易类型和分类
        val type = determineType(joined, hasSendRedPacket, hasIncomeKeyword, hasExpenseKeyword)
        val category = determineCategory(joined)
        val merchant = extractMerchant(texts, joined)

        Logger.d(TAG) { "传统解析成功: type=$type, category=$category" }

        return ExpenseEntity(
            id = 0,
            amount = amount,
            merchant = sanitizeMerchant(merchant),
            type = type,
            timestamp = System.currentTimeMillis(),
            channel = channelMap[packageName] ?: Channel.OTHER,
            category = category,
            categoryId = 0,
            note = "",
            rawText = joined.take(Constants.RAW_TEXT_MAX_LENGTH)
        )
    }

    /**
     * 判断交易类型
     */
    private fun determineType(
        text: String,
        isSendRedPacket: Boolean,
        isIncome: Boolean,
        isExpense: Boolean
    ): String {
        return when {
            isSendRedPacket -> "expense"  // 发红包优先判断为支出
            isIncome && !isExpense -> "income"
            isExpense -> "expense"
            text.contains("红包") -> "income"  // 其他红包场景默认收入
            else -> "expense"
        }
    }

    /**
     * 判断分类
     */
    private fun determineCategory(text: String): String = when {
        text.contains("红包") -> "红包"
        text.contains("转账") -> "转账"
        else -> "其他"
    }

    /**
     * 提取金额
     */
    private fun extractAmount(texts: List<String>): Double? {
        // 优先使用带格式的金额模式
        for (text in texts) {
            for (pattern in amountPatterns) {
                val amount = tryExtractAmount(text, pattern)
                if (amount != null) return amount
            }
        }

        // 兜底：查找独立的数字
        for (text in texts) {
            val amount = tryExtractStandaloneAmount(text)
            if (amount != null) return amount
        }

        return null
    }

    /**
     * 尝试用指定模式提取金额
     */
    private fun tryExtractAmount(text: String, pattern: Pattern): Double? {
        val matcher = pattern.matcher(text)
        if (!matcher.find()) return null

        val amountStr = matcher.group(1) ?: return null
        val amount = AmountUtils.parseAmount(amountStr) ?: return null

        return if (AmountUtils.isValidAmount(amount)) amount else null
    }

    /**
     * 尝试提取独立的数字金额
     */
    private fun tryExtractStandaloneAmount(text: String): Double? {
        val trimmed = text.trim()

        // 边界检查
        if (trimmed.length > MAX_TEXT_LENGTH_FOR_AMOUNT) return null
        if (trimmed.isEmpty()) return null

        // 排除时间格式
        if (timePattern.matcher(trimmed).matches()) return null
        if (timeDotPattern.matcher(trimmed).matches()) return null

        // 匹配纯数字
        val matcher = standaloneNumberPattern.matcher(trimmed)
        if (!matcher.find()) return null

        val amount = AmountUtils.parseAmount(matcher.group(1)) ?: return null
        return if (AmountUtils.isValidAmount(amount)) amount else null
    }

    /**
     * 提取商户名称
     */
    private fun extractMerchant(texts: List<String>, joined: String): String {
        // 尝试各种提取模式
        return tryExtractFromRedPacket(texts)
            ?: tryExtractFromTransfer(texts)
            ?: getDefaultMerchant(joined)
    }

    /**
     * 从红包相关文本提取商户
     */
    private fun tryExtractFromRedPacket(texts: List<String>): String? {
        for (text in texts) {
            // "XXX的红包"
            if (text.endsWith("的红包") && text.length > 3) {
                val name = text.removeSuffix("的红包").trim()
                if (isValidMerchantName(name)) return name
            }

            // "来自XXX"
            if (text.startsWith("来自") && text.length > 2) {
                val name = text.removePrefix("来自").trim()
                if (isValidMerchantName(name)) return name
            }
        }
        return null
    }

    /**
     * 从转账相关文本提取商户
     */
    private fun tryExtractFromTransfer(texts: List<String>): String? {
        for (text in texts) {
            // "转账给XXX"
            val transferToIdx = text.indexOf("转账给")
            if (transferToIdx >= 0 && transferToIdx + 3 < text.length) {
                val name = text.substring(transferToIdx + 3).trim()
                if (isValidMerchantName(name)) return name
            }

            // "XXX向你转账"
            val transferFromIdx = text.indexOf("向你转账")
            if (transferFromIdx > 0) {
                val name = text.substring(0, transferFromIdx).trim()
                if (isValidMerchantName(name)) return name
            }
        }
        return null
    }

    /**
     * 获取默认商户名
     */
    private fun getDefaultMerchant(joined: String): String = when {
        joined.contains("发") && joined.contains("红包") -> "发出红包"
        joined.contains("红包") -> "微信红包"
        joined.contains("转账") -> "微信转账"
        else -> "微信支付"
    }

    /**
     * 验证商户名称是否有效
     */
    private fun isValidMerchantName(name: String): Boolean {
        return name.length in MIN_MERCHANT_LENGTH..Constants.MAX_MERCHANT_NAME_LENGTH
    }

    /**
     * 清理商户名称
     */
    private fun sanitizeMerchant(merchant: String): String {
        return merchant.trim().take(Constants.MAX_MERCHANT_NAME_LENGTH)
    }

    /**
     * 解析通知文本
     */
    fun parseNotification(text: String, packageName: String): ExpenseEntity? {
        if (text.isBlank()) return null

        Logger.d(TAG) { "解析通知" }

        // 检查交易关键词
        val hasIncomeKeyword = incomeKeywords.any { text.contains(it) }
        val hasExpenseKeyword = expenseKeywords.any { text.contains(it) }
        val isSendRedPacket = sendRedPacketKeywords.any { text.contains(it) }

        if (!hasIncomeKeyword && !hasExpenseKeyword && !isSendRedPacket) {
            Logger.d(TAG) { "通知中未找到交易关键词" }
            return null
        }

        // 检查排除关键词
        if (excludeKeywords.any { text.contains(it) }) {
            Logger.d(TAG) { "通知包含排除关键词" }
            return null
        }

        // 提取金额
        val amount = extractAmountFromText(text)
        if (amount == null) {
            Logger.d(TAG) { "通知中未找到有效金额" }
            return null
        }

        // 判断类型和分类
        val type = determineType(text, isSendRedPacket, hasIncomeKeyword, hasExpenseKeyword)
        val category = determineCategory(text)
        val merchant = if (isSendRedPacket) "发出红包" else "微信"

        return ExpenseEntity(
            id = 0,
            amount = amount,
            merchant = merchant,
            type = type,
            timestamp = System.currentTimeMillis(),
            channel = channelMap[packageName] ?: Channel.OTHER,
            category = category,
            categoryId = 0,
            note = "来自通知",
            rawText = text.take(Constants.RAW_TEXT_MAX_LENGTH)
        )
    }

    /**
     * 从单个文本中提取金额
     */
    private fun extractAmountFromText(text: String): Double? {
        for (pattern in amountPatterns) {
            val amount = tryExtractAmount(text, pattern)
            if (amount != null) return amount
        }
        return null
    }

    /**
     * 调试方法：打印所有文本
     */
    fun debugPrint(texts: List<String>) {
        if (!Logger.isDebug) return

        Logger.d(TAG) {
            buildString {
                appendLine("========== 页面文本 (${texts.size}条) ==========")
                texts.forEachIndexed { i, t ->
                    appendLine("[$i] ${Logger.maskText(t, 10)}")
                }
                appendLine("==========================================")
            }
        }
    }
}
