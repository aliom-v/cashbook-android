package com.example.localexpense.domain.usecase

import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.Result
import com.example.localexpense.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 账单相关 UseCase 集合
 */
class ExpenseUseCases(private val repository: TransactionRepository) {

    /**
     * 获取所有账单（响应式）
     */
    fun getAllExpenses(): Flow<Result<List<ExpenseEntity>>> {
        return repository.getAllFlow()
            .map<List<ExpenseEntity>, Result<List<ExpenseEntity>>> { Result.Success(it) }
            .catch { emit(Result.Error(it, "获取账单失败")) }
    }

    /**
     * 获取分组账单（按日期分组）
     */
    fun getGroupedExpenses(): Flow<Result<Map<String, List<ExpenseEntity>>>> {
        return repository.getAllFlow()
            .map<List<ExpenseEntity>, Result<Map<String, List<ExpenseEntity>>>> { expenses ->
                val grouped = expenses.groupBy { DateUtils.formatDate(it.timestamp) }
                Result.Success(grouped)
            }
            .catch { emit(Result.Error(it, "获取分组账单失败")) }
    }

    /**
     * 添加或更新账单
     */
    suspend fun saveExpense(expense: ExpenseEntity): Result<Long> {
        return Result.suspendRunCatching {
            if (expense.id == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
                expense.id
            }
        }
    }

    /**
     * 删除账单
     */
    suspend fun deleteExpense(expense: ExpenseEntity): Result<Unit> {
        return Result.suspendRunCatching {
            repository.deleteExpense(expense)
        }
    }

    /**
     * 搜索账单
     */
    fun searchExpenses(query: String): Flow<List<ExpenseEntity>> {
        return if (query.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.search(query)
        }
    }
}
