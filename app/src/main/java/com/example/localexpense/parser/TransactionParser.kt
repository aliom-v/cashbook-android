package com.example.localexpense.parser

import android.util.Log
import com.example.localexpense.BuildConfig
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.Channel
import com.example.localexpense.util.Constants
import java.util.regex.Pattern

/**
 * 交易解析器
 * 负责从微信/支付宝页面文本或通知中提取交易信息
 */
object TransactionParser {

    private const val TAG = "TransactionParser"

    // 金额匹配模式（按优先级排序，带货币符号的优先）
    private val amountPatterns = listOf(
        Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("¥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("共\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),  // 红包格式："共5.00元"
        Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("金额[：:]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
    )
    
    // 排除时间格式（如 12:30, 09:45, 12.30）
    private val timePattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}$")
    // 时间格式（带小数点，如 12.30 可能是时间）
    private val timeDotPattern = Pattern.compile("^([01]?\\d|2[0-3])\\.([0-5]\\d)$")

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

    // 排除关键词（支付确认页面特征，出现时不记录）
    // 注意：只添加明确是确认页面、不可能出现在成功页面的关键词
    // 与 transaction_rules.json 中的 excludeKeywords 保持一致
    private val excludeKeywords = setOf(
        "确认付款", "立即支付", "确认支付", "输入密码",
        "待支付", "去支付", "支付方式", "付款方式",
        "确认转账", "请输入", "去付款",
        "选择红包个数", "请填写金额", "塞钱进红包", "添加表情"
    )

    // 发红包关键词（优先级最高）- 只匹配发送成功的场景
    private val sendRedPacketKeywords = setOf(
        "发出红包", "发了一个红包",
        "红包发送成功", "红包已发送",
        "已发送", "发送成功",
        "共发出", "个红包，共",
        "红包已塞好", "已塞钱", "塞好了",  // 微信红包成功页面
        "发红包成功", "红包已发出", "发出了红包", "红包发出成功"
    )

    // 渠道映射
    private val channelMap = Channel.PACKAGE_MAP

    /**
     * 判断交易类型
     */
    private fun determineType(text: String): String {
        val isSendRedPacket = sendRedPacketKeywords.any { text.contains(it) }
        val isIncome = incomeKeywords.any { text.contains(it) }
        val isExpense = expenseKeywords.any { text.contains(it) }

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

    fun parse(texts: List<String>, packageName: String): ExpenseEntity? {
        val joined = texts.joinToString(" | ")

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "开始解析, 包名: $packageName")
        }

        // 0. 检查排除关键词（支付确认页面，跳过）
        // 但如果同时检测到明确的成功关键词，则不跳过（发红包成功页面可能包含"塞钱进红包"等历史元素）
        val hasExcludeKeyword = excludeKeywords.any { joined.contains(it) }
        val hasSuccessKeyword = sendRedPacketKeywords.any { joined.contains(it) } ||
                expenseKeywords.any { joined.contains(it) } ||
                incomeKeywords.any { joined.contains(it) }

