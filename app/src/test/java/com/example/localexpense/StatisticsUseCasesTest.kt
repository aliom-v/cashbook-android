package com.example.localexpense

import com.example.localexpense.data.CategoryStat
import com.example.localexpense.data.DailyStat
import com.example.localexpense.data.ExpenseIncomeStat
import com.example.localexpense.domain.StatisticsUseCases
import com.example.localexpense.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * StatisticsUseCases 单元测试
 * v1.9.5 新增
 */
class StatisticsUseCasesTest {

    private lateinit var repository: ITransactionRepository
    private lateinit var useCase: StatisticsUseCases

    @Before
    fun setup() {
        repository = mock()
        useCase = StatisticsUseCases(repository)
    }

    @Test
    fun `getMonthlyStats should return expense and income totals`() = runTest {
        // Given
        val stat = ExpenseIncomeStat(totalExpense = 5000.0, totalIncome = 8000.0)
        whenever(repository.getTotalExpenseAndIncome(any(), any())).thenReturn(flowOf(stat))

        // When
        val result = useCase.getMonthlyStats().first()

        // Then
        assertEquals(5000.0, result.totalExpense, 0.01)
        assertEquals(8000.0, result.totalIncome, 0.01)
        verify(repository).getTotalExpenseAndIncome(any(), any())
    }

    @Test
    fun `getStatsForRange should call repository with range`() = runTest {
        // Given
        val start = 1000L
        val end = 2000L
        val stat = ExpenseIncomeStat(totalExpense = 3000.0, totalIncome = 4000.0)
        whenever(repository.getTotalExpenseAndIncome(start, end)).thenReturn(flowOf(stat))

        // When
        val result = useCase.getStatsForRange(start, end).first()

        // Then
        assertEquals(3000.0, result.totalExpense, 0.01)
        assertEquals(4000.0, result.totalIncome, 0.01)
        verify(repository).getTotalExpenseAndIncome(start, end)
    }

    @Test
    fun `getCategoryStats should return category statistics`() = runTest {
        // Given
        val type = "expense"
        val start = 1000L
        val end = 2000L
        val stats = listOf(
            CategoryStat(category = "餐饮", total = 1500.0),
            CategoryStat(category = "交通", total = 800.0)
        )
        whenever(repository.getCategoryStats(type, start, end)).thenReturn(flowOf(stats))

        // When
        val result = useCase.getCategoryStats(type, start, end).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("餐饮", result[0].category)
        assertEquals(1500.0, result[0].total, 0.01)
        verify(repository).getCategoryStats(type, start, end)
    }

    @Test
    fun `getCategoryStats should return empty list when no data`() = runTest {
        // Given
        val type = "expense"
        val start = 1000L
        val end = 2000L
        whenever(repository.getCategoryStats(type, start, end)).thenReturn(flowOf(emptyList()))

        // When
        val result = useCase.getCategoryStats(type, start, end).first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getDailyStats should return daily statistics`() = runTest {
        // Given
        val type = "expense"
        val start = 1000L
        val end = 2000L
        val stats = listOf(
            DailyStat(date = "2024-12-10", total = 500.0),
            DailyStat(date = "2024-12-11", total = 800.0)
        )
        whenever(repository.getDailyStats(type, start, end)).thenReturn(flowOf(stats))

        // When
        val result = useCase.getDailyStats(type, start, end).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("2024-12-10", result[0].date)
        assertEquals(500.0, result[0].total, 0.01)
        verify(repository).getDailyStats(type, start, end)
    }

    @Test
    fun `getDailyStats should return empty list when no data`() = runTest {
        // Given
        val type = "income"
        val start = 1000L
        val end = 2000L
        whenever(repository.getDailyStats(type, start, end)).thenReturn(flowOf(emptyList()))

        // When
        val result = useCase.getDailyStats(type, start, end).first()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCurrentBudget should use current month id`() = runTest {
        // Given
        whenever(repository.getTotalBudget(any())).thenReturn(flowOf(null))

        // When
        val result = useCase.getCurrentBudget().first()

        // Then
        assertNull(result)
        verify(repository).getTotalBudget(any())
    }

    @Test
    fun `saveBudget should insert budget with current month`() = runTest {
        // Given
        val amount = 10000.0
        whenever(repository.insertBudget(any())).thenReturn(50L)

        // When
        val result = useCase.saveBudget(amount)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).insertBudget(argThat { this.amount == amount })
    }

