package com.example.localexpense.domain.usecase

import com.example.localexpense.data.CategoryStat
import com.example.localexpense.data.DailyStat
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.Result
import com.example.localexpense.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * 统计数据
 */
data class MonthlyStats(
    val totalExpense: Double,
    val totalIncome: Double,
    val balance: Double = totalIncome - totalExpense
)

/**
 * 统计相关 UseCase 集合
 */
class StatsUseCases(private val repository: TransactionRepository) {

    /**
     * 获取当月统计
     */
    fun getMonthlyStats(): Flow<Result<MonthlyStats>> {
        val (start, end) = DateUtils.getCurrentMonthRange()
        return combine(
            repository.getTotalExpense(start, end),
            repository.getTotalIncome(start, end)
        ) { expense, income ->
            MonthlyStats(
                totalExpense = expense ?: 0.0,
                totalIncome = income ?: 0.0
            )
        }.map<MonthlyStats, Result<MonthlyStats>> { Result.Success(it) }
            .catch { emit(Result.Error(it, "获取月度统计失败")) }
    }

    /**
     * 获取指定时间段的分类统计
     */
    fun getCategoryStats(
        type: String,
        period: DateUtils.StatsPeriod,
        calendar: Calendar
    ): Flow<Result<List<CategoryStat>>> {
        val (start, end) = DateUtils.getDateRange(calendar, period)
        return repository.getCategoryStats(type, start, end)
            .map<List<CategoryStat>, Result<List<CategoryStat>>> { Result.Success(it) }
            .catch { emit(Result.Error(it, "获取分类统计失败")) }
    }

    /**
     * 获取指定时间段的每日统计
     */
    fun getDailyStats(
        type: String,
        period: DateUtils.StatsPeriod,
        calendar: Calendar
    ): Flow<Result<List<DailyStat>>> {
        val (start, end) = DateUtils.getDateRange(calendar, period)
        return repository.getDailyStats(type, start, end)
            .map<List<DailyStat>, Result<List<DailyStat>>> { Result.Success(it) }
            .catch { emit(Result.Error(it, "获取每日统计失败")) }
    }
}
