package com.example.localexpense.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense")
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
