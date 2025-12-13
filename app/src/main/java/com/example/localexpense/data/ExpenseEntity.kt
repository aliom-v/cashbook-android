package com.example.localexpense.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单实体类
 *
 * 索引说明：
 * - idx_timestamp: 按时间筛选优化（首页列表、日期范围查询）
 * - idx_type_timestamp: 按类型+时间筛选优化（支出/收入统计）
 * - idx_category: 按分类筛选优化
 * - idx_merchant: 搜索商户名称优化
 * - idx_stats: 统计查询优化（类型+时间+分类）
 * - idx_channel: 按渠道筛选优化（v1.9.6 新增）
 * - idx_amount_type: 金额+类型复合索引，优化大额交易筛选（v1.9.6 新增）
 *
 * v1.9.6 优化：
 * - 新增 channel 索引，优化按渠道筛选查询
 * - 新增 amount+type 复合索引，优化大额交易筛选
 */
@Entity(
    tableName = "expense",
    indices = [
        Index(value = ["timestamp"], name = "idx_timestamp"),
        Index(value = ["type", "timestamp"], name = "idx_type_timestamp"),
        Index(value = ["category"], name = "idx_category"),
        Index(value = ["merchant"], name = "idx_merchant"),
        Index(value = ["type", "timestamp", "category"], name = "idx_stats"),
        Index(value = ["channel"], name = "idx_channel"),
        Index(value = ["amount", "type"], name = "idx_amount_type")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val type: String,           // expense / income
    val timestamp: Long,
    val channel: String,        // WeChat / Alipay / Manual
    val category: String = "其他",
    val categoryId: Long = 0,
    val note: String = "",      // 备注
    val rawText: String = ""    // 原始文本，用于 debug
)
