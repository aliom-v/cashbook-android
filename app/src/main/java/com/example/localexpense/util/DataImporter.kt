package com.example.localexpense.util

import android.content.Context
import android.net.Uri
import com.example.localexpense.data.ExpenseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 数据导入工具类
 *
 * 功能：
 * 1. 从 CSV 文件导入
 * 2. 从 JSON 文件导入
 * 3. 数据验证和清洗
 * 4. 重复检测
 *
 * 使用场景：
 * - 从备份恢复数据
 * - 从其他应用迁移数据
 * - 批量导入历史记录
 */
object DataImporter {

    private const val TAG = "DataImporter"

    // 导入结果
    sealed class ImportResult {
        data class Success(
            val importedCount: Int,
            val skippedCount: Int,
            val failedCount: Int,
            val transactions: List<ExpenseEntity>
        ) : ImportResult()

        data class Failure(val error: String, val exception: Exception? = null) : ImportResult()
    }

    // 导入选项
    data class ImportOptions(
        val skipDuplicates: Boolean = true,
        val dateFormats: List<String> = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd"
        ),
        val defaultCategory: String = "其他",
        val defaultChannel: String = "导入"
    )

    /**
     * 从 URI 导入数据
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        options: ImportOptions = ImportOptions()
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Failure("无法打开文件")

            val content = BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }

            // 根据内容判断格式
            val trimmedContent = content.trim()
            when {
                trimmedContent.startsWith("{") || trimmedContent.startsWith("[") -> {
                    importFromJson(content, options)
                }
                else -> {
                    importFromCsv(content, options)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "导入失败", e)
            ImportResult.Failure("导入失败: ${e.message}", e)
        }
    }

    /**
     * 从 CSV 内容导入
     */
    fun importFromCsv(
        content: String,
        options: ImportOptions = ImportOptions()
    ): ImportResult {
        try {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return ImportResult.Failure("文件为空")
            }

            // 解析表头
            val headerLine = lines.first()
            val headers = parseCsvLine(headerLine).map { it.lowercase().trim() }

            // 查找列索引
            val amountIndex = headers.indexOfFirst { it in listOf("金额", "amount", "money") }
            val typeIndex = headers.indexOfFirst { it in listOf("类型", "type") }
            val merchantIndex = headers.indexOfFirst { it in listOf("商户", "merchant", "payee", "description") }
            val categoryIndex = headers.indexOfFirst { it in listOf("分类", "category") }
            val channelIndex = headers.indexOfFirst { it in listOf("渠道", "channel", "source") }
            val dateIndex = headers.indexOfFirst { it in listOf("时间", "日期", "date", "time", "datetime") }
            val noteIndex = headers.indexOfFirst { it in listOf("备注", "note", "memo", "remark") }

            if (amountIndex == -1) {
                return ImportResult.Failure("CSV 文件缺少金额列")
            }

            val transactions = mutableListOf<ExpenseEntity>()
            var skippedCount = 0
            var failedCount = 0

            // 解析数据行
            for (i in 1 until lines.size) {
                try {
                    val values = parseCsvLine(lines[i])
                    if (values.isEmpty()) continue

                    val amount = parseAmount(values.getOrNull(amountIndex) ?: "")
                    if (amount == null || amount <= 0) {
                        failedCount++
                        continue
                    }

                    val typeText = values.getOrNull(typeIndex) ?: ""
                    val type = when {
                        typeText.contains("收入") || typeText.equals("income", ignoreCase = true) -> "income"
                        else -> "expense"
                    }

                    val merchant = values.getOrNull(merchantIndex)?.trim()?.take(50) ?: "未知商户"
                    val category = values.getOrNull(categoryIndex)?.trim() ?: options.defaultCategory
                    val channel = values.getOrNull(channelIndex)?.trim() ?: options.defaultChannel
                    val dateStr = values.getOrNull(dateIndex) ?: ""
                    val timestamp = parseDate(dateStr, options.dateFormats) ?: System.currentTimeMillis()
                    val note = values.getOrNull(noteIndex)?.trim() ?: ""

                    val transaction = ExpenseEntity(
                        amount = amount,
                        merchant = merchant,
                        type = type,
                        timestamp = timestamp,
                        channel = channel,
                        category = category,
                        note = note
                    )

                    transactions.add(transaction)
                } catch (e: Exception) {
                    Logger.w(TAG, "解析行 $i 失败: ${e.message}")
                    failedCount++
                }
            }

            Logger.i(TAG, "CSV 导入完成: ${transactions.size} 条成功, $skippedCount 条跳过, $failedCount 条失败")

            return ImportResult.Success(
                importedCount = transactions.size,
                skippedCount = skippedCount,
                failedCount = failedCount,
                transactions = transactions
            )

        } catch (e: Exception) {
            Logger.e(TAG, "CSV 解析失败", e)
            return ImportResult.Failure("CSV 解析失败: ${e.message}", e)
        }
    }

    /**
     * 从 JSON 内容导入
     */
    fun importFromJson(
        content: String,
        options: ImportOptions = ImportOptions()
    ): ImportResult {
        try {
            val trimmedContent = content.trim()
            val jsonArray = when {
                trimmedContent.startsWith("[") -> JSONArray(trimmedContent)
                trimmedContent.startsWith("{") -> {
                    val jsonObject = JSONObject(trimmedContent)
                    jsonObject.optJSONArray("transactions")
                        ?: jsonObject.optJSONArray("data")
                        ?: jsonObject.optJSONArray("records")
                        ?: return ImportResult.Failure("JSON 格式不支持")
                }
                else -> return ImportResult.Failure("无效的 JSON 格式")
            }

            val transactions = mutableListOf<ExpenseEntity>()
            var skippedCount = 0
            var failedCount = 0

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)

                    // 解析金额，跳过无效记录
                    val amountValue = obj.optDouble("amount", -1.0)
                        .takeIf { it > 0 }
                        ?: parseAmount(obj.optString("amount"))

                    if (amountValue == null) {
                        failedCount++
                        continue
                    }
                    val amount = amountValue

                    val type = when {
                        obj.optString("type").contains("income", ignoreCase = true) -> "income"
                        obj.optString("type").contains("收入") -> "income"
                        else -> "expense"
                    }

                    val merchant = obj.optString("merchant")
                        .takeIf { it.isNotBlank() }
                        ?: obj.optString("payee")
                        ?: obj.optString("description")
                        ?: "未知商户"

                    val category = obj.optString("category")
                        .takeIf { it.isNotBlank() }
                        ?: options.defaultCategory

                    val channel = obj.optString("channel")
                        .takeIf { it.isNotBlank() }
                        ?: obj.optString("source")
                        ?: options.defaultChannel

                    val timestamp = obj.optLong("timestamp", 0)
                        .takeIf { it > 0 }
                        ?: parseDate(obj.optString("date"), options.dateFormats)
                        ?: parseDate(obj.optString("time"), options.dateFormats)
                        ?: System.currentTimeMillis()

                    val note = obj.optString("note")
                        .takeIf { it.isNotBlank() }
                        ?: obj.optString("memo", "")

                    val transaction = ExpenseEntity(
                        amount = amount,
                        merchant = merchant.take(50),
                        type = type,
                        timestamp = timestamp,
                        channel = channel,
                        category = category,
                        note = note
                    )

                    transactions.add(transaction)
                } catch (e: Exception) {
                    Logger.w(TAG, "解析 JSON 项 $i 失败: ${e.message}")
                    failedCount++
                }
            }

            Logger.i(TAG, "JSON 导入完成: ${transactions.size} 条成功, $skippedCount 条跳过, $failedCount 条失败")

            return ImportResult.Success(
                importedCount = transactions.size,
                skippedCount = skippedCount,
                failedCount = failedCount,
                transactions = transactions
            )

        } catch (e: Exception) {
            Logger.e(TAG, "JSON 解析失败", e)
            return ImportResult.Failure("JSON 解析失败: ${e.message}", e)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析 CSV 行（处理引号和逗号）
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())

        return result
    }

    /**
     * 解析金额
     */
    private fun parseAmount(text: String?): Double? {
        if (text.isNullOrBlank()) return null

        return try {
            // 移除货币符号和空格
            val cleaned = text
                .replace("¥", "")
                .replace("￥", "")
                .replace("$", "")
                .replace(",", "")
                .replace(" ", "")
                .replace("+", "")
                .replace("-", "")
                .trim()

            cleaned.toDoubleOrNull()?.takeIf { it > 0 && it < 1_000_000 }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析日期
     */
    private fun parseDate(text: String?, formats: List<String>): Long? {
        if (text.isNullOrBlank()) return null

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(text)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // 尝试下一个格式
            }
        }

        // 尝试解析时间戳
        return text.toLongOrNull()?.takeIf { it > 946684800000 } // 2000-01-01 之后
    }

    /**
     * 验证导入数据
     */
    fun validateImportData(transactions: List<ExpenseEntity>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (transactions.isEmpty()) {
            errors.add("没有可导入的数据")
            return ValidationResult(false, errors, warnings)
        }

        // 检查金额
        val invalidAmounts = transactions.count { it.amount <= 0 || it.amount > 100000 }
        if (invalidAmounts > 0) {
            warnings.add("$invalidAmounts 条记录金额异常")
        }

        // 检查时间
        val now = System.currentTimeMillis()
        val futureRecords = transactions.count { it.timestamp > now + 86400000 } // 1天后
        if (futureRecords > 0) {
            warnings.add("$futureRecords 条记录时间在未来")
        }

        val oldRecords = transactions.count { it.timestamp < 946684800000 } // 2000年之前
        if (oldRecords > 0) {
            warnings.add("$oldRecords 条记录时间异常（2000年之前）")
        }

        // 检查商户
        val emptyMerchants = transactions.count { it.merchant.isBlank() }
        if (emptyMerchants > 0) {
            warnings.add("$emptyMerchants 条记录商户为空")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )
}
