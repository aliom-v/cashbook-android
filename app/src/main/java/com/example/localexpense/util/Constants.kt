package com.example.localexpense.util

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
