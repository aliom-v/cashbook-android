package com.example.localexpense.util

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.localexpense.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据库优化工具
 *
 * 功能：
 * 1. 数据库 VACUUM（压缩空间）
 * 2. 数据库 ANALYZE（更新统计信息）
 * 3. 完整性检查
 * 4. 数据库大小监控
 * 5. 旧数据清理
 * 6. 索引优化建议
 *
 * 使用场景：
 * - 应用启动时自动维护
 * - 定期后台任务
 * - 用户手动触发优化
 */
object DatabaseOptimizer {

    private const val TAG = "DatabaseOptimizer"

    // 优化阈值
    private const val VACUUM_THRESHOLD_MB = 10L // 数据库超过此大小才执行 VACUUM
    private const val AUTO_CLEANUP_THRESHOLD_RECORDS = 10000 // 超过此数量考虑清理
    private const val FRAGMENTATION_THRESHOLD = 0.2 // 碎片率超过 20% 建议优化

    /**
     * 优化结果
     */
    data class OptimizationResult(
        val success: Boolean,
        val vacuumed: Boolean,
        val analyzed: Boolean,
        val deletedRecords: Int,
        val sizeBefore: Long,
        val sizeAfter: Long,
        val durationMs: Long,
        val message: String
    ) {
        val savedBytes: Long get() = sizeBefore - sizeAfter
        val savedPercent: Float get() = if (sizeBefore > 0) (savedBytes * 100f / sizeBefore) else 0f
    }

    /**
     * 数据库状态
     */
    data class DatabaseStatus(
        val sizeBytes: Long,
        val recordCount: Int,
        val pageCount: Int,
        val freePageCount: Int,
        val pageSize: Int,
        val isIntegrityOk: Boolean,
        val lastOptimizedTime: Long
    ) {
        val sizeMB: Float get() = sizeBytes / (1024f * 1024f)
        val fragmentationRate: Float get() = if (pageCount > 0) freePageCount.toFloat() / pageCount else 0f
        val needsOptimization: Boolean get() = fragmentationRate > FRAGMENTATION_THRESHOLD
    }

    /**
     * 执行完整优化
     *
     * @param context 上下文
     * @param cleanupDaysOld 清理多少天前的数据（0 表示不清理）
     * @param forceVacuum 强制执行 VACUUM（即使数据库较小）
     */
    suspend fun optimize(
        context: Context,
        cleanupDaysOld: Int = 0,
        forceVacuum: Boolean = false
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var deletedRecords = 0
        var vacuumed = false
        var analyzed = false

        try {
            val db = AppDatabase.getInstance(context)
            val sqliteDb = db.openHelper.writableDatabase

            // 获取优化前大小
            val sizeBefore = getDatabaseSize(context)
            Logger.d(TAG) { "开始优化，当前大小: ${sizeBefore / 1024}KB" }

            // 1. 清理旧数据
            if (cleanupDaysOld > 0) {
                deletedRecords = cleanupOldData(db, cleanupDaysOld)
                Logger.d(TAG) { "清理了 $deletedRecords 条旧记录" }
            }

            // 2. 执行 ANALYZE（更新查询优化器统计信息）
            try {
                sqliteDb.execSQL("ANALYZE")
                analyzed = true
                Logger.d(TAG) { "ANALYZE 完成" }
            } catch (e: Exception) {
                Logger.e(TAG, "ANALYZE 失败", e)
            }

            // 3. 执行 VACUUM（压缩数据库）
            val shouldVacuum = forceVacuum || sizeBefore > VACUUM_THRESHOLD_MB * 1024 * 1024
            if (shouldVacuum) {
                try {
                    // VACUUM 不能在事务中执行
                    sqliteDb.execSQL("VACUUM")
                    vacuumed = true
                    Logger.d(TAG) { "VACUUM 完成" }
                } catch (e: Exception) {
                    Logger.e(TAG, "VACUUM 失败", e)
                }
            }

            // 获取优化后大小
            val sizeAfter = getDatabaseSize(context)
            val duration = System.currentTimeMillis() - startTime

            // 记录优化时间
            SecurePreferences.putLong(context, PREF_LAST_OPTIMIZED, System.currentTimeMillis())

            val result = OptimizationResult(
                success = true,
                vacuumed = vacuumed,
                analyzed = analyzed,
                deletedRecords = deletedRecords,
                sizeBefore = sizeBefore,
                sizeAfter = sizeAfter,
                durationMs = duration,
                message = "优化完成"
            )

            Logger.i(TAG, "数据库优化完成: 节省 ${result.savedBytes / 1024}KB (${String.format("%.1f", result.savedPercent)}%), 耗时 ${duration}ms")

            result
        } catch (e: Exception) {
            Logger.e(TAG, "数据库优化失败", e)
            OptimizationResult(
                success = false,
                vacuumed = false,
                analyzed = false,
                deletedRecords = 0,
                sizeBefore = 0,
                sizeAfter = 0,
                durationMs = System.currentTimeMillis() - startTime,
                message = "优化失败: ${e.message}"
            )
        }
    }

