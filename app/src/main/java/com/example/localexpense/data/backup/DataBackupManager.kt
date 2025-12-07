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

    companion object {
        // 最大备份文件大小：10MB
        private const val MAX_BACKUP_SIZE = 10 * 1024 * 1024L
    }

    /**
     * 导出数据到 JSON 字符串
     * 注意：会检查导出数据大小，超过限制会抛出异常
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

            // 检查导出数据大小，防止生成过大的备份文件
            val jsonSize = json.toByteArray(Charsets.UTF_8).size.toLong()
            if (jsonSize > MAX_BACKUP_SIZE) {
                throw IllegalStateException(
                    "导出数据过大（${jsonSize / 1024 / 1024}MB），超过最大限制 ${MAX_BACKUP_SIZE / 1024 / 1024}MB。" +
                    "建议清理部分历史数据后重试。"
                )
            }

            Logger.d(tag) { "导出完成: ${expenses.size} 条账单, ${categories.size} 个分类, ${budgets.size} 条预算, 大小: ${jsonSize / 1024}KB" }

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
     *
     * 注意：非合并模式下，只有在数据解析成功后才会清空现有数据，
     * 以避免解析失败导致数据丢失
     */
    suspend fun importFromJson(json: String, merge: Boolean = false): Result<ImportResult> = withContext(Dispatchers.IO) {
        Result.suspendRunCatching {
            Logger.d(tag) { "开始导入数据, 合并模式: $merge" }

            // 1. 先解析数据，确保格式正确（不清空数据）
            val backup = jsonToBackup(json)

            // 验证版本
            if (backup.version > BackupData.BACKUP_VERSION) {
                throw IllegalArgumentException("备份版本过高(${backup.version})，请更新应用后重试")
            }

            // 2. 数据解析成功后，再清空现有数据（非合并模式）
            if (!merge) {
                repository.clearAllData()
            }

            // 3. 导入数据，统计成功和失败数量
            var importedExpenses = 0
            var failedExpenses = 0
            var importedCategories = 0
            var failedCategories = 0
            var importedBudgets = 0
            var failedBudgets = 0

            // 先导入分类（账单依赖分类）
            backup.categories.forEach { category ->
                try {
                    repository.insertCategory(category.copy(id = 0))
                    importedCategories++
                } catch (e: Exception) {
                    failedCategories++
                    Logger.w(tag, "导入分类失败: ${category.name}", e)
                }
            }

            // 导入账单
            backup.expenses.forEach { expense ->
                try {
                    repository.insertExpense(expense.copy(id = 0))
                    importedExpenses++
                } catch (e: Exception) {
                    failedExpenses++
                    Logger.w(tag, "导入账单失败: ${expense.merchant}", e)
                }
            }

            // 导入预算
            backup.budgets.forEach { budget ->
                try {
                    repository.insertBudget(budget.copy(id = 0))
                    importedBudgets++
                } catch (e: Exception) {
                    failedBudgets++
                    Logger.w(tag, "导入预算失败: month=${budget.month}", e)
                }
            }

            Logger.d(tag) { "导入完成: $importedExpenses 条账单, $importedCategories 个分类, $importedBudgets 条预算" }

            ImportResult(
                totalExpenses = backup.expenses.size,
                importedExpenses = importedExpenses,
                failedExpenses = failedExpenses,
                totalCategories = backup.categories.size,
                importedCategories = importedCategories,
                failedCategories = failedCategories,
                totalBudgets = backup.budgets.size,
                importedBudgets = importedBudgets,
                failedBudgets = failedBudgets
            )
        }
    }

    /**
     * 从 Uri（文件）导入数据
     */
    suspend fun importFromUri(uri: Uri, merge: Boolean = false): Result<ImportResult> = withContext(Dispatchers.IO) {
        Result.suspendRunCatching {
            // 检查文件大小，防止内存溢出
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            if (fileSize > MAX_BACKUP_SIZE) {
                throw IllegalArgumentException("备份文件过大（${fileSize / 1024 / 1024}MB），最大支持 10MB")
            }
            if (fileSize == 0L) {
                throw IllegalArgumentException("备份文件为空")
            }

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
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("备份文件格式错误，无法解析 JSON", e)
        }

        // 验证必要字段存在（必须同时包含 expenses 和 categories）
        if (!root.has("expenses") || !root.has("categories")) {
            throw IllegalArgumentException("备份文件格式错误，缺少必要数据（需要包含 expenses 和 categories）")
        }

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
    val failedExpenses: Int = 0,
    val totalCategories: Int,
    val importedCategories: Int,
    val failedCategories: Int = 0,
    val totalBudgets: Int,
    val importedBudgets: Int,
    val failedBudgets: Int = 0
) {
    val hasFailures: Boolean
        get() = failedExpenses > 0 || failedCategories > 0 || failedBudgets > 0

    val summary: String
        get() = buildString {
            append("成功导入 $importedExpenses 条账单、$importedCategories 个分类、$importedBudgets 条预算")
            if (hasFailures) {
                append("\n")
                val failures = mutableListOf<String>()
                if (failedExpenses > 0) failures.add("$failedExpenses 条账单")
                if (failedCategories > 0) failures.add("$failedCategories 个分类")
                if (failedBudgets > 0) failures.add("$failedBudgets 条预算")
                append("(${failures.joinToString("、")}导入失败)")
            }
        }
}
