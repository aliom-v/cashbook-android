package com.example.localexpense.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.localexpense.util.Constants
import java.util.concurrent.Executors

@Database(
    entities = [ExpenseEntity::class, CategoryEntity::class, BudgetEntity::class],
    version = 3,
    exportSchema = false  // 禁用 schema 导出，避免构建警告
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 数据库迁移：版本 1 -> 2
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "执行数据库迁移 1 -> 2")
                // 确保表结构完整性（示例：添加索引提升查询性能）
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_timestamp ON expense(timestamp)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_type ON expense(type)")
                } catch (e: Exception) {
                    Log.w(TAG, "迁移索引创建失败（可能已存在）: ${e.message}")
                }
            }
        }

        /**
         * 数据库迁移：版本 2 -> 3
         * 添加更多索引以优化查询性能
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "执行数据库迁移 2 -> 3")
                try {
                    // 删除旧索引（如果存在），使用新的命名规范
                    db.execSQL("DROP INDEX IF EXISTS index_expense_timestamp")
                    db.execSQL("DROP INDEX IF EXISTS index_expense_type")

                    // 创建新索引
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON expense(timestamp)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_type_timestamp ON expense(type, timestamp)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_category ON expense(category)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_merchant ON expense(merchant)")
                } catch (e: Exception) {
                    Log.w(TAG, "迁移索引创建失败: ${e.message}")
                }
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                Constants.DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                // 移除 Callback，改用 Repository 初始化默认分类
                // 这样更安全，避免在数据库创建时的线程问题
                .build()
        }
    }
}
