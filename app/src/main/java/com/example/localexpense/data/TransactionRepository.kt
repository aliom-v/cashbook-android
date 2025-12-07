package com.example.localexpense.data

import android.content.Context
import com.example.localexpense.util.CryptoUtils
import com.example.localexpense.util.Logger
import com.example.localexpense.util.RetryUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "TransactionRepo"

        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun getInstance(context: Context): TransactionRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransactionRepository(context).also { INSTANCE = it }
            }
    }

    // 使用 applicationContext 防止 Activity Context 泄漏
    private val appContext: Context = context.applicationContext
    private val db: AppDatabase = AppDatabase.getInstance(appContext)
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
                Logger.e(TAG, "初始化默认分类失败", e)
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
                    Logger.i(TAG, "默认分类初始化完成")
                }
                isInitialized = true
            } catch (e: Exception) {
                Logger.e(TAG, "initDefaultCategoriesInternal 失败", e)
            }
        }
    }

    /**
     * 等待 Repository 初始化完成
     * 用于确保在插入数据前，默认分类已初始化
     * 使用双重检查锁定模式确保线程安全
     */
    suspend fun waitForInitialization() {
        // 快速路径：已初始化则直接返回（volatile 读）
        if (isInitialized) return
        // 进入 Mutex 保护区域进行第二次检查
        initMutex.withLock {
            // 双重检查：在获取锁后再次检查
            if (isInitialized) return
            // 执行初始化
            try {
                val count = categoryDao.count()
                if (count == 0) {
                    categoryDao.insertAll(DefaultCategories.expense + DefaultCategories.income)
                    Logger.i(TAG, "默认分类初始化完成")
                }
                isInitialized = true
            } catch (e: Exception) {
                Logger.e(TAG, "waitForInitialization 失败", e)
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
                // 加密 rawText 后插入
                expenseDao.insert(encryptEntity(entity))
            } catch (e: Exception) {
                Logger.e(TAG, "插入失败", e)
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
     * 会等待初始化完成后再插入，支持自动重试
     * @return 插入成功返回 true，失败返回 false
     */
    suspend fun insertTransactionSync(entity: ExpenseEntity): Boolean {
        return try {
            waitForInitialization()
            // 使用重试机制处理临时性数据库错误
            RetryUtils.withRetry(
                maxRetries = 2,
                shouldRetry = RetryUtils::isRetryableDbException
            ) {
                expenseDao.insert(encryptEntity(entity))
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "同步插入失败", e)
            false
        }
    }

    suspend fun insertExpense(entity: ExpenseEntity): Long =
        RetryUtils.withRetry(shouldRetry = RetryUtils::isRetryableDbException) {
            expenseDao.insert(encryptEntity(entity))
        }

    suspend fun updateExpense(entity: ExpenseEntity) =
        RetryUtils.withRetry(shouldRetry = RetryUtils::isRetryableDbException) {
            expenseDao.update(encryptEntity(entity))
        }

    suspend fun deleteExpense(entity: ExpenseEntity) = expenseDao.delete(entity)

    fun getAllFlow(): Flow<List<ExpenseEntity>> = expenseDao.getAll().map { list ->
        list.map { decryptEntity(it) }
    }

    fun getByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getByDateRange(start, end).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 搜索交易记录，自动转义 SQL LIKE 特殊字符
     */
    fun search(query: String): Flow<List<ExpenseEntity>> {
        val escapedQuery = escapeSqlLike(query)
        return expenseDao.search(escapedQuery).map { list ->
            list.map { decryptEntity(it) }
        }
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

    fun getByDate(date: String): Flow<List<ExpenseEntity>> =
        expenseDao.getByDate(date).map { list ->
            list.map { decryptEntity(it) }
        }

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

    suspend fun getAllExpensesOnce(): List<ExpenseEntity> =
        expenseDao.getAllOnce().map { decryptEntity(it) }

    suspend fun getAllCategoriesOnce(): List<CategoryEntity> = categoryDao.getAllOnce()

    suspend fun getAllBudgetsOnce(): List<BudgetEntity> = budgetDao.getAllOnce()

    suspend fun clearAllData() {
        expenseDao.deleteAll()
        categoryDao.deleteAll()
        budgetDao.deleteAll()
    }

    /**
     * 仅删除所有账单记录（保留分类和预算）
     */
    suspend fun deleteAllExpenses() {
        expenseDao.deleteAll()
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

    // ========== 加密/解密辅助方法 ==========

    /**
     * 加密实体的 rawText 字段
     * 其他字段保持不变
     */
    private fun encryptEntity(entity: ExpenseEntity): ExpenseEntity {
        if (entity.rawText.isEmpty()) return entity
        return entity.copy(rawText = CryptoUtils.encrypt(entity.rawText))
    }

    /**
     * 解密实体的 rawText 字段
     * 兼容历史未加密数据（无 ENC: 前缀的数据会原样返回）
     */
    private fun decryptEntity(entity: ExpenseEntity): ExpenseEntity {
        if (entity.rawText.isEmpty()) return entity
        return entity.copy(rawText = CryptoUtils.decrypt(entity.rawText))
    }
}
