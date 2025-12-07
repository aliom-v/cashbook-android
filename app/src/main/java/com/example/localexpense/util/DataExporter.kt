package com.example.localexpense.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.localexpense.data.ExpenseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据导出工具类
 *
 * 功能：
 * 1. 导出为 CSV 格式
 * 2. 导出为 Excel 兼容格式 (TSV)
 * 3. 支持自定义导出范围
 * 4. 支持多种存储位置
 *
 * 使用场景：
 * - 用户数据备份
 * - 数据分析导出
 * - 与其他应用数据交换
 */
object DataExporter {

    private const val TAG = "DataExporter"

    // 导出格式
    enum class ExportFormat {
        CSV,    // 逗号分隔，通用格式
        TSV,    // Tab 分隔，Excel 友好
        JSON    // JSON 格式，程序友好
    }

    // 导出结果
    sealed class ExportResult {
        data class Success(
            val uri: Uri?,
            val filePath: String,
            val recordCount: Int,
            val fileSize: Long
        ) : ExportResult()

        data class Failure(val error: String, val exception: Exception? = null) : ExportResult()
    }

    // 导出选项
    data class ExportOptions(
        val format: ExportFormat = ExportFormat.CSV,
        val includeHeader: Boolean = true,
        val dateFormat: String = "yyyy-MM-dd HH:mm:ss",
        val encoding: String = "UTF-8",
        val includeNote: Boolean = true,
        val includeRawText: Boolean = false,  // 原始文本（已加密）
        val customFileName: String? = null
    )

    /**
     * 导出交易记录到文件
     */
    suspend fun exportTransactions(
        context: Context,
        transactions: List<ExpenseEntity>,
        options: ExportOptions = ExportOptions()
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (transactions.isEmpty()) {
                return@withContext ExportResult.Failure("没有可导出的数据")
            }

            // 生成文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val extension = when (options.format) {
                ExportFormat.CSV -> "csv"
                ExportFormat.TSV -> "tsv"
                ExportFormat.JSON -> "json"
            }
            val fileName = options.customFileName ?: "expense_export_$timestamp.$extension"

            // 生成内容
            val content = when (options.format) {
                ExportFormat.CSV -> generateCsvContent(transactions, options)
                ExportFormat.TSV -> generateTsvContent(transactions, options)
                ExportFormat.JSON -> generateJsonContent(transactions, options)
            }

            // 写入文件
            val (uri, filePath, fileSize) = saveToFile(context, fileName, content, options)

            Logger.i(TAG, "导出成功: $filePath, ${transactions.size} 条记录, ${fileSize}B")

