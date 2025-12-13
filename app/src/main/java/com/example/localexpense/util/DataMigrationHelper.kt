package com.example.localexpense.util

import android.content.Context
import android.content.SharedPreferences
import com.example.localexpense.BuildConfig
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.di.RepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 数据迁移助手
 *
 * 功能：
 * 1. 应用版本升级时的数据迁移
 * 2. 旧数据格式兼容处理
 * 3. 清理过期数据
 * 4. 迁移状态追踪
 *
 * 使用方式：
 * 在 Application.onCreate 中调用 DataMigrationHelper.checkAndMigrate(context)
 */
object DataMigrationHelper {

    private const val TAG = "DataMigration"
    private const val PREFS_NAME = "migration_prefs"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_MIGRATION_COMPLETED = "migration_completed_v"

    /**
     * 迁移结果
     */
    sealed class MigrationResult {
        object NoMigrationNeeded : MigrationResult()
        object FreshInstall : MigrationResult()
        data class Success(val fromVersion: Int, val toVersion: Int) : MigrationResult()
        data class Failed(val error: String) : MigrationResult()
    }

    /**
     * 检查并执行数据迁移
     *
     * @param context 应用上下文
     * @return 迁移结果
     */
    suspend fun checkAndMigrate(context: Context): MigrationResult {
        val prefs = getPrefs(context)
        val currentVersion = BuildConfig.VERSION_CODE
        val lastVersion = prefs.getInt(KEY_LAST_VERSION_CODE, -1)

        return when {
            // 全新安装
            lastVersion == -1 -> {
                Logger.i(TAG, "全新安装，版本 $currentVersion")
                saveCurrentVersion(prefs, currentVersion)
                MigrationResult.FreshInstall
            }

            // 版本相同，无需迁移
            lastVersion == currentVersion -> {
                MigrationResult.NoMigrationNeeded
            }

            // 版本升级，需要迁移
            lastVersion < currentVersion -> {
                Logger.i(TAG, "检测到版本升级: $lastVersion -> $currentVersion")
                try {
                    executeMigrations(context, lastVersion, currentVersion)
                    saveCurrentVersion(prefs, currentVersion)
                    MigrationResult.Success(lastVersion, currentVersion)
                } catch (e: Exception) {
                    Logger.e(TAG, "迁移失败", e)
                    MigrationResult.Failed(e.message ?: "Unknown error")
                }
            }

            // 版本降级（罕见情况）
            else -> {
                Logger.w(TAG, "检测到版本降级: $lastVersion -> $currentVersion")
                saveCurrentVersion(prefs, currentVersion)
                MigrationResult.NoMigrationNeeded
            }
        }
    }

    /**
     * 执行迁移
     */
    private suspend fun executeMigrations(context: Context, fromVersion: Int, toVersion: Int) {
        val startTime = PerformanceMonitor.startTimer("数据迁移")

        // 按版本顺序执行迁移
        if (fromVersion < 2) {
            migrateToV2(context)
        }
        if (fromVersion < 3) {
            migrateToV3(context)
        }
        if (fromVersion < 4) {
            migrateToV4(context)
        }
        // 未来版本迁移可以在这里添加
        // if (fromVersion < 5) { migrateToV5(context) }

        PerformanceMonitor.endTimer("数据迁移", startTime)
        Logger.i(TAG, "迁移完成: $fromVersion -> $toVersion")
    }

