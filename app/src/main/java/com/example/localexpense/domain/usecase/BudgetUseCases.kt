package com.example.localexpense.domain.usecase

import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.Result
import com.example.localexpense.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 预算相关 UseCase 集合
 */
class BudgetUseCases(private val repository: TransactionRepository) {

    /**
     * 获取当月预算
     */
    fun getCurrentMonthBudget(): Flow<Result<BudgetEntity?>> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return repository.getTotalBudget(currentMonth)
            .map<BudgetEntity?, Result<BudgetEntity?>> { Result.Success(it) }
            .catch { emit(Result.Error(it, "获取预算失败")) }
    }

    /**
     * 保存预算
     */
    suspend fun saveBudget(amount: Double, existingBudget: BudgetEntity?): Result<Unit> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return Result.suspendRunCatching {
            repository.insertBudget(
                BudgetEntity(
                    id = existingBudget?.id ?: 0,
                    amount = amount,
                    month = currentMonth
                )
            )
        }
    }
}