            ExportResult.Success(
                uri = uri,
                filePath = filePath,
                recordCount = transactions.size,
                fileSize = fileSize
            )

        } catch (e: Exception) {
            Logger.e(TAG, "导出失败", e)
            ExportResult.Failure("导出失败: ${e.message}", e)
        }
    }

    /**
     * 生成 CSV 内容
     */
    private fun generateCsvContent(
        transactions: List<ExpenseEntity>,
        options: ExportOptions
    ): String {
        val dateFormat = SimpleDateFormat(options.dateFormat, Locale.getDefault())
        val sb = StringBuilder()

        // 添加 BOM (Excel 需要)
        sb.append("\uFEFF")

        // 表头
        if (options.includeHeader) {
            val headers = mutableListOf(
                "ID", "金额", "类型", "商户", "分类", "渠道", "时间"
            )
            if (options.includeNote) headers.add("备注")
            sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
        }

        // 数据行
        transactions.forEach { tx ->
            val typeText = if (tx.type == "income") "收入" else "支出"
            val row = mutableListOf(
                tx.id.toString(),
                String.format("%.2f", tx.amount),
                typeText,
                tx.merchant,
                tx.category,
                tx.channel,
                dateFormat.format(Date(tx.timestamp))
            )
            if (options.includeNote) row.add(tx.note ?: "")

            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }

        return sb.toString()
    }

    /**
     * 生成 TSV 内容 (Excel 友好)
     */
    private fun generateTsvContent(
        transactions: List<ExpenseEntity>,
        options: ExportOptions
    ): String {
        val dateFormat = SimpleDateFormat(options.dateFormat, Locale.getDefault())
        val sb = StringBuilder()

        // 添加 BOM
        sb.append("\uFEFF")

        // 表头
        if (options.includeHeader) {
            val headers = mutableListOf(
                "ID", "金额", "类型", "商户", "分类", "渠道", "时间"
            )
            if (options.includeNote) headers.add("备注")
            sb.appendLine(headers.joinToString("\t"))
        }

        // 数据行
        transactions.forEach { tx ->
            val typeText = if (tx.type == "income") "收入" else "支出"
            val row = mutableListOf(
                tx.id.toString(),
                String.format("%.2f", tx.amount),
                typeText,
                tx.merchant,
                tx.category,
                tx.channel,
                dateFormat.format(Date(tx.timestamp))
            )
            if (options.includeNote) row.add(tx.note ?: "")

            sb.appendLine(row.joinToString("\t") { escapeTsv(it) })
        }

        return sb.toString()
    }

    /**
     * 生成 JSON 内容
     */
    private fun generateJsonContent(
        transactions: List<ExpenseEntity>,
        options: ExportOptions
    ): String {
        val dateFormat = SimpleDateFormat(options.dateFormat, Locale.getDefault())
        val sb = StringBuilder()

        sb.appendLine("{")
        sb.appendLine("  \"exportTime\": \"${dateFormat.format(Date())}\",")
        sb.appendLine("  \"recordCount\": ${transactions.size},")
        sb.appendLine("  \"transactions\": [")

        transactions.forEachIndexed { index, tx ->
            val typeText = if (tx.type == "income") "income" else "expense"
            sb.appendLine("    {")
            sb.appendLine("      \"id\": ${tx.id},")
            sb.appendLine("      \"amount\": ${tx.amount},")
            sb.appendLine("      \"type\": \"$typeText\",")
            sb.appendLine("      \"merchant\": ${escapeJson(tx.merchant)},")
            sb.appendLine("      \"category\": ${escapeJson(tx.category)},")
            sb.appendLine("      \"channel\": ${escapeJson(tx.channel)},")
            sb.appendLine("      \"timestamp\": ${tx.timestamp},")
            sb.appendLine("      \"date\": \"${dateFormat.format(Date(tx.timestamp))}\"")
            if (options.includeNote && !tx.note.isNullOrEmpty()) {
                sb.append(",\n      \"note\": ${escapeJson(tx.note)}")
            }
            sb.appendLine()
            sb.append("    }")
            if (index < transactions.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("  ]")
        sb.appendLine("}")

        return sb.toString()
    }

    /**
     * 保存内容到文件
     */
    private fun saveToFile(
        context: Context,
        fileName: String,
        content: String,
        options: ExportOptions
    ): Triple<Uri?, String, Long> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, fileName, content, options)
        } else {
            saveToExternalStorage(context, fileName, content, options)
        }
    }

    /**
     * Android 10+ 使用 MediaStore
     */
    private fun saveToMediaStore(
        context: Context,
        fileName: String,
        content: String,
        options: ExportOptions
    ): Triple<Uri?, String, Long> {
        val mimeType = when {
            fileName.endsWith(".csv") -> "text/csv"
            fileName.endsWith(".tsv") -> "text/tab-separated-values"
            fileName.endsWith(".json") -> "application/json"
            else -> "text/plain"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LocalExpense")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("无法创建文件")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray(charset(options.encoding)))
        } ?: throw Exception("无法写入文件")

        val fileSize = content.toByteArray(charset(options.encoding)).size.toLong()
        val filePath = "Downloads/LocalExpense/$fileName"

        return Triple(uri, filePath, fileSize)
    }

    /**
     * Android 9 及以下使用外部存储
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(
        context: Context,
        fileName: String,
        content: String,
        options: ExportOptions
    ): Triple<Uri?, String, Long> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadDir, "LocalExpense")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val file = File(exportDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray(charset(options.encoding)))
        }

        return Triple(Uri.fromFile(file), file.absolutePath, file.length())
    }

    /**
     * 导出到指定输出流 (用于分享)
     */
    suspend fun exportToStream(
        outputStream: OutputStream,
        transactions: List<ExpenseEntity>,
        options: ExportOptions = ExportOptions()
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val content = when (options.format) {
                ExportFormat.CSV -> generateCsvContent(transactions, options)
                ExportFormat.TSV -> generateTsvContent(transactions, options)
                ExportFormat.JSON -> generateJsonContent(transactions, options)
            }

            outputStream.write(content.toByteArray(charset(options.encoding)))
            outputStream.flush()

            ExportResult.Success(
                uri = null,
                filePath = "stream",
                recordCount = transactions.size,
                fileSize = content.toByteArray().size.toLong()
            )
        } catch (e: Exception) {
            Logger.e(TAG, "导出到流失败", e)
            ExportResult.Failure("导出失败: ${e.message}", e)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * CSV 转义
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * TSV 转义
     */
    private fun escapeTsv(value: String): String {
        return value.replace("\t", " ").replace("\n", " ")
    }

    /**
     * JSON 转义
     */
    private fun escapeJson(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // ==================== 统计导出 ====================

    /**
     * 导出月度统计报表
     */
    suspend fun exportMonthlyReport(
        context: Context,
        transactions: List<ExpenseEntity>,
        year: Int,
        month: Int
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sb = StringBuilder()
            sb.append("\uFEFF") // BOM

            // 标题
            sb.appendLine("${year}年${month}月 收支报表")
            sb.appendLine()

            // 汇总
            val expenses = transactions.filter { it.type == "expense" }
            val incomes = transactions.filter { it.type == "income" }
            val totalExpense = expenses.sumOf { it.amount }
            val totalIncome = incomes.sumOf { it.amount }

            sb.appendLine("【汇总】")
            sb.appendLine("总支出: ¥${String.format("%.2f", totalExpense)}")
            sb.appendLine("总收入: ¥${String.format("%.2f", totalIncome)}")
            sb.appendLine("净收入: ¥${String.format("%.2f", totalIncome - totalExpense)}")
            sb.appendLine("交易笔数: ${transactions.size}")
            sb.appendLine()

            // 按分类统计支出
            sb.appendLine("【支出分类统计】")
            expenses.groupBy { it.category }
                .mapValues { it.value.sumOf { tx -> tx.amount } }
                .toList()
                .sortedByDescending { it.second }
                .forEach { (category, amount) ->
                    val percent = if (totalExpense > 0) amount / totalExpense * 100 else 0.0
                    sb.appendLine("$category: ¥${String.format("%.2f", amount)} (${String.format("%.1f", percent)}%)")
                }
            sb.appendLine()

            // 按分类统计收入
            if (incomes.isNotEmpty()) {
                sb.appendLine("【收入分类统计】")
                incomes.groupBy { it.category }
                    .mapValues { it.value.sumOf { tx -> tx.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .forEach { (category, amount) ->
                        val percent = if (totalIncome > 0) amount / totalIncome * 100 else 0.0
                        sb.appendLine("$category: ¥${String.format("%.2f", amount)} (${String.format("%.1f", percent)}%)")
                    }
                sb.appendLine()
            }

            // 明细列表
            sb.appendLine("【交易明细】")
            sb.appendLine("日期\t类型\t金额\t商户\t分类")
            transactions.sortedByDescending { it.timestamp }.forEach { tx ->
                val typeText = if (tx.type == "income") "收入" else "支出"
                val sign = if (tx.type == "income") "+" else "-"
                sb.appendLine("${dateFormat.format(Date(tx.timestamp))}\t$typeText\t$sign¥${String.format("%.2f", tx.amount)}\t${tx.merchant}\t${tx.category}")
            }

            // 保存文件
            val fileName = "expense_report_${year}${String.format("%02d", month)}.txt"
            val (uri, filePath, fileSize) = saveToFile(context, fileName, sb.toString(), ExportOptions())

            ExportResult.Success(
                uri = uri,
                filePath = filePath,
                recordCount = transactions.size,
                fileSize = fileSize
            )

        } catch (e: Exception) {
            Logger.e(TAG, "导出月度报表失败", e)
            ExportResult.Failure("导出失败: ${e.message}", e)
        }
    }
}
