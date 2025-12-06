package com.example.localexpense.parser

import android.util.Log
import com.example.localexpense.BuildConfig
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.Channel
import com.example.localexpense.util.Constants
import java.util.regex.Pattern

object TransactionParser {

    private const val TAG = "TransactionParser"

    // 金额匹配模式（支持逗号分隔的金额，如 1,000.00）
    // 注意：按优先级排序，带货币符号的优先
    private val amountPatterns = listOf(
        Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("¥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元"),
        Pattern.compile("金额[：:]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
    )
    
    // 排除时间格式的正则（如 12:30, 09:45）
    private val timePattern = Pattern.compile("^\\d{1,2}[:.:]\\d{2}$")

    // 收入关键词
    private val incomeKeywords = listOf(
        "已领取", "领取了", "已存入", "存入零钱", "收到",
        "向你转账", "给你转", "转账给你", "已收钱", "收款成功",
        "红包已存入", "已到账", "入账成功"
    )

    // 支出关键词
    private val expenseKeywords = listOf(
        "支付成功", "付款成功", "已付款", "已支付",
        "转账成功", "已转账", "转账给",
        "扫码付款", "消费"
    )

    // 发红包关键词（更精确）
    private val sendRedPacketKeywords = listOf(
        "发红包", "发出红包", "发了一个红包",
        "塞钱进红包", "红包发送成功", "红包已发送",
        "共发出", "个红包，共"
    )

    // 渠道映射 - 使用 Channel 常量
    private val channelMap = Channel.PACKAGE_MAP

    fun parse(texts: List<String>, packageName: String): ExpenseEntity? {
        val joined = texts.joinToString(" | ")
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "开始解析, 包名: $packageName")
        }
        
        // 1. 提取金额
        val amount = extractAmount(texts)
        if (amount == null || amount <= 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "未找到有效金额")
            return null
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "找到金额: ***")

        // 2. 判断收入还是支出
        val isIncome = incomeKeywords.any { joined.contains(it) }
        val isExpense = expenseKeywords.any { joined.contains(it) }

        // 发红包判断：使用专门的关键词列表，更精确
        val isSendRedPacket = sendRedPacketKeywords.any { joined.contains(it) }

        val type = when {
            isSendRedPacket -> "expense"  // 发红包优先判断为支出
            isIncome && !isExpense -> "income"
            isExpense -> "expense"
            joined.contains("红包") -> "income"  // 其他红包场景默认收入
            else -> "expense"
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "交易类型: $type (isIncome=$isIncome, isExpense=$isExpense, isSendRedPacket=$isSendRedPacket)")
        }

        // 3. 提取商户/来源
        val merchant = extractMerchant(texts, joined)
        if (BuildConfig.DEBUG) Log.d(TAG, "商户: [已隐藏]")

        // 4. 分类
        val category = when {
            joined.contains("红包") -> "红包"
            joined.contains("转账") -> "转账"
            else -> "其他"
        }

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
                    if (amount != null && amount > 0 && amount < Constants.MAX_AMOUNT) {
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
            // 跳过时间格式
            if (timePattern.matcher(trimmed).matches()) continue
            
            val numPattern = Pattern.compile("^([0-9]+(?:\\.[0-9]{1,2})?)$")
            val matcher = numPattern.matcher(trimmed)
            if (matcher.find()) {
                // 使用 BigDecimal 进行精确解析
                val amount = AmountUtils.parseAmount(matcher.group(1))
                // 排除可能是时间的数字（如 12.30 可能是时间）
                if (amount != null && amount > 0 && amount < Constants.MAX_AMOUNT) {
                    // 如果是 XX.XX 格式且第一部分 < 24，可能是时间，跳过
                    if (trimmed.contains(".") && trimmed.split(".")[0].toIntOrNull()?.let { it < 24 } == true) {
                        continue
                    }
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
                val name = text.substring(idx + 3).trim()
                if (name.isNotEmpty() && name.length <= Constants.MAX_MERCHANT_NAME_LENGTH) {
                    return name
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
        
        // 提取金额（使用 BigDecimal 精确解析）
        var amount: Double? = null
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val parsed = AmountUtils.parseAmount(matcher.group(1))
                // 添加金额上限检查
                if (parsed != null && parsed > 0 && parsed < Constants.MAX_AMOUNT) {
                    amount = parsed
                    break
                }
            }
        }

        if (amount == null || amount <= 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "通知中未找到金额")
            return null
        }
        
        // 判断类型
        val isIncome = incomeKeywords.any { text.contains(it) }
        val isExpense = expenseKeywords.any { text.contains(it) }
        val isSendRedPacket = sendRedPacketKeywords.any { text.contains(it) }

        val type = when {
            isSendRedPacket -> "expense"
            isIncome -> "income"
            isExpense -> "expense"
            text.contains("红包") -> "income"
            else -> "expense"
        }
        
        val category = when {
            text.contains("红包") -> "红包"
            text.contains("转账") -> "转账"
            else -> "其他"
        }
        
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
