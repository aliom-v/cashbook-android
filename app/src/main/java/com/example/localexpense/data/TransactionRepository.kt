package com.example.localexpense.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.util.Constants
import com.example.localexpense.util.CryptoUtils
import com.example.localexpense.util.InputValidator
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
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class TransactionRepository @Inject constructor(
    @ApplicationContext context: Context
) : ITransactionRepository {

    companion object {
        private const val TAG = "TransactionRepo"

        // Paging 配置 - 使用 Constants 中定义的值
        private const val PAGE_SIZE = Constants.PAGE_SIZE
        private const val PREFETCH_DISTANCE = Constants.PREFETCH_DISTANCE
        private const val INITIAL_LOAD_SIZE = Constants.PAGE_SIZE * 2  // 首次加载两页
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
        // 统一使用 waitForInitialization() 避免代码重复
        repositoryScope.launch {
            try {
                waitForInitialization()
            } catch (e: Exception) {
                Logger.e(TAG, "初始化默认分类失败", e)
            }
        }
    }

    /**
     * 等待 Repository 初始化完成
     * 用于确保在插入数据前，默认分类已初始化
     * 使用双重检查锁定模式确保线程安全
     *
     * v1.9.3 优化：合并初始化逻辑，移除 initDefaultCategoriesInternal()
     */
    override suspend fun waitForInitialization() {
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
                throw e  // 重新抛出异常，让调用者知道初始化失败
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    override fun isInitialized(): Boolean = isInitialized

    // 关闭资源（App退出时调用）
    override fun shutdown() {
        repositoryScope.cancel()
    }

    // ==================== 交易插入（优化：提取公共逻辑） ====================

    /**
     * 内部方法：执行实际的交易插入操作
     * v1.9.5 优化：提取公共逻辑，减少代码重复
     *
     * @param entity 交易实体
     * @param useRetry 是否使用重试机制
     * @return 插入的记录ID
     */
    private suspend fun insertTransactionInternal(entity: ExpenseEntity, useRetry: Boolean = false): Long {
        waitForInitialization()
        val encryptedEntity = encryptEntity(entity)
        return if (useRetry) {
            RetryUtils.withRetry(
                maxRetries = 2,
                shouldRetry = RetryUtils::isRetryableDbException
            ) {
                expenseDao.insert(encryptedEntity)
            }
        } else {
            expenseDao.insert(encryptedEntity)
        }
    }

    /**
     * 内部方法：在主线程执行回调
     */
    private suspend fun <T> callbackOnMain(callback: ((T) -> Unit)?, value: T) {
        callback?.let {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                it(value)
            }
        }
    }

    /**
     * 异步插入交易记录
     * @param entity 交易实体
     * @param onError 可选的错误回调，在主线程调用
     */
    override fun insertTransaction(entity: ExpenseEntity, onError: ((String) -> Unit)?) {
        repositoryScope.launch {
            try {
                insertTransactionInternal(entity)
            } catch (e: Exception) {
                Logger.e(TAG, "插入失败", e)
                callbackOnMain(onError, "记账失败: ${e.message}")
            }
        }
    }

    /**
     * 异步插入交易记录（带完整回调）
     *
     * @param entity 交易实体
     * @param onSuccess 成功回调，返回插入的记录ID，在主线程调用
     * @param onError 失败回调，返回错误信息，在主线程调用
     */
    override fun insertTransactionWithCallback(
        entity: ExpenseEntity,
        onSuccess: ((Long) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        repositoryScope.launch {
            try {
                val id = insertTransactionInternal(entity)
                callbackOnMain(onSuccess, id)
            } catch (e: Exception) {
                Logger.e(TAG, "插入失败", e)
                callbackOnMain(onError, "记账失败: ${e.message}")
            }
        }
    }

    /**
     * 同步方式插入交易（适用于无障碍服务等场景）
     * 会等待初始化完成后再插入，支持自动重试
     * @return 插入成功返回 true，失败返回 false
     */
    override suspend fun insertTransactionSync(entity: ExpenseEntity): Boolean {
        return try {
            insertTransactionInternal(entity, useRetry = true)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "同步插入失败", e)
            false
        }
    }

    override suspend fun insertExpense(entity: ExpenseEntity): Long {
        waitForInitialization()
        return RetryUtils.withRetry(shouldRetry = RetryUtils::isRetryableDbException) {
            expenseDao.insert(encryptEntity(entity))
        }
    }

    override suspend fun updateExpense(entity: ExpenseEntity) =
        RetryUtils.withRetry(shouldRetry = RetryUtils::isRetryableDbException) {
            expenseDao.update(encryptEntity(entity))
        }

    override suspend fun deleteExpense(entity: ExpenseEntity) = expenseDao.delete(entity)

    override fun getAllFlow(): Flow<List<ExpenseEntity>> = expenseDao.getAll().map { list ->
        list.map { decryptEntity(it) }
    }

    override fun getByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getByDateRange(start, end).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 搜索交易记录，自动清理和转义查询字符串
     * 使用 InputValidator 进行完整的安全处理
     */
    override fun search(query: String): Flow<List<ExpenseEntity>> {
        // 使用 InputValidator 进行完整的安全处理（清理 + SQL LIKE 转义）
        val safeQuery = InputValidator.prepareSearchQuery(query)
        if (safeQuery.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return expenseDao.search(safeQuery).map { list ->
            list.map { decryptEntity(it) }
        }
    }

    // 保留内部方法用于向后兼容（已废弃，请使用 InputValidator.escapeSqlLikePattern）
    @Deprecated("Use InputValidator.escapeSqlLikePattern instead", ReplaceWith("InputValidator.escapeSqlLikePattern(query)"))
    private fun escapeSqlLike(query: String): String {
        return InputValidator.escapeSqlLikePattern(query)
    }

    override fun getTotalExpense(start: Long, end: Long) = expenseDao.getTotalExpense(start, end)

    override fun getTotalIncome(start: Long, end: Long) = expenseDao.getTotalIncome(start, end)

    /**
     * 获取收支总额（单次查询，性能更优）
     */
    override fun getTotalExpenseAndIncome(start: Long, end: Long) = expenseDao.getTotalExpenseAndIncome(start, end)

    override fun getCategoryStats(type: String, start: Long, end: Long) = expenseDao.getCategoryStats(type, start, end)

    override fun getDailyStats(type: String, start: Long, end: Long) = expenseDao.getDailyStats(type, start, end)

    override fun getByDate(date: String): Flow<List<ExpenseEntity>> =
        expenseDao.getByDate(date).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 分页获取所有记录
     * @param limit 每页数量
     * @param offset 偏移量
     */
    override fun getAllPaged(limit: Int, offset: Int): Flow<List<ExpenseEntity>> =
        expenseDao.getAllPaged(limit, offset).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 分页搜索交易记录
     * @param query 搜索关键词
     * @param limit 每页数量
     * @param offset 偏移量
     */
    override fun searchPaged(query: String, limit: Int, offset: Int): Flow<List<ExpenseEntity>> {
        val safeQuery = InputValidator.prepareSearchQuery(query)
        if (safeQuery.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return expenseDao.searchPaged(safeQuery, limit, offset).map { list ->
            list.map { decryptEntity(it) }
        }
    }

    /**
     * 获取最近 N 条记录（用于首页展示）
     * @param limit 记录数量
     */
    override fun getRecent(limit: Int): Flow<List<ExpenseEntity>> =
        expenseDao.getRecent(limit).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 获取总记录数
     */
    override suspend fun getExpenseCount(): Int = expenseDao.getExpenseCount()

    // ==================== Paging 3 分页查询 ====================

    /**
     * 获取所有交易记录（Paging 3 分页）
     * 适用于大数据集，按需加载，避免 OOM
     */
    override fun getAllPaging(): Flow<PagingData<ExpenseEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { expenseDao.getAllPaging() }
        ).flow.map { pagingData ->
            pagingData.map { decryptEntity(it) }
        }
    }

    /**
     * 搜索交易记录（Paging 3 分页）
     * @param query 搜索关键词
     */
    override fun searchPaging(query: String): Flow<PagingData<ExpenseEntity>> {
        val safeQuery = InputValidator.prepareSearchQuery(query)
        if (safeQuery.isEmpty()) {
            return Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                pagingSourceFactory = { EmptyPagingSource<ExpenseEntity>() }
            ).flow
        }
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { expenseDao.searchPaging(safeQuery) }
        ).flow.map { pagingData ->
            pagingData.map { decryptEntity(it) }
        }
    }

    /**
     * 按日期范围查询（Paging 3 分页）
     * @param start 开始时间戳
     * @param end 结束时间戳
     */
    override fun getByDateRangePaging(start: Long, end: Long): Flow<PagingData<ExpenseEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { expenseDao.getByDateRangePaging(start, end) }
        ).flow.map { pagingData ->
            pagingData.map { decryptEntity(it) }
        }
    }

    // Category operations
    override fun getAllCategories() = categoryDao.getAll()

    override fun getCategoriesByType(type: String) = categoryDao.getByType(type)

    override suspend fun getCategoryById(id: Long) = categoryDao.getById(id)

    override suspend fun insertCategory(category: CategoryEntity) = categoryDao.insert(category)

    override suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    override suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    /**
     * 初始化默认分类（公开方法，调用 waitForInitialization）
     */
    override suspend fun initDefaultCategories() {
        waitForInitialization()
    }

    // Budget operations
    override fun getBudgetsByMonth(month: Int) = budgetDao.getByMonth(month)

    override fun getTotalBudget(month: Int) = budgetDao.getTotalBudget(month)

    override suspend fun insertBudget(budget: BudgetEntity): Long = budgetDao.insert(budget)

    override suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.delete(budget)

    // ========== 数据备份相关 ==========

    override suspend fun getAllExpensesOnce(): List<ExpenseEntity> =
        expenseDao.getAllOnce().map { decryptEntity(it) }

    override suspend fun getAllCategoriesOnce(): List<CategoryEntity> = categoryDao.getAllOnce()

    override suspend fun getAllBudgetsOnce(): List<BudgetEntity> = budgetDao.getAllOnce()

    override suspend fun clearAllData() {
        expenseDao.deleteAll()
        categoryDao.deleteAll()
        budgetDao.deleteAll()
    }

    /**
     * 批量插入交易记录（带事务保护）
     * 要么全部成功，要么全部失败，保证数据一致性
     *
     * @param entities 交易实体列表
     * @return BatchInsertResult 包含成功/失败数量
     */
    override suspend fun insertExpensesBatch(entities: List<ExpenseEntity>): BatchInsertResult {
        if (entities.isEmpty()) {
            return BatchInsertResult(0, 0, emptyList())
        }

        waitForInitialization()

        return try {
            db.withTransaction {
                val encryptedEntities = entities.map { encryptEntity(it) }
                encryptedEntities.forEach { expenseDao.insert(it) }
            }
            Logger.i(TAG, "批量插入成功: ${entities.size} 条记录")
            BatchInsertResult(entities.size, 0, emptyList())
        } catch (e: Exception) {
            Logger.e(TAG, "批量插入失败", e)
            // 事务回滚，所有记录都未插入
            BatchInsertResult(0, entities.size, listOf(e.message ?: "未知错误"))
        }
    }

    /**
     * 批量插入交易记录（尽可能多地插入，失败的跳过）
     * 适用于容错场景，会尝试插入所有记录
     *
     * @param entities 交易实体列表
     * @return BatchInsertResult 包含成功/失败数量和错误信息
     */
    override suspend fun insertExpensesBatchBestEffort(entities: List<ExpenseEntity>): BatchInsertResult {
        if (entities.isEmpty()) {
            return BatchInsertResult(0, 0, emptyList())
        }

        waitForInitialization()

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()

        entities.forEach { entity ->
            try {
                expenseDao.insert(encryptEntity(entity))
                successCount++
            } catch (e: Exception) {
                failCount++
                errors.add("${entity.merchant}: ${e.message}")
                Logger.w(TAG, "插入失败: ${entity.merchant}", e)
            }
        }

        Logger.i(TAG, "批量插入完成: 成功=$successCount, 失败=$failCount")
        return BatchInsertResult(successCount, failCount, errors)
    }

    /**
     * 批量插入分类（带事务保护）
     */
    override suspend fun insertCategoriesBatch(categories: List<CategoryEntity>): BatchInsertResult {
        if (categories.isEmpty()) {
            return BatchInsertResult(0, 0, emptyList())
        }

        return try {
            db.withTransaction {
                categories.forEach { categoryDao.insert(it) }
            }
            BatchInsertResult(categories.size, 0, emptyList())
        } catch (e: Exception) {
            Logger.e(TAG, "批量插入分类失败", e)
            BatchInsertResult(0, categories.size, listOf(e.message ?: "未知错误"))
        }
    }

    /**
     * 批量插入预算（带事务保护）
     */
    override suspend fun insertBudgetsBatch(budgets: List<BudgetEntity>): BatchInsertResult {
        if (budgets.isEmpty()) {
            return BatchInsertResult(0, 0, emptyList())
        }

        return try {
            db.withTransaction {
                budgets.forEach { budgetDao.insert(it) }
            }
            BatchInsertResult(budgets.size, 0, emptyList())
        } catch (e: Exception) {
            Logger.e(TAG, "批量插入预算失败", e)
            BatchInsertResult(0, budgets.size, listOf(e.message ?: "未知错误"))
        }
    }

    /**
     * 仅删除所有账单记录（保留分类和预算）
     */
    override suspend fun deleteAllExpenses() {
        expenseDao.deleteAll()
    }

    /**
     * 批量删除交易记录（带事务保护）
     * 要么全部成功，要么全部失败
     *
     * @param ids 要删除的交易ID列表
     * @return BatchDeleteResult 包含成功/失败数量
     */
    override suspend fun deleteExpensesBatch(ids: List<Long>): BatchDeleteResult {
        if (ids.isEmpty()) {
            return BatchDeleteResult(0, 0, emptyList())
        }

        return try {
            db.withTransaction {
                ids.forEach { id ->
                    expenseDao.deleteById(id)
                }
            }
            Logger.i(TAG, "批量删除成功: ${ids.size} 条记录")
            BatchDeleteResult(ids.size, 0, emptyList())
        } catch (e: Exception) {
            Logger.e(TAG, "批量删除失败", e)
            // 事务回滚，所有记录都未删除
            BatchDeleteResult(0, ids.size, listOf(e.message ?: "未知错误"))
        }
    }

    /**
     * 批量删除交易记录（尽可能多地删除，失败的跳过）
     * 适用于容错场景
     *
     * @param ids 要删除的交易ID列表
     * @return BatchDeleteResult 包含成功/失败数量
     */
    override suspend fun deleteExpensesBatchBestEffort(ids: List<Long>): BatchDeleteResult {
        if (ids.isEmpty()) {
            return BatchDeleteResult(0, 0, emptyList())
        }

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()

        ids.forEach { id ->
            try {
                expenseDao.deleteById(id)
                successCount++
            } catch (e: Exception) {
                failCount++
                errors.add("ID $id: ${e.message}")
                Logger.w(TAG, "删除失败: ID=$id", e)
            }
        }

        Logger.i(TAG, "批量删除完成: 成功=$successCount, 失败=$failCount")
        return BatchDeleteResult(successCount, failCount, errors)
    }

    /**
     * 删除指定时间之前的账单数据
     * @param beforeTimestamp 时间戳（毫秒）
     * @return 删除的记录数
     */
    override suspend fun deleteExpensesBeforeDate(beforeTimestamp: Long): Int {
        return expenseDao.deleteBeforeDate(beforeTimestamp)
    }

    /**
     * 统计指定时间之前的账单数量
     * @param beforeTimestamp 时间戳（毫秒）
     * @return 记录数
     */
    override suspend fun countExpensesBeforeDate(beforeTimestamp: Long): Int {
        return expenseDao.countBeforeDate(beforeTimestamp)
    }

    // ==================== v1.9.6 新增查询 ====================

    /**
     * 按渠道获取交易记录
     * 使用 idx_channel 索引优化查询
     * @param channel 渠道名称（如 "微信支付", "支付宝"）
     * @param limit 返回记录数限制
     */
    override fun getByChannel(channel: String, limit: Int): Flow<List<ExpenseEntity>> =
        expenseDao.getByChannel(channel, limit).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 获取大额支出交易
     * 使用 idx_amount_type 索引优化查询
     * @param minAmount 最小金额阈值
     * @param limit 返回记录数限制
     */
    override fun getLargeExpenses(minAmount: Double, limit: Int): Flow<List<ExpenseEntity>> =
        expenseDao.getLargeExpenses(minAmount, limit).map { list ->
            list.map { decryptEntity(it) }
        }

    /**
     * 获取分类统计（带数量）
     * 使用 idx_stats 复合索引优化
     * @param type 类型（expense/income）
     * @param start 开始时间戳
     * @param end 结束时间戳
     * @param limit 返回分类数限制
     */
    override fun getCategoryStatsWithCount(
        type: String,
        start: Long,
        end: Long,
        limit: Int
    ): Flow<List<CategoryStatWithCount>> =
        expenseDao.getCategoryStatsWithCount(type, start, end, limit)

    /**
     * 获取月度趋势统计
     * 用于年度报表展示
     * @param start 开始时间戳
     * @param end 结束时间戳
     */
    override fun getMonthlyTrend(start: Long, end: Long): Flow<List<MonthlyTrendStat>> =
        expenseDao.getMonthlyTrend(start, end)

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

/**
 * 批量插入结果
 */
data class BatchInsertResult(
    val successCount: Int,
    val failCount: Int,
    val errors: List<String>
) {
    val totalCount: Int get() = successCount + failCount
    val hasErrors: Boolean get() = failCount > 0

    fun toSummary(): String = buildString {
        append("成功: $successCount")
        if (failCount > 0) {
            append(", 失败: $failCount")
        }
    }
}

/**
 * 批量删除结果
 */
data class BatchDeleteResult(
    val successCount: Int,
    val failCount: Int,
    val errors: List<String>
) {
    val totalCount: Int get() = successCount + failCount
    val hasErrors: Boolean get() = failCount > 0

    fun toSummary(): String = buildString {
        append("成功删除: $successCount")
        if (failCount > 0) {
            append(", 失败: $failCount")
        }
    }
}
