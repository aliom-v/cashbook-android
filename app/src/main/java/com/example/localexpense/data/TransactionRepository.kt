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
        // 快速路径：已初始化则直接返回
        if (isInitialized) return
        // 否则调用内部初始化方法（会获取锁并检查）
        initDefaultCategoriesInternal()
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
    /**
     * 异步插入交易记录
     * @param entity 交易实体
     * @param onError 可选的错误回调，在主线程调用
     */
    fun insertTransaction(entity: ExpenseEntity, onError: ((String) -> Unit)? = null) {
        repositoryScope.launch {
            try {
                // 等待初始化完成
                waitForInitialization()
                expenseDao.insert(entity)
            } catch (e: Exception) {
                android.util.Log.e("TransactionRepo", "插入失败: ${e.message}", e)
                // 在主线程回调错误信息
                onError?.let { callback ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        callback("记账失败: ${e.message}")
                    }
                }
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
     * 包括：\ % _ [ ] （SQLite 支持方括号作为字符类通配符）
     */
    private fun escapeSqlLike(query: String): String {
        return query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
            .replace("[", "\\[")
            .replace("]", "\\]")
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

    /**
     * 初始化默认分类（公开方法，内部调用受保护的初始化逻辑）
     */
    suspend fun initDefaultCategories() {
        initDefaultCategoriesInternal()
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

    /**
     * 删除指定时间之前的账单数据
     * @param beforeTimestamp 时间戳（毫秒）
     * @return 删除的记录数
     */
    suspend fun deleteExpensesBeforeDate(beforeTimestamp: Long): Int {
        return expenseDao.deleteBeforeDate(beforeTimestamp)
    }

    /**
     * 统计指定时间之前的账单数量
     * @param beforeTimestamp 时间戳（毫秒）
     * @return 记录数
     */
    suspend fun countExpensesBeforeDate(beforeTimestamp: Long): Int {
        return expenseDao.countBeforeDate(beforeTimestamp)
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
