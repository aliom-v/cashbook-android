package com.example.localexpense.domain

import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.data.CategoryStat
import com.example.localexpense.data.DailyStat
import com.example.localexpense.data.ExpenseIncomeStat
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.util.CoroutineHelper
import com.example.localexpense.util.DateUtils
import com.example.localexpense.util.Logger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统计相关 UseCase
 *
 * 封装统计查询的业务逻辑，支持 Hilt 依赖注入
 *
 * v1.9.0 新增
 * v1.9.3 重构：添加 Hilt @Inject 注解，使用 ITransactionRepository 接口
 */
@Singleton
class StatisticsUseCases @Inject constructor(
    private val repository: ITransactionRepository
) {

    companion object {
        private const val TAG = "StatisticsUseCases"
    }

    /**
     * 获取月度支出和收入总计
     */
    fun getMonthlyStats(): Flow<ExpenseIncomeStat> {
        val (monthStart, monthEnd) = DateUtils.getCurrentMonthRange()
        return repository.getTotalExpenseAndIncome(monthStart, monthEnd)
    }

    /**
     * 获取指定日期范围的支出和收入
     */
    fun getStatsForRange(start: Long, end: Long): Flow<ExpenseIncomeStat> {
        return repository.getTotalExpenseAndIncome(start, end)
    }

    /**
     * 获取分类统计
     */
    fun getCategoryStats(type: String, start: Long, end: Long): Flow<List<CategoryStat>> {
        return repository.getCategoryStats(type, start, end)
    }

    /**
     * 获取每日统计
     */
    fun getDailyStats(type: String, start: Long, end: Long): Flow<List<DailyStat>> {
        return repository.getDailyStats(type, start, end)
    }

    /**
     * 获取当月预算
     */
    fun getCurrentBudget(): Flow<BudgetEntity?> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return repository.getTotalBudget(currentMonth)
    }

    /**
     * 保存预算
     */
    suspend fun saveBudget(amount: Double, existingBudgetId: Long? = null): kotlin.Result<Unit> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return CoroutineHelper.runSafely {
            val budget = BudgetEntity.create(
                id = existingBudgetId ?: 0,
                amount = amount,
                month = currentMonth
            )
            repository.insertBudget(budget)
            Unit // 显式返回 Unit，忽略 insertBudget 返回的 Long
        }.also { result ->
            result.fold(
                onSuccess = { Logger.d(TAG) { "预算保存成功" } },
                onFailure = { e -> Logger.e(TAG, "预算保存失败", e) }
            )
        }
    }

    // ==================== v1.9.6 新增 ====================

    /**
     * 获取分类统计（带数量和平均值）
     * @param type 类型（expense/income）
     * @param start 开始时间戳
     * @param end 结束时间戳
     * @param limit 返回分类数限制，默认 20
     */
    fun getCategoryStatsWithCount(
        type: String,
        start: Long,
        end: Long,
        limit: Int = 20
    ): Flow<List<com.example.localexpense.data.CategoryStatWithCount>> {
        Logger.d(TAG) { "获取分类统计(带数量): type=$type, limit=$limit" }
        return repository.getCategoryStatsWithCount(type, start, end, limit)
    }

    /**
     * 获取月度趋势统计
     * 用于年度报表展示收支趋势
     * @param start 开始时间戳
     * @param end 结束时间戳
     */
    fun getMonthlyTrend(start: Long, end: Long): Flow<List<com.example.localexpense.data.MonthlyTrendStat>> {
        Logger.d(TAG) { "获取月度趋势统计" }
        return repository.getMonthlyTrend(start, end)
    }

    /**
     * 获取年度趋势统计（便捷方法）
     * 自动计算当前年度的时间范围
     */
    fun getYearlyTrend(): Flow<List<com.example.localexpense.data.MonthlyTrendStat>> {
        val (yearStart, yearEnd) = DateUtils.getCurrentYearRange()
        return getMonthlyTrend(yearStart, yearEnd)
    }
}
