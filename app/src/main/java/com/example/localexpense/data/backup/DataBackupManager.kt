package com.example.localexpense.data.backup

import android.content.Context
import android.net.Uri
import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.Result
import com.example.localexpense.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份数据结构
 */
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val expenses: List<ExpenseEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}

/**
 * 数据备份/恢复管理器
 */
class DataBackupManager(
    private val context: Context,
    private val repository: TransactionRepository
) {
    private val tag = "DataBackupManager"

    /**
     * 导出数据到 JSON 字符串
     */
    suspend fun exportToJson(): Result<String> = withContext(Dispatchers.IO) {
        Result.suspendRunCatching {
            Logger.d(tag) { "开始导出数据..." }

            // 收集所有数据（需要一次性获取，而非 Flow）
            val expenses = repository.getAllExpensesOnce()
            val categories = repository.getAllCategoriesOnce()
            val budgets = repository.getAllBudgetsOnce()

            val backup = BackupData(
                expenses = expenses,
                categories = categories,
                budgets = budgets
            )

            val json = backupToJson(backup)
            Logger.d(tag) { "导出完成: ${expenses.size} 条账单, ${categories.size} 个分类, ${budgets.size} 条预算" }

            json
        }
    }

    /**
     * 导出数据到 Uri（文件）
     */
    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val jsonResult = exportToJson()
        when (jsonResult) {
            is Result.Success -> {
                Result.suspendRunCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonResult.data.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法打开输出流")
                }
            }
            is Result.Error -> jsonResult
            is Result.Loading -> Result.Error(IllegalStateException("未预期的状态"))
        }
    }

    /**
     * 从 JSON 字符串导入数据
     */
    suspend fun importFromJson(json: String, merge: Boolean = false): Result<ImportResult> = withContext(Dispatchers.IO) {
        Result.suspendRunCatching {
            Logger.d(tag) { "开始导入数据, 合并模式: $merge" }

            val backup = jsonToBackup(json)

            // 验证版本
            if (backup.version > BackupData.BACKUP_VERSION) {
                throw IllegalArgumentException("备份版本过高(${backup.version})，请更新应用后重试")
            }

            if (!merge) {
                // 清空现有数据
                repository.clearAllData()
            }

            // 导入数据
            var importedExpenses = 0
            var importedCategories = 0
            var importedBudgets = 0

            // 先导入分类（账单依赖分类）
            backup.categories.forEach { category ->
                try {
                    repository.insertCategory(category.copy(id = 0))
                    importedCategories++
                } catch (e: Exception) {
                    Logger.w(tag, "导入分类失败: ${category.name}", e)
                }
            }

            // 导入账单
            backup.expenses.forEach { expense ->
                try {
                    repository.insertExpense(expense.copy(id = 0))
                    importedExpenses++
                } catch (e: Exception) {
                    Logger.w(tag, "导入账单失败: ${expense.merchant}", e)
                }
            }

            // 导入预算
            backup.budgets.forEach { budget ->
                try {
                    repository.insertBudget(budget.copy(id = 0))
                    importedBudgets++
                } catch (e: Exception) {
                    Logger.w(tag, "导入预算失败: month=${budget.month}", e)
                }
            }

            Logger.d(tag) { "导入完成: $importedExpenses 条账单, $importedCategories 个分类, $importedBudgets 条预算" }

            ImportResult(
                totalExpenses = backup.expenses.size,
                importedExpenses = importedExpenses,
                totalCategories = backup.categories.size,
                importedCategories = importedCategories,
                totalBudgets = backup.budgets.size,
                importedBudgets = importedBudgets
            )
        }
    }

    /**
     * 从 Uri（文件）导入数据
     */
    suspend fun importFromUri(uri: Uri, merge: Boolean = false): Result<ImportResult> = withContext(Dispatchers.IO) {
        Result.suspendRunCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: throw IllegalStateException("无法读取文件")

            when (val result = importFromJson(json, merge)) {
                is Result.Success -> result.data
                is Result.Error -> throw result.exception
                is Result.Loading -> throw IllegalStateException("未预期的状态")
            }
        }
    }

    /**
     * 生成备份文件名
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "localexpense_backup_${dateFormat.format(Date())}.json"
    }

    // ========== JSON 序列化 ==========

    private fun backupToJson(backup: BackupData): String {
        val root = JSONObject().apply {
            put("version", backup.version)
            put("timestamp", backup.timestamp)
            put("expenses", expensesToJsonArray(backup.expenses))
            put("categories", categoriesToJsonArray(backup.categories))
            put("budgets", budgetsToJsonArray(backup.budgets))
        }
        return root.toString(2)
    }

    private fun jsonToBackup(json: String): BackupData {
        val root = JSONObject(json)
        return BackupData(
            version = root.optInt("version", 1),
            timestamp = root.optLong("timestamp", 0),
            expenses = jsonArrayToExpenses(root.optJSONArray("expenses")),
            categories = jsonArrayToCategories(root.optJSONArray("categories")),
            budgets = jsonArrayToBudgets(root.optJSONArray("budgets"))
        )
    }

    private fun expensesToJsonArray(expenses: List<ExpenseEntity>): JSONArray {
        return JSONArray().apply {
            expenses.forEach { expense ->
                put(JSONObject().apply {
                    put("amount", expense.amount)
                    put("merchant", expense.merchant)
                    put("type", expense.type)
                    put("timestamp", expense.timestamp)
                    put("channel", expense.channel)
                    put("category", expense.category)
                    put("categoryId", expense.categoryId)
                    put("note", expense.note)
                })
            }
        }
    }

    private fun jsonArrayToExpenses(array: JSONArray?): List<ExpenseEntity> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ExpenseEntity(
                amount = obj.getDouble("amount"),
                merchant = obj.getString("merchant"),
                type = obj.getString("type"),
                timestamp = obj.getLong("timestamp"),
                channel = obj.optString("channel", "手动"),
                category = obj.optString("category", "其他"),
                categoryId = obj.optLong("categoryId", 0),
                note = obj.optString("note", "")
            )
        }
    }

    private fun categoriesToJsonArray(categories: List<CategoryEntity>): JSONArray {
        return JSONArray().apply {
            categories.forEach { category ->
                put(JSONObject().apply {
                    put("name", category.name)
                    put("icon", category.icon)
                    put("color", category.color)
                    put("type", category.type)
                    put("isDefault", category.isDefault)
                })
            }
        }
    }

    private fun jsonArrayToCategories(array: JSONArray?): List<CategoryEntity> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CategoryEntity(
                name = obj.getString("name"),
                icon = obj.getString("icon"),
                color = obj.getLong("color"),
                type = obj.getString("type"),
                isDefault = obj.optBoolean("isDefault", false)
            )
        }
    }

    private fun budgetsToJsonArray(budgets: List<BudgetEntity>): JSONArray {
        return JSONArray().apply {
            budgets.forEach { budget ->
                put(JSONObject().apply {
                    put("amount", budget.amount)
                    put("month", budget.month)
                })
            }
        }
    }

    private fun jsonArrayToBudgets(array: JSONArray?): List<BudgetEntity> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BudgetEntity(
                amount = obj.getDouble("amount"),
                month = obj.getInt("month")
            )
        }
    }
}

/**
 * 导入结果
 */
data class ImportResult(
    val totalExpenses: Int,
    val importedExpenses: Int,
    val totalCategories: Int,
    val importedCategories: Int,
    val totalBudgets: Int,
    val importedBudgets: Int
) {
    val summary: String
        get() = "成功导入 $importedExpenses 条账单、$importedCategories 个分类、$importedBudgets 条预算"
}
