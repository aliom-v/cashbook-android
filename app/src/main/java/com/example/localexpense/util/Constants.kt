package com.example.localexpense.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 应用常量定义
 */
object Constants {
    // 防重复检测时间间隔（毫秒）
    const val DUPLICATE_CHECK_INTERVAL_MS = 3000L

    // 原始文本最大长度
    const val RAW_TEXT_MAX_LENGTH = 300

    // 搜索防抖延迟（毫秒）
    const val SEARCH_DEBOUNCE_MS = 300L

    // 金额最大值（用于过滤异常值）
    const val MAX_AMOUNT = 1000000.0

    // 商户名称最大长度
    const val MAX_MERCHANT_NAME_LENGTH = 15

    // 通知ID取模范围
    const val NOTIFICATION_ID_MOD = 10000

    // 数据库名称
    const val DATABASE_NAME = "local_expense.db"
}

/**
 * 交易类型
 */
object TransactionType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
}

/**
 * 渠道类型
 */
object Channel {
    const val WECHAT = "微信"
    const val ALIPAY = "支付宝"
    const val UNIONPAY = "云闪付"
    const val MANUAL = "手动"
    const val OTHER = "其他"

    // 包名映射
    val PACKAGE_MAP = mapOf(
        "com.tencent.mm" to WECHAT,
        "com.eg.android.AlipayGphone" to ALIPAY,
        "com.unionpay" to UNIONPAY
    )
}

/**
 * 默认分类名称
 */
object CategoryNames {
    const val FOOD = "餐饮"
    const val SHOPPING = "购物"
    const val TRANSPORT = "交通"
    const val ENTERTAINMENT = "娱乐"
    const val LIVING = "生活"
    const val MEDICAL = "医疗"
    const val EDUCATION = "教育"
    const val SALARY = "工资"
    const val BONUS = "奖金"
    const val RED_PACKET = "红包"
    const val TRANSFER = "转账"
    const val INVESTMENT = "投资"
    const val PART_TIME = "兼职"
    const val OTHER = "其他"
}

/**
 * 金额工具类
 * 使用 BigDecimal 进行精确计算，避免浮点数精度问题
 */
object AmountUtils {
    private const val SCALE = 2 // 保留2位小数

    /**
     * 安全地将字符串转换为 Double
     */
    fun parseAmount(str: String?): Double? {
        if (str.isNullOrBlank()) return null
        return try {
            BigDecimal(str.replace(",", ""))
                .setScale(SCALE, RoundingMode.HALF_UP)
                .toDouble()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 格式化金额显示
     */
    fun format(amount: Double): String {
        return BigDecimal(amount)
            .setScale(SCALE, RoundingMode.HALF_UP)
            .toString()
    }

    /**
     * 安全加法
     */
    fun add(a: Double, b: Double): Double {
        return BigDecimal(a).add(BigDecimal(b))
            .setScale(SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * 安全减法
     */
    fun subtract(a: Double, b: Double): Double {
        return BigDecimal(a).subtract(BigDecimal(b))
            .setScale(SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * 安全除法（带除零保护）
     */
    fun divide(a: Double, b: Double): Double {
        if (b == 0.0) return 0.0
        return BigDecimal(a).divide(BigDecimal(b), SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * 计算百分比
     */
    fun percentage(part: Double, total: Double): Double {
        if (total == 0.0) return 0.0
        return divide(part * 100, total)
    }
}
