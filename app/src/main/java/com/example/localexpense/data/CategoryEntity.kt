package com.example.localexpense.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,  // Material icon name
    val color: Long,   // Color as ARGB long
    val type: String,  // expense / income
    val isDefault: Boolean = false
)

// 预设分类
object DefaultCategories {
    val expense = listOf(
        CategoryEntity(name = "餐饮", icon = "Restaurant", color = 0xFFE53935, type = "expense", isDefault = true),
        CategoryEntity(name = "购物", icon = "ShoppingBag", color = 0xFFFF9800, type = "expense", isDefault = true),
        CategoryEntity(name = "交通", icon = "DirectionsCar", color = 0xFF2196F3, type = "expense", isDefault = true),
        CategoryEntity(name = "娱乐", icon = "SportsEsports", color = 0xFF9C27B0, type = "expense", isDefault = true),
        CategoryEntity(name = "生活", icon = "Home", color = 0xFF4CAF50, type = "expense", isDefault = true),
        CategoryEntity(name = "医疗", icon = "LocalHospital", color = 0xFFE91E63, type = "expense", isDefault = true),
        CategoryEntity(name = "教育", icon = "School", color = 0xFF00BCD4, type = "expense", isDefault = true),
        CategoryEntity(name = "红包", icon = "CardGiftcard", color = 0xFFFF5722, type = "expense", isDefault = true),
        CategoryEntity(name = "转账", icon = "SwapHoriz", color = 0xFF795548, type = "expense", isDefault = true),
        CategoryEntity(name = "自动扣款", icon = "Autorenew", color = 0xFF673AB7, type = "expense", isDefault = true),
        CategoryEntity(name = "其他", icon = "MoreHoriz", color = 0xFF607D8B, type = "expense", isDefault = true)
    )

    val income = listOf(
        CategoryEntity(name = "工资", icon = "AccountBalance", color = 0xFF4CAF50, type = "income", isDefault = true),
        CategoryEntity(name = "奖金", icon = "CardGiftcard", color = 0xFFFF9800, type = "income", isDefault = true),
        CategoryEntity(name = "红包", icon = "CardGiftcard", color = 0xFFFF5722, type = "income", isDefault = true),
        CategoryEntity(name = "转账", icon = "SwapHoriz", color = 0xFF795548, type = "income", isDefault = true),
        CategoryEntity(name = "退款", icon = "Replay", color = 0xFF009688, type = "income", isDefault = true),
        CategoryEntity(name = "理财收益", icon = "TrendingUp", color = 0xFF8BC34A, type = "income", isDefault = true),
        CategoryEntity(name = "投资", icon = "TrendingUp", color = 0xFF2196F3, type = "income", isDefault = true),
        CategoryEntity(name = "兼职", icon = "Work", color = 0xFF9C27B0, type = "income", isDefault = true),
        CategoryEntity(name = "其他", icon = "MoreHoriz", color = 0xFF607D8B, type = "income", isDefault = true)
    )
}