    /**
     * 清理旧数据
     */
    private suspend fun cleanupOldData(db: AppDatabase, daysOld: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (daysOld.toLong() * 24 * 60 * 60 * 1000)
        return db.expenseDao().deleteBeforeDate(cutoffTime)
    }

    /**
     * 获取数据库状态
     */
    suspend fun getDatabaseStatus(context: Context): DatabaseStatus = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val sqliteDb = db.openHelper.readableDatabase

            // 获取记录数
            val recordCount = db.expenseDao().getExpenseCount()

            // 获取页面信息
            val pageCount = queryPragmaInt(sqliteDb, "page_count")
            val freePageCount = queryPragmaInt(sqliteDb, "freelist_count")
            val pageSize = queryPragmaInt(sqliteDb, "page_size")

            // 完整性检查
            val integrityCheck = queryPragmaString(sqliteDb, "integrity_check")
            val isIntegrityOk = integrityCheck == "ok"

            // 获取文件大小
            val sizeBytes = getDatabaseSize(context)

            // 获取上次优化时间
            val lastOptimized = SecurePreferences.getLong(context, PREF_LAST_OPTIMIZED, 0)

            DatabaseStatus(
                sizeBytes = sizeBytes,
                recordCount = recordCount,
                pageCount = pageCount,
                freePageCount = freePageCount,
                pageSize = pageSize,
                isIntegrityOk = isIntegrityOk,
                lastOptimizedTime = lastOptimized
            )
        } catch (e: Exception) {
            Logger.e(TAG, "获取数据库状态失败", e)
            DatabaseStatus(0, 0, 0, 0, 0, false, 0)
        }
    }

    /**
     * 执行完整性检查
     */
    suspend fun checkIntegrity(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val sqliteDb = db.openHelper.readableDatabase
            val result = queryPragmaString(sqliteDb, "integrity_check")
            val isOk = result == "ok"

            if (isOk) {
                Logger.i(TAG, "数据库完整性检查通过")
            } else {
                Logger.e(TAG, "数据库完整性检查失败: $result")
            }

            isOk
        } catch (e: Exception) {
            Logger.e(TAG, "完整性检查异常", e)
            false
        }
    }

    /**
     * 获取数据库文件大小
     */
    fun getDatabaseSize(context: Context): Long {
        val dbFile = context.getDatabasePath("local_expense_database")
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")

        var totalSize = 0L
        if (dbFile.exists()) totalSize += dbFile.length()
        if (walFile.exists()) totalSize += walFile.length()
        if (shmFile.exists()) totalSize += shmFile.length()

        return totalSize
    }

    /**
     * 检查是否需要优化
     */
    suspend fun needsOptimization(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val status = getDatabaseStatus(context)

            // 检查碎片率
            if (status.fragmentationRate > FRAGMENTATION_THRESHOLD) {
                Logger.d(TAG) { "碎片率较高: ${String.format("%.1f", status.fragmentationRate * 100)}%" }
                return@withContext true
            }

            // 检查上次优化时间（超过 7 天）
            val daysSinceOptimized = (System.currentTimeMillis() - status.lastOptimizedTime) / (24 * 60 * 60 * 1000)
            if (daysSinceOptimized > 7) {
                Logger.d(TAG) { "超过 7 天未优化" }
                return@withContext true
            }

            false
        } catch (e: Exception) {
            Logger.e(TAG, "检查优化需求失败", e)
            false
        }
    }

    /**
     * 自动优化（如果需要）
     */
    suspend fun autoOptimizeIfNeeded(context: Context): OptimizationResult? {
        return if (needsOptimization(context)) {
            Logger.i(TAG, "执行自动优化")
            optimize(context)
        } else {
            Logger.d(TAG) { "数据库状态良好，跳过优化" }
            null
        }
    }

    /**
     * 获取索引使用情况
     */
    suspend fun getIndexStats(context: Context): List<IndexInfo> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val sqliteDb = db.openHelper.readableDatabase

            val indexes = mutableListOf<IndexInfo>()

            sqliteDb.query("SELECT name, tbl_name FROM sqlite_master WHERE type='index'").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val table = cursor.getString(1)
                    if (name != null && !name.startsWith("sqlite_")) {
                        indexes.add(IndexInfo(name, table))
                    }
                }
            }

            indexes
        } catch (e: Exception) {
            Logger.e(TAG, "获取索引信息失败", e)
            emptyList()
        }
    }

    /**
     * 生成数据库报告
     */
    suspend fun generateReport(context: Context): String = withContext(Dispatchers.IO) {
        val status = getDatabaseStatus(context)
        val indexes = getIndexStats(context)

        buildString {
            appendLine("===== 数据库状态报告 =====")
            appendLine()
            appendLine("【基本信息】")
            appendLine("  文件大小: ${String.format("%.2f", status.sizeMB)} MB")
            appendLine("  记录数量: ${status.recordCount}")
            appendLine("  完整性: ${if (status.isIntegrityOk) "✓ 正常" else "✗ 异常"}")
            appendLine()
            appendLine("【存储信息】")
            appendLine("  页面大小: ${status.pageSize} bytes")
            appendLine("  总页面数: ${status.pageCount}")
            appendLine("  空闲页面: ${status.freePageCount}")
            appendLine("  碎片率: ${String.format("%.1f", status.fragmentationRate * 100)}%")
            appendLine()
            appendLine("【索引信息】")
            if (indexes.isEmpty()) {
                appendLine("  无自定义索引")
            } else {
                indexes.forEach { appendLine("  - ${it.name} (${it.table})") }
            }
            appendLine()
            appendLine("【维护状态】")
            if (status.lastOptimizedTime > 0) {
                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                appendLine("  上次优化: ${formatter.format(status.lastOptimizedTime)}")
            } else {
                appendLine("  上次优化: 从未")
            }
            appendLine("  建议优化: ${if (status.needsOptimization) "是" else "否"}")
            appendLine()
            appendLine("============================")
        }
    }

    // ==================== 辅助方法 ====================

    private fun queryPragmaInt(db: SupportSQLiteDatabase, pragma: String): Int {
        db.query("PRAGMA $pragma").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun queryPragmaString(db: SupportSQLiteDatabase, pragma: String): String {
        db.query("PRAGMA $pragma").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
        }
    }

    // ==================== 常量 ====================

    private const val PREF_LAST_OPTIMIZED = "db_last_optimized"

    // ==================== 数据类 ====================

    data class IndexInfo(
        val name: String,
        val table: String
    )
}
