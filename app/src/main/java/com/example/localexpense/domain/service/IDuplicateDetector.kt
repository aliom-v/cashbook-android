package com.example.localexpense.domain.service

/**
 * 去重检测接口
 * 用于检测交易记录是否重复
 */
interface IDuplicateDetector {

    /**
     * 检查是否应该处理该交易
     * @param amount 金额
     * @param merchant 商户名
     * @param type 交易类型
     * @param packageName 来源应用包名
     * @return 如果应该处理返回 true，如果是重复记录返回 false
     */
    fun shouldProcess(
        amount: Double,
        merchant: String,
        type: String,
        packageName: String? = null
    ): Boolean

    /**
     * 标记交易为已处理
     * @param amount 金额
     * @param merchant 商户名
     * @param type 交易类型
     */
    fun markProcessed(amount: Double, merchant: String, type: String)

    /**
     * 标记交易为已取消（用户取消了确认）
     * @param amount 金额
     * @param merchant 商户名
     * @param type 交易类型
     */
    fun markCancelled(amount: Double, merchant: String, type: String)

    /**
     * 清除所有缓存
     */
    fun clear()

    /**
     * 获取统计信息
     */
    fun getStats(): DuplicateStats
}

/**
 * 去重统计信息
 */
data class DuplicateStats(
    val totalChecks: Long,
    val duplicatesBlocked: Long,
    val cacheSize: Int,
    val hitRate: Double
)
