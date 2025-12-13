package com.example.localexpense.domain

import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.util.CoroutineHelper
import com.example.localexpense.util.DateUtils
import com.example.localexpense.util.Logger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预算相关 UseCase
 *
 * 封装预算管理的业务逻辑，支持 Hilt 依赖注入
 *
 * v1.9.3 新增
 */
@Singleton
class BudgetUseCases @Inject constructor(
    private val repository: ITransactionRepository
) {

    companion object {
        private const val TAG = "BudgetUseCases"
    }

    /**
     * 获取指定月份的预算
     */
    fun getBudgetsByMonth(month: Int): Flow<List<BudgetEntity>> {
        return repository.getBudgetsByMonth(month)
    }

    /**
     * 获取当月预算总额
     */
    fun getCurrentMonthBudget(): Flow<BudgetEntity?> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return repository.getTotalBudget(currentMonth)
    }

    /**
     * 获取指定月份的预算总额
     */
    fun getTotalBudget(month: Int): Flow<BudgetEntity?> {
        return repository.getTotalBudget(month)
    }

    /**
     * 添加预算
     */
    suspend fun addBudget(budget: BudgetEntity): kotlin.Result<Long> {
        return CoroutineHelper.runSafely {
            repository.insertBudget(budget)
        }.also { result ->
            result.fold(
                onSuccess = { id -> Logger.d(TAG) { "预算添加成功: id=$id, month=${budget.month}" } },
                onFailure = { e -> Logger.e(TAG, "预算添加失败", e) }
            )
        }
    }

    /**
     * 保存当月预算
     * @param amount 预算金额
     * @param existingBudgetId 已存在的预算ID（用于更新）
     */
    suspend fun saveCurrentMonthBudget(amount: Double, existingBudgetId: Long? = null): kotlin.Result<Long> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return CoroutineHelper.runSafely {
            val budget = BudgetEntity.create(
                id = existingBudgetId ?: 0,
                amount = amount,
                month = currentMonth
            )
            repository.insertBudget(budget)
        }.also { result ->
            result.fold(
                onSuccess = { id -> Logger.d(TAG) { "当月预算保存成功: id=$id" } },
                onFailure = { e -> Logger.e(TAG, "当月预算保存失败", e) }
            )
        }
    }

    /**
     * 删除预算
     */
    suspend fun deleteBudget(budget: BudgetEntity): kotlin.Result<Unit> {
        return CoroutineHelper.runSafely {
            repository.deleteBudget(budget)
        }.also { result ->
            result.fold(
                onSuccess = { Logger.d(TAG) { "预算删除成功: id=${budget.id}" } },
                onFailure = { e -> Logger.e(TAG, "预算删除失败", e) }
            )
        }
    }

    /**
     * 计算预算使用情况
     * @param budget 预算实体
     * @param totalExpense 当月总支出
     * @return 预算使用百分比 (0-100+)，超过100表示超支
     */
    fun calculateBudgetUsage(budget: BudgetEntity?, totalExpense: Double): Double {
        if (budget == null || budget.amount <= 0) return 0.0
        return (totalExpense / budget.amount) * 100
    }

    /**
     * 获取预算剩余金额
     * @param budget 预算实体
     * @param totalExpense 当月总支出
     * @return 剩余金额，负数表示超支
     */
    fun getRemainingBudget(budget: BudgetEntity?, totalExpense: Double): Double {
        if (budget == null) return 0.0
        return budget.amount - totalExpense
    }
}