    @Test
    fun `saveBudget should update existing budget`() = runTest {
        // Given
        val amount = 15000.0
        val existingId = 25L
        whenever(repository.insertBudget(any())).thenReturn(existingId)

        // When
        val result = useCase.saveBudget(amount, existingId)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).insertBudget(argThat {
            this.amount == amount && this.id == existingId
        })
    }

    @Test
    fun `saveBudget should return failure on exception`() = runTest {
        // Given
        val amount = 8000.0
        whenever(repository.insertBudget(any())).thenThrow(RuntimeException("保存失败"))

        // When
        val result = useCase.saveBudget(amount)

        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    // ==================== v1.9.7 新增测试 ====================

    @Test
    fun `getCategoryStatsWithCount should return category stats with count`() = runTest {
        // Given
        val type = "expense"
        val start = 1000L
        val end = 2000L
        val limit = 10
        val stats = listOf(
            com.example.localexpense.data.CategoryStatWithCount(category = "餐饮", total = 1500.0, count = 10),
            com.example.localexpense.data.CategoryStatWithCount(category = "交通", total = 800.0, count = 5)
        )
        whenever(repository.getCategoryStatsWithCount(type, start, end, limit)).thenReturn(flowOf(stats))

        // When
        val result = useCase.getCategoryStatsWithCount(type, start, end, limit).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("餐饮", result[0].category)
        assertEquals(1500.0, result[0].total, 0.01)
        assertEquals(10, result[0].count)
        assertEquals(150.0, result[0].average, 0.01) // 1500 / 10
        verify(repository).getCategoryStatsWithCount(type, start, end, limit)
    }

    @Test
    fun `getMonthlyTrend should return monthly trend statistics`() = runTest {
        // Given
        val start = 1000L
        val end = 2000L
        val stats = listOf(
            com.example.localexpense.data.MonthlyTrendStat(month = "2024-11", expense = 5000.0, income = 8000.0),
            com.example.localexpense.data.MonthlyTrendStat(month = "2024-12", expense = 6000.0, income = 9000.0)
        )
        whenever(repository.getMonthlyTrend(start, end)).thenReturn(flowOf(stats))

        // When
        val result = useCase.getMonthlyTrend(start, end).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("2024-11", result[0].month)
        assertEquals(5000.0, result[0].expense, 0.01)
        assertEquals(8000.0, result[0].income, 0.01)
        assertEquals(3000.0, result[0].net, 0.01) // 8000 - 5000
        verify(repository).getMonthlyTrend(start, end)
    }

    @Test
    fun `getYearlyTrend should call getMonthlyTrend with year range`() = runTest {
        // Given
        val stats = listOf(
            com.example.localexpense.data.MonthlyTrendStat(month = "2024-01", expense = 4000.0, income = 7000.0)
        )
        whenever(repository.getMonthlyTrend(any(), any())).thenReturn(flowOf(stats))

        // When
        val result = useCase.getYearlyTrend().first()

        // Then
        assertEquals(1, result.size)
        verify(repository).getMonthlyTrend(any(), any())
    }

    @Test
    fun `MonthlyTrendStat savingsRate should calculate correctly`() {
        // Given
        val stat = com.example.localexpense.data.MonthlyTrendStat(
            month = "2024-12",
            expense = 6000.0,
            income = 10000.0
        )

        // Then
        assertEquals(4000.0, stat.net, 0.01) // 10000 - 6000
        assertEquals(40.0, stat.savingsRate, 0.01) // (4000 / 10000) * 100
    }

    @Test
    fun `MonthlyTrendStat savingsRate should be zero when income is zero`() {
        // Given
        val stat = com.example.localexpense.data.MonthlyTrendStat(
            month = "2024-12",
            expense = 1000.0,
            income = 0.0
        )

        // Then
        assertEquals(-1000.0, stat.net, 0.01)
        assertEquals(0.0, stat.savingsRate, 0.01) // 收入为0时返回0
    }
}
