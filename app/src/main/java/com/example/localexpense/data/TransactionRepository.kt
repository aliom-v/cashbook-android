package com.example.localexpense.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository private constructor(context: Context) {

    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val expenseDao: ExpenseDao = db.expenseDao()
    private val categoryDao: CategoryDao = db.categoryDao()
    private val budgetDao: BudgetDao = db.budgetDao()

    // 使用单一的协程作用域，避免每次创建新的
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 初始化锁，防止竞态条件
    private val initMutex = Mutex()

    // 标记是否已初始化
    @Volatile
    private var isInitialized = false

    init {
        // 在后台初始化默认分类
        repositoryScope.launch {
            try {
                initDefaultCategoriesInternal()
            } catch (e: Exception) {
                android.util.Log.e("TransactionRepo", "初始化默认分类失败: ${e.message}", e)
            }
        }
    }

    private suspend fun initDefaultCategoriesInternal() {
        // 使用 Mutex 确保只有一个协程执行初始化
        initMutex.withLock {
            if (isInitialized) return
            try {
                val count = categoryDao.count()
                if (count == 0) {
                    categoryDao.insertAll(DefaultCategories.expense + DefaultCategories.income)
                    android.util.Log.i("TransactionRepo", "默认分类初始化完成")
                }
                isInitialized = true
            } catch (e: Exception) {
                android.util.Log.e("TransactionRepo", "initDefaultCategoriesInternal 失败: ${e.message}", e)
            }
        }
    }

    /**
     * 等待 Repository 初始化完成
     * 用于确保在插入数据前，默认分类已初始化
     */
    suspend fun waitForInitialization() {
        initMutex.withLock {
            // 持有锁并检查初始化状态
            if (!isInitialized) {
                // 如果未初始化，直接在锁内执行初始化逻辑
                try {
                    val count = categoryDao.count()
                    if (count == 0) {
                        categoryDao.insertAll(DefaultCategories.expense + DefaultCategories.income)
                        android.util.Log.i("TransactionRepo", "默认分类初始化完成")
                    }
                    isInitialized = true
                } catch (e: Exception) {
                    android.util.Log.e("TransactionRepo", "waitForInitialization 失败: ${e.message}", e)
                }
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized

    // 关闭资源（App退出时调用）
    fun shutdown() {
        repositoryScope.cancel()
    }

    // Expense operations - 异步插入，使用协程
    fun insertTransaction(entity: ExpenseEntity) {
        repositoryScope.launch {
            try {
                // 等待初始化完成
                waitForInitialization()
                expenseDao.insert(entity)
            } catch (e: Exception) {
                android.util.Log.e("TransactionRepo", "插入失败: ${e.message}", e)
            }
        }
    }

    /**
     * 同步方式插入交易（适用于无障碍服务等场景）
     * 会等待初始化完成后再插入
     * @return 插入成功返回 true，失败返回 false
     */
    suspend fun insertTransactionSync(entity: ExpenseEntity): Boolean {
        return try {
            waitForInitialization()
            expenseDao.insert(entity)
            true
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepo", "同步插入失败: ${e.message}", e)
            false
        }
    }

    suspend fun insertExpense(entity: ExpenseEntity): Long = expenseDao.insert(entity)

    suspend fun updateExpense(entity: ExpenseEntity) = expenseDao.update(entity)

    suspend fun deleteExpense(entity: ExpenseEntity) = expenseDao.delete(entity)

    fun getAllFlow() = expenseDao.getAll()

    fun getByDateRange(start: Long, end: Long) = expenseDao.getByDateRange(start, end)

    /**
     * 搜索交易记录，自动转义 SQL LIKE 特殊字符
     */
    fun search(query: String): Flow<List<ExpenseEntity>> {
        val escapedQuery = escapeSqlLike(query)
        return expenseDao.search(escapedQuery)
    }

    /**
     * 转义 SQL LIKE 查询中的特殊字符
     */
    private fun escapeSqlLike(query: String): String {
        return query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    fun getTotalExpense(start: Long, end: Long) = expenseDao.getTotalExpense(start, end)

    fun getTotalIncome(start: Long, end: Long) = expenseDao.getTotalIncome(start, end)

    fun getCategoryStats(type: String, start: Long, end: Long) = expenseDao.getCategoryStats(type, start, end)

    fun getDailyStats(type: String, start: Long, end: Long) = expenseDao.getDailyStats(type, start, end)

    fun getByDate(date: String) = expenseDao.getByDate(date)

    // Category operations
    fun getAllCategories() = categoryDao.getAll()

    fun getCategoriesByType(type: String) = categoryDao.getByType(type)

    suspend fun getCategoryById(id: Long) = categoryDao.getById(id)

    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insert(category)

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun initDefaultCategories() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.expense + DefaultCategories.income)
        }
    }

    // Budget operations
    fun getBudgetsByMonth(month: Int) = budgetDao.getByMonth(month)

    fun getTotalBudget(month: Int) = budgetDao.getTotalBudget(month)

    suspend fun insertBudget(budget: BudgetEntity) = budgetDao.insert(budget)

    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.delete(budget)

    // ========== 数据备份相关 ==========

    suspend fun getAllExpensesOnce(): List<ExpenseEntity> = expenseDao.getAllOnce()

    suspend fun getAllCategoriesOnce(): List<CategoryEntity> = categoryDao.getAllOnce()

    suspend fun getAllBudgetsOnce(): List<BudgetEntity> = budgetDao.getAllOnce()

    suspend fun clearAllData() {
        expenseDao.deleteAll()
        categoryDao.deleteAll()
        budgetDao.deleteAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun getInstance(context: Context): TransactionRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransactionRepository(context).also { INSTANCE = it }
            }
    }
}
