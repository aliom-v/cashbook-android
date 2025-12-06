package com.example.localexpense.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,  // null means total budget
    val amount: Double,
    val month: Int,  // YYYYMM format, e.g., 202411
    val notifyThreshold: Int = 80  // notify when reaching this percentage
) {
    init {
        // 验证月份格式 YYYYMM
        require(month in 100001..999912) { "月份格式无效: $month, 应为 YYYYMM 格式" }
        require(month % 100 in 1..12) { "月份值无效: ${month % 100}, 应为 1-12" }
        require(amount >= 0) { "预算金额不能为负数: $amount" }
        require(notifyThreshold in 0..100) { "通知阈值应在 0-100 之间: $notifyThreshold" }
    }
}
