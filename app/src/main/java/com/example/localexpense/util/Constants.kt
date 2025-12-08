package com.example.localexpense.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 应用常量定义
 */
object Constants {
    // 防重复检测时间间隔（毫秒）
    // 增加到15秒，因为支付宝等应用会多次触发页面刷新
    // 注意：实际判断使用 金额+商户+类型 组合，所以相同金额但不同商户不会被过滤
    const val DUPLICATE_CHECK_INTERVAL_MS = 15000L

    // 支付宝专用的去重时间窗口（更长，因为支付宝页面刷新频繁）
    const val ALIPAY_DUPLICATE_CHECK_INTERVAL_MS = 30000L

    // 原始文本最大长度
    const val RAW_TEXT_MAX_LENGTH = 300

    // 搜索防抖延迟（毫秒）
    const val SEARCH_DEBOUNCE_MS = 300L

    // 金额最大值（单笔交易上限，用于过滤异常值）
    // 提高到5万以支持大额转账和消费场景
    const val MAX_AMOUNT = 50000.0

    // 商户名称最大长度（与RuleEngine保持一致）
    const val MAX_MERCHANT_NAME_LENGTH = 50

    // 备注最大长度
    const val MAX_NOTE_LENGTH = 100

    // 分类名称最大长度
    const val MAX_CATEGORY_NAME_LENGTH = 20

    // 搜索关键词最大长度
    const val MAX_SEARCH_QUERY_LENGTH = 50

    // 单个文本最大长度（用于过滤无效文本）
    const val MAX_SINGLE_TEXT_LENGTH = 500

    // 通知ID取模范围
    const val NOTIFICATION_ID_MOD = 10000

    // ========== 无障碍服务相关常量 ==========

    // OCR 冷却时间（毫秒）
    const val OCR_COOLDOWN_MS = 2000L

    // 节点树遍历最大深度
    const val MAX_NODE_COLLECT_DEPTH = 25

    // 单个节点最大子节点数量
    const val MAX_CHILD_NODE_COUNT = 100

    // 最大收集文本数量
    const val MAX_COLLECTED_TEXT_COUNT = 300

    // 监控时间容差（毫秒）
    const val MONITOR_TIME_TOLERANCE_MS = 500L

    // 数据库名称
    const val DATABASE_NAME = "local_expense.db"

    // 需要过滤的特殊号码（运营商客服、银行等）
    // 注意：只过滤明显不可能是消费金额的数字（5位以上客服号码）
    // 移除了可能是真实消费的金额（如12306铁路订票）
    val BLACKLIST_AMOUNTS = setOf(
        // 运营商客服号码（5位数，不可能是消费金额）
        10086.0,  // 中国移动
        10010.0,  // 中国联通
        10000.0,  // 中国电信
        // 银行客服号码（5位数，不可能是消费金额）
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
        // 常见误识别（6位数密码提示）
        123456.0  // 可能是密码提示
        // 注意：移除了 12306、12315、12345 等可能是真实消费金额的值
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
        PackageNames.WECHAT to WECHAT,
        PackageNames.ALIPAY to ALIPAY,
        PackageNames.UNIONPAY to UNIONPAY
    )
}

/**
 * 监控的应用包名
 * 集中管理所有硬编码的包名，方便维护
 */
object PackageNames {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
    const val UNIONPAY = "com.unionpay"

    // 所有监控的包名集合
    val MONITORED_PACKAGES = setOf(WECHAT, ALIPAY, UNIONPAY)
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
