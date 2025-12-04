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
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 用于数据库初始化的单线程执行器
        private val databaseWriteExecutor = Executors.newSingleThreadExecutor()

        /**
         * 数据库迁移：版本 1 -> 2
         * 示例：添加新字段或表结构变更
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "执行数据库迁移 1 -> 2")
                // 示例：如果需要添加新字段
                // db.execSQL("ALTER TABLE expense ADD COLUMN new_field TEXT DEFAULT ''")

                // 当前版本 2 的迁移逻辑（如果有的话）
                // 如果从版本 1 升级，确保数据完整性
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
            // 添加迁移策略，保护用户数据
            .addMigrations(MIGRATION_1_2)
            // 仅在开发阶段使用，生产环境应移除
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Log.i(TAG, "数据库创建，初始化默认分类")
                    // 直接使用SQL插入默认分类，避免竞态条件
                    databaseWriteExecutor.execute {
                        // 插入支出分类
                        DefaultCategories.expense.forEach { cat ->
                            db.execSQL(
                                "INSERT INTO category (name, icon, color, type, isDefault) VALUES (?, ?, ?, ?, ?)",
                                arrayOf(cat.name, cat.icon, cat.color, cat.type, if (cat.isDefault) 1 else 0)
                            )
                        }
                        // 插入收入分类
                        DefaultCategories.income.forEach { cat ->
                            db.execSQL(
                                "INSERT INTO category (name, icon, color, type, isDefault) VALUES (?, ?, ?, ?, ?)",
                                arrayOf(cat.name, cat.icon, cat.color, cat.type, if (cat.isDefault) 1 else 0)
                            )
                        }
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Log.d(TAG, "数据库已打开，版本: ${db.version}")
                }
            })
            .build()
        }
    }
}
