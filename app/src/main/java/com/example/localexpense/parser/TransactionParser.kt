package com.example.localexpense.parser

import com.example.localexpense.accessibility.TransactionDiagnostics
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.Channel
import com.example.localexpense.util.Constants
import com.example.localexpense.util.Logger
import com.example.localexpense.util.PackageNames
import com.example.localexpense.util.PerformanceMonitor
import com.example.localexpense.util.SafeRegexMatcher
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
 *
 * 性能优化 v1.8.2：
 * - 关键词集合预编译为高效查找结构
 * - 快速预检查减少不必要的遍历
 * - 金额模式按优先级排序，命中后立即返回
 */
object TransactionParser {

    private const val TAG = "TransactionParser"

    // 配置常量
    private const val MAX_TEXT_LENGTH_FOR_AMOUNT = 20  // 用于金额提取的最大文本长度
    private const val MIN_MERCHANT_LENGTH = 1
    private const val MAX_PARSE_TIME_MS = 50L  // 解析耗时警告阈值

    // 快速预检查字符（v1.8.2）
    // 如果文本不包含这些字符，可以快速跳过
    private val QUICK_CHECK_CHARS = charArrayOf('￥', '¥', '元', '成', '付', '收', '转', '红', '退')

    // 金额匹配模式（按优先级排序，带货币符号的优先）
    private val amountPatterns = listOf(
        // 带货币符号（最高优先级）
        Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("¥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        // 红包和转账格式
        Pattern.compile("共\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("共计\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("总计\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("合计\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        // 领取格式
        Pattern.compile("领取了\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("你领取了\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("已存入\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        // 支付宝特有格式
        Pattern.compile("付款\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("支付\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("收款\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        // 金额标签格式
        Pattern.compile("金额[：:]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("实付[：:]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("实收[：:]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        // 通用格式（最后匹配）
        Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元")
    )

    // 排除时间格式（扩展支持更多格式）
    private val timePattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}(:\\d{2})?$")  // HH:MM 或 HH:MM:SS
    private val timeDotPattern = Pattern.compile("^([01]?\\d|2[0-3])\\.([0-5]\\d)$")  // H.MM
    private val timeRangePattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}\\s*[-–—~]\\s*\\d{1,2}[:.：]\\d{2}$")  // 时间范围
    private val time12HourPattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}\\s*[AaPp][Mm]$")  // 12小时制

    // 独立数字模式（用于兜底提取）
    private val standaloneNumberPattern = Pattern.compile("^([0-9]+(?:\\.[0-9]{1,2})?)$")

    // ========== 商户提取预编译模式（v1.8.3 性能优化）==========
    // 标签格式商户模式
    private val merchantLabelPatterns = listOf(
        Pattern.compile("(?:商户|商家|收款方|付款方|店铺)[：:]\\s*(.+?)(?:\\s|$)"),
        Pattern.compile("(?:商户名称|商家名称)[：:]\\s*(.+?)(?:\\s|$)"),
        Pattern.compile("(?:收款人|付款人)[：:]\\s*(.+?)(?:\\s|$)")
    )

    // 转账商户模式
    private val transferMerchantPattern = Pattern.compile("收到(.+?)的转账")

    // 支付商户模式
    private val payToPattern = Pattern.compile("向(.+?)(?:付款|支付)")
    private val consumePattern = Pattern.compile("在(.+?)消费")
    private val dashPattern = Pattern.compile("^(.+?)\\s*[-–—]\\s*(?:支付|付款)")

    // 通知商户模式
    private val notificationMerchantPatterns = listOf(
        Pattern.compile("(?:在|向)\\s*(.{1,20})\\s*(?:消费|付款|支付)"),
        Pattern.compile("(?:收到)?(.{1,15})的红包"),
        Pattern.compile("(.{1,15})向你转账"),
        Pattern.compile("(?:商户|商家)[：:]\\s*(.{1,30})"),
        Pattern.compile("来自\\s*(.{1,20})")
    )

    // 收入关键词
    private val incomeKeywords = setOf(
        // 红包收入（增强）
        "已领取", "领取了", "已存入", "存入零钱", "红包已存入",
        "红包到账", "已存入零钱", "收到红包", "领取成功", "红包已领取",
        "拆红包成功", "恭喜发财", "大吉大利", "零钱已入账", "你领取了",
        "红包已收", "收下红包", "已收下", "拆了红包", "抢到红包",
        "手气最佳", "领了红包", "收了红包", "红包已到账",
        // 转账收入（增强）
        "向你转账", "给你转", "转账给你", "已收钱", "收款成功",
        "已到账", "入账成功", "收到转账", "已收款", "转账已收",
        "收钱成功", "到账成功", "转入成功", "收款到账",
        "转账已到账", "转账收款", "收到了转账", "已收到转账",
        "转账到账", "转账入账",
        // 二维码收款（新增）
        "二维码收款成功", "扫码收款成功", "收款码收款", "商家收款",
        // 退款
        "退款成功", "退款到账", "已退款", "退款已到账", "退款已存入",
        "已原路退回", "退款已入账", "退回成功", "退款中", "退款处理成功",
        // 支付宝特有
        "余额宝收益", "收益到账", "利息到账", "基金收益", "理财收益",
        // 银行卡到账（新增）
        "银行卡入账", "卡号入账", "工资到账", "薪资到账"
    )

    // 支出关键词
    private val expenseKeywords = setOf(
        "支付成功", "付款成功", "已付款", "已支付",
        // 转账支出（增强）
        "转账成功", "已转账", "转出成功", "转账已发出", "已转出",
        "转账发送成功", "转出", "转给", "已转给", "转账完成",
        "扫码付款", "消费", "消费成功",
        // 免密支付
        "免密支付成功", "免密支付", "小额免密", "小额免密支付",
        "已扣款", "扣款成功", "已自动扣款",
        // 自动扣款
        "自动扣款成功", "已自动扣费", "自动续费成功", "代扣成功",
        "连续包月", "自动续费", "订阅扣费",
        // 支付宝特有
        "花呗支付", "余额支付", "银行卡支付", "信用卡支付",
        "花呗分期", "分期付款", "分期支付成功",
        // 云闪付特有
        "交易成功", "刷卡成功", "银联支付",
        "碰一碰", "NFC支付", "闪付成功", "挥卡成功",
        // 话费充值（新增）
        "充值成功", "话费充值成功", "流量充值成功",
        // 生活缴费（新增）
        "缴费成功", "水费缴纳成功", "电费缴纳成功", "燃气缴费成功"
    )

    // 排除关键词（支付确认页面特征）
    // v1.9.2: 优化排除关键词，减少误过滤
    private val excludeKeywords = setOf(
        // 支付前确认页面
        "确认付款", "立即支付", "确认支付", "输入密码",
        "待支付", "去支付", "支付方式", "付款方式",
        "确认转账", "请输入", "去付款",
        // 红包准备页面
        "选择红包个数", "请填写金额", "塞钱进红包", "添加表情",
        // 验证页面
        "请确认", "指纹验证", "面容验证",
        // 设置页面
        "选择付款方式", "更换付款方式", "添加银行卡"
        // 注意：移除了"订单详情"、"商品详情"、"购买"等，因为支付成功页面可能包含这些
    )

    // 强成功关键词（即使有排除关键词，也应该处理）
    // v1.9.2 新增：更全面的成功状态关键词
    private val strongSuccessKeywords = setOf(
        // 明确的成功状态
        "成功", "完成", "到账", "已入账",
        // 已完成状态
        "已支付", "已付款", "已收款", "已转账", "已领取", "已收钱",
        "已扣款", "已自动扣款", "已存入", "已到账", "已退款"
    )

    // 发红包关键词（支出，优先级最高）
    private val sendRedPacketKeywords = setOf(
        // 微信发红包（增强）
        "发出红包", "发了一个红包", "发了红包",
        "红包发送成功", "红包已发送", "红包已发",
        "已发送", "发送成功", "已发出",
        "共发出", "个红包，共", "个红包,共",
        "红包已塞好", "已塞钱", "塞好了", "塞入红包",
        "发红包成功", "红包已发出", "发出了红包", "红包发出成功",
        "红包已塞入", "成功发出", "红包发出",
        // 支付宝发红包
        "口令红包已创建", "红包创建成功"
    )

    // 退款关键词（收入）
    private val refundKeywords = setOf(
        "退款成功", "退款到账", "已退款", "退款已到账",
        "退款已存入", "已原路退回", "退款已入账", "退回成功"
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
     * 优化：添加快速预检查，减少不必要的计算
     * v1.9.2: 添加诊断记录
     */
    private fun parseInternal(texts: List<String>, packageName: String): ExpenseEntity? {
        // 合并文本用于关键词检测
        val joined = texts.joinToString(" | ")

        // 快速预检查：如果不包含任何交易相关字符，直接返回
        if (!quickCheck(joined)) {
            TransactionDiagnostics.record(
                TransactionDiagnostics.Stage.QUICK_CHECK,
                packageName,
                TransactionDiagnostics.Result.BLOCKED,
                "文本不包含交易相关字符"
            )
            return null
        }

        TransactionDiagnostics.record(
            TransactionDiagnostics.Stage.QUICK_CHECK,
            packageName,
            TransactionDiagnostics.Result.PASSED
        )

        Logger.d(TAG) { "开始解析, 包名: $packageName, 文本数: ${texts.size}" }

        // 步骤 0: 检查排除关键词
        if (!shouldProcess(joined)) {
            Logger.d(TAG) { "检测到确认页面，跳过记录" }
            TransactionDiagnostics.record(
                TransactionDiagnostics.Stage.EXCLUDE_CHECK,
                packageName,
                TransactionDiagnostics.Result.BLOCKED,
                "包含排除关键词且无成功状态"
            )
            return null
        }

        // 步骤 1: 优先使用规则引擎匹配
        val ruleMatch = tryRuleEngine(texts, packageName, joined)
        if (ruleMatch != null) {
            TransactionDiagnostics.record(
                TransactionDiagnostics.Stage.RULE_ENGINE_MATCH,
                packageName,
                TransactionDiagnostics.Result.PASSED,
                "规则引擎匹配成功",
                ruleMatch.amount,
                ruleMatch.merchant
            )
            return ruleMatch
        }

        // 步骤 2: 降级到传统解析
        val legacyResult = tryLegacyParse(texts, packageName, joined)
        if (legacyResult != null) {
            TransactionDiagnostics.record(
                TransactionDiagnostics.Stage.LEGACY_PARSE,
                packageName,
                TransactionDiagnostics.Result.PASSED,
                "传统解析成功",
                legacyResult.amount,
                legacyResult.merchant
            )
        } else {
            TransactionDiagnostics.record(
                TransactionDiagnostics.Stage.LEGACY_PARSE,
                packageName,
                TransactionDiagnostics.Result.BLOCKED,
                "未找到有效交易信息"
            )
        }
        return legacyResult
    }

    /**
     * 快速预检查（v1.8.2）
     * 检查文本是否包含任何交易相关字符
     */
    private fun quickCheck(text: String): Boolean {
        for (char in QUICK_CHECK_CHARS) {
            if (text.indexOf(char) >= 0) {
                return true
            }
        }
        // 额外检查数字（可能是金额）
        for (c in text) {
            if (c.isDigit()) {
                return true
            }
        }
        return false
    }

    /**
     * 检查是否应该处理这个页面
     * v1.9.2 优化：使用更智能的判断逻辑，减少漏记
     */
    private fun shouldProcess(joined: String): Boolean {
        val hasExcludeKeyword = excludeKeywords.any { joined.contains(it) }
        if (!hasExcludeKeyword) return true

        // 如果有强成功关键词，应该处理
        val hasStrongSuccess = strongSuccessKeywords.any { joined.contains(it) }
        if (hasStrongSuccess) {
            Logger.d(TAG) { "检测到排除关键词但有强成功状态，继续处理" }
            return true
        }

        // 如果有明确的交易成功关键词，仍然处理
        val hasSuccessKeyword = sendRedPacketKeywords.any { joined.contains(it) } ||
                expenseKeywords.any { joined.contains(it) } ||
                incomeKeywords.any { joined.contains(it) }

        if (hasSuccessKeyword) {
            Logger.d(TAG) { "检测到排除关键词但有交易成功关键词，继续处理" }
        }

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
        val hasRefund = refundKeywords.any { joined.contains(it) }

        if (!hasIncomeKeyword && !hasExpenseKeyword && !hasSendRedPacket && !hasRefund) {
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
        val type = determineType(joined, hasSendRedPacket, hasIncomeKeyword, hasExpenseKeyword, hasRefund)
        val category = determineCategory(joined)
        val merchant = extractMerchant(texts, joined, packageName)

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
        isExpense: Boolean,
        isRefund: Boolean = false
    ): String {
        return when {
            isRefund -> "income"  // 退款是收入
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
        text.contains("退款") || text.contains("退回") -> "退款"
        text.contains("红包") -> "红包"
        text.contains("转账") -> "转账"
        text.contains("自动扣款") || text.contains("自动续费") || text.contains("连续包月") -> "自动扣款"
        text.contains("免密") -> "免密支付"
        else -> "其他"
    }

    /**
     * 提取金额
     * 优化：先快速过滤可能包含金额的文本，再进行复杂匹配
     */
    private fun extractAmount(texts: List<String>): Double? {
        // 快速过滤：只处理可能包含金额的文本
        val potentialAmountTexts = texts.filter { text ->
            text.contains('￥') || text.contains('¥') ||
            text.contains('元') || text.any { it.isDigit() }
        }

        // 优先使用带格式的金额模式
        for (text in potentialAmountTexts) {
            for (pattern in amountPatterns) {
                val amount = tryExtractAmount(text, pattern)
                if (amount != null) return amount
            }
        }

        // 兜底：查找独立的数字
        for (text in potentialAmountTexts) {
            val amount = tryExtractStandaloneAmount(text)
            if (amount != null) return amount
        }

        return null
    }

    /**
     * 尝试用指定模式提取金额
     * 优化：使用 SafeRegexMatcher 防止 ReDoS
     */
    private fun tryExtractAmount(text: String, pattern: Pattern): Double? {
        val result = SafeRegexMatcher.findWithTimeout(pattern, text)
        if (result == null || !result.matched) return null

        val amountStr = result.group(1) ?: return null
        val amount = AmountUtils.parseAmount(amountStr) ?: return null

        return if (AmountUtils.isValidAmount(amount)) amount else null
    }

    /**
     * 尝试提取独立的数字金额
     * 优化：使用 SafeRegexMatcher 防止 ReDoS
     */
    private fun tryExtractStandaloneAmount(text: String): Double? {
        val trimmed = text.trim()

        // 边界检查
        if (trimmed.length > MAX_TEXT_LENGTH_FOR_AMOUNT) return null
        if (trimmed.isEmpty()) return null

        // 排除时间格式（使用 SafeRegexMatcher）
        if (SafeRegexMatcher.matchesWithTimeout(timePattern, trimmed)) return null
        if (SafeRegexMatcher.matchesWithTimeout(timeDotPattern, trimmed)) return null
        if (SafeRegexMatcher.matchesWithTimeout(timeRangePattern, trimmed)) return null
        if (SafeRegexMatcher.matchesWithTimeout(time12HourPattern, trimmed)) return null

        // 匹配纯数字
        val result = SafeRegexMatcher.findWithTimeout(standaloneNumberPattern, trimmed)
        if (result == null || !result.matched) return null

        val amount = AmountUtils.parseAmount(result.group(1)) ?: return null
        return if (AmountUtils.isValidAmount(amount)) amount else null
    }

    /**
     * 提取商户名称
     */
    private fun extractMerchant(texts: List<String>, joined: String, packageName: String? = null): String {
        // 尝试各种提取模式（按优先级）
        return tryExtractFromLabel(texts)           // 1. 标签格式（最可靠）
            ?: tryExtractFromRedPacket(texts)       // 2. 红包格式
            ?: tryExtractFromTransfer(texts)        // 3. 转账格式
            ?: tryExtractFromPayment(texts)         // 4. 支付格式
            ?: getDefaultMerchant(joined, packageName)  // 5. 默认（根据渠道）
    }

    /**
     * 从标签格式提取商户（如：商户：xxx、收款方：xxx）
     * 优化：使用预编译的正则模式，防止 ReDoS
     */
    private fun tryExtractFromLabel(texts: List<String>): String? {
        for (text in texts) {
            for (pattern in merchantLabelPatterns) {
                val result = SafeRegexMatcher.findWithTimeout(pattern, text)
                if (result != null && result.matched) {
                    val name = result.group(1)?.trim()
                    if (!name.isNullOrBlank() && isValidMerchantName(name)) {
                        return name
                    }
                }
            }
        }
        return null
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

            // "XXX发出的红包"
            val sentIdx = text.indexOf("发出的红包")
            if (sentIdx > 0) {
                val name = text.substring(0, sentIdx).trim()
                if (isValidMerchantName(name)) return name
            }

            // "XXX发的红包"
            val sentIdx2 = text.indexOf("发的红包")
            if (sentIdx2 > 0) {
                val name = text.substring(0, sentIdx2).trim()
                if (isValidMerchantName(name)) return name
            }
        }
        return null
    }

    /**
     * 从转账相关文本提取商户
     * 修复：避免使用魔数，使用字符串长度计算
     */
    private fun tryExtractFromTransfer(texts: List<String>): String? {
        for (text in texts) {
            // "转账给XXX"
            val transferToKeyword = "转账给"
            val transferToIdx = text.indexOf(transferToKeyword)
            if (transferToIdx >= 0) {
                val startIdx = transferToIdx + transferToKeyword.length
                if (startIdx < text.length) {
                    val name = text.substring(startIdx).trim()
                    if (isValidMerchantName(name)) return name
                }
            }

            // "转给XXX"
            val transferToKeyword2 = "转给"
            val transferToIdx2 = text.indexOf(transferToKeyword2)
            if (transferToIdx2 >= 0) {
                val startIdx = transferToIdx2 + transferToKeyword2.length
                if (startIdx < text.length) {
                    val name = text.substring(startIdx).trim()
                    if (isValidMerchantName(name)) return name
                }
            }

            // "XXX向你转账"
            val transferFromKeyword = "向你转账"
            val transferFromIdx = text.indexOf(transferFromKeyword)
            if (transferFromIdx > 0) {
                val name = text.substring(0, transferFromIdx).trim()
                if (isValidMerchantName(name)) return name
            }

            // "XXX给你转账"
            val transferFromKeyword2 = "给你转账"
            val transferFromIdx2 = text.indexOf(transferFromKeyword2)
            if (transferFromIdx2 > 0) {
                val name = text.substring(0, transferFromIdx2).trim()
                if (isValidMerchantName(name)) return name
            }

            // "收到XXX的转账"
            val result = SafeRegexMatcher.findWithTimeout(transferMerchantPattern, text)
            if (result != null && result.matched) {
                val name = result.group(1)?.trim()
                if (!name.isNullOrBlank() && isValidMerchantName(name)) return name
            }
        }
        return null
    }

    /**
     * 从支付相关文本提取商户
     * 优化：使用预编译的正则模式，防止 ReDoS
     */
    private fun tryExtractFromPayment(texts: List<String>): String? {
        for (text in texts) {
            // "向XXX付款"
            val payToResult = SafeRegexMatcher.findWithTimeout(payToPattern, text)
            if (payToResult != null && payToResult.matched) {
                val name = payToResult.group(1)?.trim()
                if (!name.isNullOrBlank() && isValidMerchantName(name)) return name
            }

            // "在XXX消费"
            val consumeResult = SafeRegexMatcher.findWithTimeout(consumePattern, text)
            if (consumeResult != null && consumeResult.matched) {
                val name = consumeResult.group(1)?.trim()
                if (!name.isNullOrBlank() && isValidMerchantName(name)) return name
            }

            // "XXX-支付成功" 或 "XXX - 付款成功"
            val dashResult = SafeRegexMatcher.findWithTimeout(dashPattern, text)
            if (dashResult != null && dashResult.matched) {
                val name = dashResult.group(1)?.trim()
                if (!name.isNullOrBlank() && isValidMerchantName(name)) return name
            }
        }
        return null
    }

    /**
     * 获取默认商户名
     * 根据包名返回对应渠道的默认商户名
     */
    private fun getDefaultMerchant(joined: String, packageName: String? = null): String {
        val channelName = when (packageName) {
            PackageNames.WECHAT -> "微信"
            PackageNames.ALIPAY -> "支付宝"
            PackageNames.UNIONPAY -> "云闪付"
            else -> "支付"
        }
        return when {
            joined.contains("发") && joined.contains("红包") -> "发出红包"
            joined.contains("红包") -> "${channelName}红包"
            joined.contains("转账") -> "${channelName}转账"
            else -> "${channelName}支付"
        }
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
     * 优化：增加商户提取逻辑
     */
    fun parseNotification(text: String, packageName: String): ExpenseEntity? {
        if (text.isBlank()) return null

        Logger.d(TAG) { "解析通知" }

        // 检查交易关键词
        val hasIncomeKeyword = incomeKeywords.any { text.contains(it) }
        val hasExpenseKeyword = expenseKeywords.any { text.contains(it) }
        val isSendRedPacket = sendRedPacketKeywords.any { text.contains(it) }
        val isRefund = refundKeywords.any { text.contains(it) }

        if (!hasIncomeKeyword && !hasExpenseKeyword && !isSendRedPacket && !isRefund) {
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
        val type = determineType(text, isSendRedPacket, hasIncomeKeyword, hasExpenseKeyword, isRefund)
        val category = determineCategory(text)

        // 尝试从通知文本中提取商户名
        val merchant = extractMerchantFromNotification(text, packageName, isRefund, isSendRedPacket)

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
     * 从通知文本中提取商户名
     * 优化：使用预编译的正则模式
     */
    private fun extractMerchantFromNotification(
        text: String,
        packageName: String,
        isRefund: Boolean,
        isSendRedPacket: Boolean
    ): String {
        // 尝试多种模式提取商户
        for (pattern in notificationMerchantPatterns) {
            val result = SafeRegexMatcher.findWithTimeout(pattern, text)
            if (result != null && result.matched) {
                val name = result.group(1)?.trim()
                if (!name.isNullOrBlank() && name.length >= MIN_MERCHANT_LENGTH) {
                    return sanitizeMerchant(name)
                }
            }
        }

        // 根据渠道和类型返回默认商户名
        val channelName = when (packageName) {
            PackageNames.WECHAT -> "微信"
            PackageNames.ALIPAY -> "支付宝"
            PackageNames.UNIONPAY -> "云闪付"
            else -> "支付"
        }

        return when {
            isRefund -> "退款"
            isSendRedPacket -> "发出红包"
            text.contains("红包") -> "${channelName}红包"
            text.contains("转账") -> "${channelName}转账"
            else -> channelName
        }
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
