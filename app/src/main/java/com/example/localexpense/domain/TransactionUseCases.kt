package com.example.localexpense.domain

import com.example.localexpense.data.BatchDeleteResult
import com.example.localexpense.data.BatchInsertResult
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.util.CoroutineHelper
import com.example.localexpense.util.Logger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易相关 UseCase
 *
 * 封装交易的增删改查业务逻辑，提高代码复用性和可测试性
 *
 * v1.9.0 新增
 * v1.9.3 重构：添加 Hilt @Inject 注解，使用 ITransactionRepository 接口
 */
@Singleton
class TransactionUseCases @Inject constructor(
    private val repository: ITransactionRepository
) {

    companion object {
        private const val TAG = "TransactionUseCases"
    }

    /**
     * 获取所有交易
     */
    fun getAllTransactions(): Flow<List<ExpenseEntity>> = repository.getAllFlow()

    /**
     * 搜索交易
     */
    fun searchTransactions(query: String): Flow<List<ExpenseEntity>> = repository.search(query)

    /**
     * 添加交易
     */
    suspend fun addTransaction(expense: ExpenseEntity): kotlin.Result<Long> {
        return CoroutineHelper.runSafely {
            if (expense.id == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
                expense.id
            }
        }.also { result ->
            result.fold(
                onSuccess = { id -> Logger.d(TAG) { "交易保存成功: id=$id" } },
                onFailure = { e -> Logger.e(TAG, "交易保存失败", e) }
            )
        }
    }

    /**
     * 删除交易
     */
    suspend fun deleteTransaction(expense: ExpenseEntity): kotlin.Result<Unit> {
        return CoroutineHelper.runSafely {
            repository.deleteExpense(expense)
        }.also { result ->
            result.fold(
                onSuccess = { Logger.d(TAG) { "交易删除成功: id=${expense.id}" } },
                onFailure = { e -> Logger.e(TAG, "交易删除失败", e) }
            )
        }
    }

    /**
     * 批量删除交易
     */
    suspend fun deleteTransactions(ids: List<Long>): BatchDeleteResult {
        Logger.d(TAG) { "批量删除交易: ${ids.size} 条" }
        return repository.deleteExpensesBatch(ids)
    }

    /**
     * 批量添加交易
     */
    suspend fun addTransactions(expenses: List<ExpenseEntity>): BatchInsertResult {
        Logger.d(TAG) { "批量添加交易: ${expenses.size} 条" }
        return repository.insertExpensesBatch(expenses)
    }

    /**
     * 获取按日期范围的交易
     */
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>> {
        return repository.getByDateRange(start, end)
    }

    /**
     * 获取最近的交易
     */
    fun getRecentTransactions(limit: Int = 10): Flow<List<ExpenseEntity>> {
        return repository.getRecent(limit)
    }

    /**
     * 删除所有交易记录
     */
    suspend fun deleteAllTransactions(): kotlin.Result<Unit> {
        return CoroutineHelper.runSafely {
            repository.deleteAllExpenses()
        }.also { result ->
            result.fold(
                onSuccess = { Logger.d(TAG) { "清除所有交易成功" } },
                onFailure = { e -> Logger.e(TAG, "清除所有交易失败", e) }
            )
        }
    }

    /**
     * 获取指定日期的交易
     */
    fun getTransactionsByDate(date: String): Flow<List<ExpenseEntity>> {
        return repository.getByDate(date)
    }

    // ==================== v1.9.6 新增 ====================

    /**
     * 按渠道获取交易记录
     * @param channel 渠道名称（如 "微信支付", "支付宝"）
     * @param limit 返回记录数限制，默认 200
     */
    fun getTransactionsByChannel(channel: String, limit: Int = 200): Flow<List<ExpenseEntity>> {
        Logger.d(TAG) { "按渠道查询交易: channel=$channel, limit=$limit" }
        return repository.getByChannel(channel, limit)
    }

    /**
     * 获取大额支出交易
     * @param minAmount 最小金额阈值
     * @param limit 返回记录数限制，默认 100
     */
    fun getLargeExpenses(minAmount: Double, limit: Int = 100): Flow<List<ExpenseEntity>> {
        Logger.d(TAG) { "查询大额交易: minAmount=$minAmount, limit=$limit" }
        return repository.getLargeExpenses(minAmount, limit)
    }
}
