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
)
