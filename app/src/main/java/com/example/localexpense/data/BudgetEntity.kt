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
    companion object {
        // 合理的年份范围：2000年到2100年
        private const val MIN_MONTH = 200001  // 2000年01月
        private const val MAX_MONTH = 210012  // 2100年12月

        /**
         * 安全创建 BudgetEntity，带验证
         * @throws IllegalArgumentException 如果参数无效
         */
        fun create(
            id: Long = 0,
            categoryId: Long? = null,
            amount: Double,
            month: Int,
            notifyThreshold: Int = 80
        ): BudgetEntity {
            require(month in MIN_MONTH..MAX_MONTH) { "月份格式无效: $month, 应为 YYYYMM 格式 (${MIN_MONTH}-${MAX_MONTH})" }
            require(month % 100 in 1..12) { "月份值无效: ${month % 100}, 应为 1-12" }
            require(amount >= 0) { "预算金额不能为负数: $amount" }
            require(notifyThreshold in 0..100) { "通知阈值应在 0-100 之间: $notifyThreshold" }
            return BudgetEntity(id, categoryId, amount, month, notifyThreshold)
        }
    }

    /** 检查数据是否有效 */
    fun isValid(): Boolean {
        return month in MIN_MONTH..MAX_MONTH &&
                month % 100 in 1..12 &&
                amount >= 0 &&
                notifyThreshold in 0..100
    }
}