    /**
     * 迁移到 V2
     * - 清理旧的缓存数据
     */
    private suspend fun migrateToV2(context: Context) {
        Logger.i(TAG, "执行 V2 迁移")

        // 清理旧的临时文件
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.endsWith(".log")) {
                file.delete()
            }
        }
    }

    /**
     * 迁移到 V3
     * - 迁移到加密存储
     */
    private suspend fun migrateToV3(context: Context) {
        Logger.i(TAG, "执行 V3 迁移")

        // 将旧的 SharedPreferences 数据迁移到加密存储
        val oldPrefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
        val rulesVersion = oldPrefs.getString("rules_version", null)

        if (rulesVersion != null) {
            SecurePreferences.putString(context, SecurePreferences.Keys.RULES_VERSION, rulesVersion)
            // 清理旧数据
            oldPrefs.edit().clear().apply()
        }
    }

    /**
     * 迁移到 V4
     * - 数据库索引已通过 Room Migration 处理
     * - 这里可以做一些额外的清理工作
     */
    private suspend fun migrateToV4(context: Context) {
        Logger.i(TAG, "执行 V4 迁移")

        // 清理超过 1 年的旧数据（可选）
        // 取消注释以启用自动清理
        // cleanOldData(context, daysToKeep = 365)
    }

    /**
     * 清理旧数据
     *
     * @param context 上下文
     * @param daysToKeep 保留最近多少天的数据
     * @return 删除的记录数
     */
    suspend fun cleanOldData(context: Context, daysToKeep: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep.toLong() * 24 * 60 * 60 * 1000)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoryEntryPoint::class.java
        )
        val repository = entryPoint.transactionRepository()

        val count = repository.countExpensesBeforeDate(cutoffTime)
        if (count > 0) {
            Logger.i(TAG, "准备清理 $count 条超过 $daysToKeep 天的记录")
            val deleted = repository.deleteExpensesBeforeDate(cutoffTime)
            Logger.i(TAG, "已清理 $deleted 条记录")
            return deleted
        }

        return 0
    }

    /**
     * 获取数据统计信息
     */
    suspend fun getDataStats(context: Context): DataStats {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            RepositoryEntryPoint::class.java
        )
        val repository = entryPoint.transactionRepository()
        val allExpenses = repository.getAllExpensesOnce()

        val totalCount = allExpenses.size
        val expenseCount = allExpenses.count { it.type == "expense" }
        val incomeCount = allExpenses.count { it.type == "income" }

        val oldestRecord = allExpenses.minByOrNull { it.timestamp }?.timestamp
        val newestRecord = allExpenses.maxByOrNull { it.timestamp }?.timestamp

        // 估算数据库大小
        val dbFile = context.getDatabasePath(Constants.DATABASE_NAME)
        val dbSizeKB = if (dbFile.exists()) dbFile.length() / 1024 else 0

        return DataStats(
            totalRecords = totalCount,
            expenseRecords = expenseCount,
            incomeRecords = incomeCount,
            oldestTimestamp = oldestRecord,
            newestTimestamp = newestRecord,
            databaseSizeKB = dbSizeKB
        )
    }

    /**
     * 数据统计信息
     */
    data class DataStats(
        val totalRecords: Int,
        val expenseRecords: Int,
        val incomeRecords: Int,
        val oldestTimestamp: Long?,
        val newestTimestamp: Long?,
        val databaseSizeKB: Long
    ) {
        /**
         * 格式化输出
         */
        override fun toString(): String {
            return buildString {
                appendLine("===== 数据统计 =====")
                appendLine("总记录数: $totalRecords")
                appendLine("  - 支出: $expenseRecords")
                appendLine("  - 收入: $incomeRecords")
                appendLine("数据库大小: ${databaseSizeKB}KB")
                oldestTimestamp?.let {
                    appendLine("最早记录: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))}")
                }
                newestTimestamp?.let {
                    appendLine("最新记录: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))}")
                }
            }
        }
    }

    /**
     * 检查是否需要数据清理
     *
     * @param context 上下文
     * @param thresholdRecords 记录数阈值
     * @param thresholdSizeKB 数据库大小阈值（KB）
     */
    suspend fun needsCleanup(
        context: Context,
        thresholdRecords: Int = 10000,
        thresholdSizeKB: Long = 50 * 1024  // 50MB
    ): Boolean {
        val stats = getDataStats(context)
        return stats.totalRecords > thresholdRecords || stats.databaseSizeKB > thresholdSizeKB
    }

    /**
     * 重置迁移状态（用于测试）
     */
    fun resetMigrationState(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveCurrentVersion(prefs: SharedPreferences, version: Int) {
        prefs.edit()
            .putInt(KEY_LAST_VERSION_CODE, version)
            .putBoolean("$KEY_MIGRATION_COMPLETED$version", true)
            .apply()
    }
}
