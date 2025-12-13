package com.example.localexpense.domain.repository

import androidx.paging.PagingData
import com.example.localexpense.data.BatchDeleteResult
import com.example.localexpense.data.BatchInsertResult
import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.data.CategoryStat
import com.example.localexpense.data.DailyStat
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.data.ExpenseIncomeStat
import kotlinx.coroutines.flow.Flow

/**
 * 交易数据仓库接口
 * 定义所有交易相关的数据操作
 */
interface ITransactionRepository {

    // ==================== 初始化 ====================

    /**
     * 等待初始化完成
     */
    suspend fun waitForInitialization()

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean

    /**
     * 关闭资源
     */
    fun shutdown()

    // ==================== 交易记录操作 ====================

    /**
     * 异步插入交易记录
     */
    fun insertTransaction(entity: ExpenseEntity, onError: ((String) -> Unit)? = null)

    /**
     * 异步插入交易记录（带完整回调）
     */
    fun insertTransactionWithCallback(
        entity: ExpenseEntity,
        onSuccess: ((Long) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )

    /**
     * 同步插入交易记录
     */
    suspend fun insertTransactionSync(entity: ExpenseEntity): Boolean

    /**
     * 插入交易记录
     */
    suspend fun insertExpense(entity: ExpenseEntity): Long

    /**
     * 更新交易记录
     */
    suspend fun updateExpense(entity: ExpenseEntity)

    /**
     * 删除交易记录
     */
    suspend fun deleteExpense(entity: ExpenseEntity)

    /**
     * 获取所有交易记录 Flow
     */
    fun getAllFlow(): Flow<List<ExpenseEntity>>

    /**
     * 按日期范围获取交易记录
     */
    fun getByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>>

    /**
     * 搜索交易记录
     */
    fun search(query: String): Flow<List<ExpenseEntity>>

    /**
     * 获取指定日期范围内的支出总额
     */
    fun getTotalExpense(start: Long, end: Long): Flow<Double?>

    /**
     * 获取指定日期范围内的收入总额
     */
    fun getTotalIncome(start: Long, end: Long): Flow<Double?>

    /**
     * 获取收支总额
     */
    fun getTotalExpenseAndIncome(start: Long, end: Long): Flow<ExpenseIncomeStat>

    /**
     * 获取分类统计
     */
    fun getCategoryStats(type: String, start: Long, end: Long): Flow<List<CategoryStat>>

    /**
     * 获取每日统计
     */
    fun getDailyStats(type: String, start: Long, end: Long): Flow<List<DailyStat>>

    /**
     * 按日期字符串获取交易记录
     */
    fun getByDate(date: String): Flow<List<ExpenseEntity>>

    /**
     * 分页获取所有记录
     */
    fun getAllPaged(limit: Int, offset: Int): Flow<List<ExpenseEntity>>

    /**
     * 分页搜索
     */
    fun searchPaged(query: String, limit: Int, offset: Int): Flow<List<ExpenseEntity>>

    /**
     * 获取最近 N 条记录
     */
    fun getRecent(limit: Int): Flow<List<ExpenseEntity>>

    /**
     * 获取总记录数
     */
    suspend fun getExpenseCount(): Int

    // ==================== Paging 3 分页 ====================

    /**
     * 获取所有交易记录（Paging 3）
     */
    fun getAllPaging(): Flow<PagingData<ExpenseEntity>>

    /**
     * 搜索交易记录（Paging 3）
     */
    fun searchPaging(query: String): Flow<PagingData<ExpenseEntity>>

    /**
     * 按日期范围查询（Paging 3）
     */
    fun getByDateRangePaging(start: Long, end: Long): Flow<PagingData<ExpenseEntity>>

    // ==================== 分类操作 ====================

    /**
     * 获取所有分类
     */
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /**
     * 按类型获取分类
     */
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>>

    /**
     * 根据 ID 获取分类
     */
    suspend fun getCategoryById(id: Long): CategoryEntity?

    /**
     * 插入分类
     */
    suspend fun insertCategory(category: CategoryEntity): Long

    /**
     * 更新分类
     */
    suspend fun updateCategory(category: CategoryEntity)

    /**
     * 删除分类
     */
    suspend fun deleteCategory(category: CategoryEntity)

    /**
     * 初始化默认分类
     */
    suspend fun initDefaultCategories()

    // ==================== 预算操作 ====================

    /**
     * 按月获取预算
     */
    fun getBudgetsByMonth(month: Int): Flow<List<BudgetEntity>>

    /**
     * 获取指定月份的总预算
     */
    fun getTotalBudget(month: Int): Flow<BudgetEntity?>

    /**
     * 插入预算
     */
    suspend fun insertBudget(budget: BudgetEntity): Long

    /**
     * 删除预算
     */
    suspend fun deleteBudget(budget: BudgetEntity)

    // ==================== 数据备份相关 ====================

    /**
     * 一次性获取所有交易记录
     */
    suspend fun getAllExpensesOnce(): List<ExpenseEntity>

    /**
     * 一次性获取所有分类
     */
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    /**
     * 一次性获取所有预算
     */
    suspend fun getAllBudgetsOnce(): List<BudgetEntity>

    /**
     * 清除所有数据
     */
    suspend fun clearAllData()

    /**
     * 批量插入交易记录
     */
    suspend fun insertExpensesBatch(entities: List<ExpenseEntity>): BatchInsertResult

    /**
     * 批量插入交易记录（尽可能多地插入）
     */
    suspend fun insertExpensesBatchBestEffort(entities: List<ExpenseEntity>): BatchInsertResult

    /**
     * 批量插入分类
     */
    suspend fun insertCategoriesBatch(categories: List<CategoryEntity>): BatchInsertResult

    /**
     * 批量插入预算
     */
    suspend fun insertBudgetsBatch(budgets: List<BudgetEntity>): BatchInsertResult

    /**
     * 删除所有交易记录
     */
    suspend fun deleteAllExpenses()

    /**
     * 批量删除交易记录
     */
    suspend fun deleteExpensesBatch(ids: List<Long>): BatchDeleteResult

    /**
     * 批量删除交易记录（尽可能多地删除）
     */
    suspend fun deleteExpensesBatchBestEffort(ids: List<Long>): BatchDeleteResult

    /**
     * 删除指定时间之前的交易记录
     */
    suspend fun deleteExpensesBeforeDate(beforeTimestamp: Long): Int

    /**
     * 统计指定时间之前的交易记录数量
     */
    suspend fun countExpensesBeforeDate(beforeTimestamp: Long): Int

    // ==================== v1.9.6 新增查询 ====================

    /**
     * 按渠道获取交易记录
     */
    fun getByChannel(channel: String, limit: Int = 200): Flow<List<ExpenseEntity>>

    /**
     * 获取大额支出交易
     */
    fun getLargeExpenses(minAmount: Double, limit: Int = 100): Flow<List<ExpenseEntity>>

    /**
     * 获取分类统计（带数量）
     */
    fun getCategoryStatsWithCount(type: String, start: Long, end: Long, limit: Int = 20): Flow<List<com.example.localexpense.data.CategoryStatWithCount>>

    /**
     * 获取月度趋势统计
     */
    fun getMonthlyTrend(start: Long, end: Long): Flow<List<com.example.localexpense.data.MonthlyTrendStat>>
}
