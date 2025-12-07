package com.example.localexpense.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 应用常量定义
 */
object Constants {
    // 防重复检测时间间隔（毫秒）
    // 设置为5秒，平衡误过滤和重复记录的风险
    // 注意：实际判断使用 金额+商户+类型 组合，所以相同金额但不同商户不会被过滤
    const val DUPLICATE_CHECK_INTERVAL_MS = 5000L

    // 原始文本最大长度
    const val RAW_TEXT_MAX_LENGTH = 300

    // 搜索防抖延迟（毫秒）
    const val SEARCH_DEBOUNCE_MS = 300L

    // 金额最大值（单笔交易上限，用于过滤异常值）
    // 日常消费很少超过1万，设置为10000可以过滤大部分误识别
    const val MAX_AMOUNT = 10000.0

    // 商户名称最大长度（与RuleEngine保持一致）
    const val MAX_MERCHANT_NAME_LENGTH = 50

    // 通知ID取模范围
    const val NOTIFICATION_ID_MOD = 10000

    // 数据库名称
    const val DATABASE_NAME = "local_expense.db"

    // 需要过滤的特殊号码（运营商客服、银行等）
    val BLACKLIST_AMOUNTS = setOf(
        // 运营商
        10086.0,  // 中国移动
        10010.0,  // 中国联通
        10000.0,  // 中国电信
        // 银行客服
        95588.0,  // 工商银行
        95533.0,  // 建设银行
        95566.0,  // 中国银行
        95555.0,  // 招商银行
        95568.0,  // 民生银行
        95599.0,  // 农业银行
        95528.0,  // 浦发银行
        95558.0,  // 中信银行
        95561.0,  // 兴业银行
        95508.0,  // 广发银行
        95516.0,  // 银联
        95559.0,  // 交通银行
        95595.0,  // 光大银行
        95511.0,  // 平安银行
        95526.0,  // 华夏银行
        95577.0,  // 华夏银行（新）
        95580.0,  // 邮储银行
        // 客服热线
        400.0,    // 400开头的客服
        800.0,    // 800开头的客服
        // 紧急服务
        110.0,    // 报警
        120.0,    // 急救
        119.0,    // 火警
        122.0,    // 交通事故
        // 公共服务
        12315.0,  // 消费者投诉
        12306.0,  // 铁路客服
        12345.0,  // 政务服务
        114.0,    // 查号台
        // 常见误识别
        1234.0,   // 可能是验证码
        123456.0  // 可能是密码提示
    )
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

    /**
     * 验证金额是否有效
     * - 金额大于0
     * - 金额小于上限
     * - 不在黑名单中（如10086等特殊号码）
     */
    fun isValidAmount(amount: Double): Boolean {
        if (amount <= 0) return false
        if (amount > Constants.MAX_AMOUNT) return false
        if (amount in Constants.BLACKLIST_AMOUNTS) return false
        return true
    }
}