        if (hasExcludeKeyword && !hasSuccessKeyword) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "检测到确认页面关键词且无成功关键词，跳过记录")
            }
            return null
        }

        // 1. 优先使用规则引擎匹配（更准确、可热更新）
        val ruleMatch = RuleEngine.match(texts, packageName)
        if (ruleMatch != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✓ 规则引擎匹配成功: ${ruleMatch.rule.category}")
            }
            return ExpenseEntity(
                id = 0,
                amount = ruleMatch.amount,
                merchant = ruleMatch.merchant,
                type = ruleMatch.rule.type,
                timestamp = System.currentTimeMillis(),
                channel = channelMap[packageName] ?: "其他",
                category = ruleMatch.rule.category,
                categoryId = 0,
                note = "",
                rawText = joined.take(Constants.RAW_TEXT_MAX_LENGTH)
            )
        }

        // 2. 降级到原有解析逻辑（向后兼容）
        // 注意：传统解析也必须要求有明确的交易关键词，防止误记录
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "规则引擎未匹配，使用传统解析")
        }

        // 先检查是否有交易相关关键词（必须有收入或支出关键词才继续）
        val hasIncomeKeyword = incomeKeywords.any { joined.contains(it) }
        val hasExpenseKeyword = expenseKeywords.any { joined.contains(it) }
        val hasSendRedPacket = sendRedPacketKeywords.any { joined.contains(it) }

        if (!hasIncomeKeyword && !hasExpenseKeyword && !hasSendRedPacket) {
            if (BuildConfig.DEBUG) Log.d(TAG, "未找到交易关键词，跳过记录")
            return null
        }

        // 提取金额
        val amount = extractAmount(texts)
        if (amount == null || amount <= 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "未找到有效金额")
            return null
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "找到金额: ***")

        // 判断交易类型和分类
        val type = determineType(joined)
        val category = determineCategory(joined)
        if (BuildConfig.DEBUG) Log.d(TAG, "交易类型: $type")

        // 提取商户/来源
        val merchant = extractMerchant(texts, joined)
        if (BuildConfig.DEBUG) Log.d(TAG, "商户: [已隐藏]")

        return ExpenseEntity(
            id = 0,
            amount = amount,
            merchant = merchant,
            type = type,
            timestamp = System.currentTimeMillis(),
            channel = channelMap[packageName] ?: "其他",
            category = category,
            categoryId = 0,
            note = "",
            rawText = joined.take(Constants.RAW_TEXT_MAX_LENGTH)
        )
    }

    private fun extractAmount(texts: List<String>): Double? {
        // 遍历所有文本，用所有模式匹配
        for (text in texts) {
            for (pattern in amountPatterns) {
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

        // 兜底：找独立的数字（可能是金额）
        for (text in texts) {
            val trimmed = text.trim()
            // 跳过太长的文本（可能是订单号）
            if (trimmed.length > 20) continue
            // 跳过时间格式 (如 12:30, 09:45)
            if (timePattern.matcher(trimmed).matches()) continue
            // 跳过时间格式 (带小数点，如 12.30 表示12点30分)
            if (timeDotPattern.matcher(trimmed).matches()) continue

            val numPattern = Pattern.compile("^([0-9]+(?:\\.[0-9]{1,2})?)$")
            val matcher = numPattern.matcher(trimmed)
            if (matcher.find()) {
                // 使用 BigDecimal 进行精确解析
                val amount = AmountUtils.parseAmount(matcher.group(1))
                if (amount != null && AmountUtils.isValidAmount(amount)) {
                    return amount
                }
            }
        }

        return null
    }

    private fun extractMerchant(texts: List<String>, joined: String): String {
        // 找 "XXX的红包"
        for (text in texts) {
            if (text.endsWith("的红包")) {
                val name = text.removeSuffix("的红包").trim()
                if (name.isNotEmpty() && name.length <= Constants.MAX_MERCHANT_NAME_LENGTH) {
                    return name
                }
            }
        }

        // 找 "来自XXX"
        for (text in texts) {
            if (text.startsWith("来自")) {
                val name = text.removePrefix("来自").trim()
                if (name.isNotEmpty() && name.length <= Constants.MAX_MERCHANT_NAME_LENGTH) {
                    return name
                }
            }
        }

        // 找 "转账给XXX" 或 "XXX转账"
        for (text in texts) {
            if (text.contains("转账给")) {
                val idx = text.indexOf("转账给")
                // 边界检查：确保 "转账给" 后面还有内容
                if (idx + 3 < text.length) {
                    val name = text.substring(idx + 3).trim()
                    if (name.isNotEmpty() && name.length <= Constants.MAX_MERCHANT_NAME_LENGTH) {
                        return name
                    }
                }
            }
        }

        // 默认
        return when {
            joined.contains("发") && joined.contains("红包") -> "发出红包"
            joined.contains("红包") -> "微信红包"
            joined.contains("转账") -> "微信转账"
            else -> "微信支付"
        }
    }
    
    // 解析通知文本
    fun parseNotification(text: String, packageName: String): ExpenseEntity? {
        if (BuildConfig.DEBUG) Log.d(TAG, "解析通知: [内容已隐藏]")

        // 先检查是否有交易关键词（必须有明确的交易词才继续）
        val hasIncomeKeyword = incomeKeywords.any { text.contains(it) }
        val hasExpenseKeyword = expenseKeywords.any { text.contains(it) }
        val isSendRedPacket = sendRedPacketKeywords.any { text.contains(it) }

        if (!hasIncomeKeyword && !hasExpenseKeyword && !isSendRedPacket) {
            if (BuildConfig.DEBUG) Log.d(TAG, "通知中未找到交易关键词，跳过")
            return null
        }

        // 检查排除关键词（非交易通知）
        if (excludeKeywords.any { text.contains(it) }) {
            if (BuildConfig.DEBUG) Log.d(TAG, "通知包含排除关键词，跳过")
            return null
        }

        // 提取金额（使用 BigDecimal 精确解析）
        var amount: Double? = null
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val parsed = AmountUtils.parseAmount(matcher.group(1))
                // 使用统一的金额验证
                if (parsed != null && AmountUtils.isValidAmount(parsed)) {
                    amount = parsed
                    break
                }
            }
        }

        if (amount == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "通知中未找到有效金额")
            return null
        }

        // 判断类型和分类
        val type = determineType(text)
        val category = determineCategory(text)
        
        return ExpenseEntity(
            id = 0,
            amount = amount,
            merchant = if (isSendRedPacket) "发出红包" else "微信",
            type = type,
            timestamp = System.currentTimeMillis(),
            channel = channelMap[packageName] ?: "其他",
            category = category,
            categoryId = 0,
            note = "来自通知",
            rawText = text.take(Constants.RAW_TEXT_MAX_LENGTH)
        )
    }
    
    // 调试方法：打印所有文本
    fun debugPrint(texts: List<String>) {
        Log.d(TAG, "========== 页面文本 ==========")
        texts.forEachIndexed { i, t ->
            Log.d(TAG, "[$i] $t")
        }
        Log.d(TAG, "==============================")
    }
}
