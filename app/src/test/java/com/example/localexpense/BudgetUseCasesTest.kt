package com.example.localexpense

import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.domain.BudgetUseCases
import com.example.localexpense.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * BudgetUseCases 单元测试
 * v1.9.5 新增
 */
class BudgetUseCasesTest {

    private lateinit var repository: ITransactionRepository
    private lateinit var useCase: BudgetUseCases

    @Before
    fun setup() {
        repository = mock()
        useCase = BudgetUseCases(repository)
    }

    @Test
    fun `getBudgetsByMonth should return flow from repository`() = runTest {
        // Given
        val month = 202412
        val budgets = listOf(
            createBudgetEntity(1L, 5000.0, month),
            createBudgetEntity(2L, 3000.0, month)
        )
        whenever(repository.getBudgetsByMonth(month)).thenReturn(flowOf(budgets))

        // When
        val result = useCase.getBudgetsByMonth(month).first()

        // Then
        assertEquals(2, result.size)
        assertEquals(5000.0, result[0].amount, 0.01)
        verify(repository).getBudgetsByMonth(month)
    }

    @Test
    fun `getCurrentMonthBudget should use current month`() = runTest {
        // Given
        val budget = createBudgetEntity(1L, 10000.0, 202412)
        whenever(repository.getTotalBudget(any())).thenReturn(flowOf(budget))

        // When
        val result = useCase.getCurrentMonthBudget().first()

        // Then
        assertNotNull(result)
        assertEquals(10000.0, result?.amount ?: 0.0, 0.01)
        verify(repository).getTotalBudget(any())
    }

    @Test
    fun `getCurrentMonthBudget should return null when no budget`() = runTest {
        // Given
        whenever(repository.getTotalBudget(any())).thenReturn(flowOf(null))

        // When
        val result = useCase.getCurrentMonthBudget().first()

        // Then
        assertNull(result)
    }

    @Test
    fun `getTotalBudget should call repository with month`() = runTest {
        // Given
        val month = 202411
        val budget = createBudgetEntity(1L, 8000.0, month)
        whenever(repository.getTotalBudget(month)).thenReturn(flowOf(budget))

        // When
        val result = useCase.getTotalBudget(month).first()

        // Then
        assertNotNull(result)
        verify(repository).getTotalBudget(month)
    }

    @Test
    fun `addBudget should insert and return id`() = runTest {
        // Given
        val budget = createBudgetEntity(0L, 6000.0, 202412)
        whenever(repository.insertBudget(budget)).thenReturn(15L)

        // When
        val result = useCase.addBudget(budget)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(15L, result.getOrNull())
        verify(repository).insertBudget(budget)
    }

    @Test
    fun `addBudget should return failure on exception`() = runTest {
        // Given
        val budget = createBudgetEntity(0L, 5000.0, 202412)
        whenever(repository.insertBudget(budget)).thenThrow(RuntimeException("插入失败"))

        // When
        val result = useCase.addBudget(budget)

        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `saveCurrentMonthBudget should create new budget`() = runTest {
        // Given
        val amount = 12000.0
        whenever(repository.insertBudget(any())).thenReturn(25L)

        // When
        val result = useCase.saveCurrentMonthBudget(amount)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(25L, result.getOrNull())
        verify(repository).insertBudget(argThat { this.amount == amount })
    }

    @Test
    fun `saveCurrentMonthBudget should update existing budget`() = runTest {
        // Given
        val amount = 15000.0
        val existingId = 10L
        whenever(repository.insertBudget(any())).thenReturn(existingId)

        // When
        val result = useCase.saveCurrentMonthBudget(amount, existingId)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).insertBudget(argThat {
            this.amount == amount && this.id == existingId
        })
    }

    @Test
    fun `deleteBudget should call repository delete`() = runTest {
        // Given
        val budget = createBudgetEntity(5L, 8000.0, 202412)
        whenever(repository.deleteBudget(budget)).thenReturn(Unit)

        // When
        val result = useCase.deleteBudget(budget)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).deleteBudget(budget)
    }

    @Test
    fun `calculateBudgetUsage should return correct percentage`() {
        // Given
        val budget = createBudgetEntity(1L, 10000.0, 202412)
        val totalExpense = 5000.0

        // When
        val result = useCase.calculateBudgetUsage(budget, totalExpense)

        // Then
        assertEquals(50.0, result, 0.01)
    }

    @Test
    fun `calculateBudgetUsage should return over 100 when overspent`() {
        // Given
        val budget = createBudgetEntity(1L, 5000.0, 202412)
        val totalExpense = 7500.0

        // When
        val result = useCase.calculateBudgetUsage(budget, totalExpense)

        // Then
        assertEquals(150.0, result, 0.01)
    }

    @Test
    fun `calculateBudgetUsage should return 0 when budget is null`() {
        // When
        val result = useCase.calculateBudgetUsage(null, 5000.0)

        // Then
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `calculateBudgetUsage should return 0 when budget amount is 0`() {
        // Given
        val budget = createBudgetEntity(1L, 0.0, 202412)

        // When
        val result = useCase.calculateBudgetUsage(budget, 5000.0)

        // Then
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `getRemainingBudget should return correct remaining`() {
        // Given
        val budget = createBudgetEntity(1L, 10000.0, 202412)
        val totalExpense = 3000.0

        // When
        val result = useCase.getRemainingBudget(budget, totalExpense)

        // Then
        assertEquals(7000.0, result, 0.01)
    }

    @Test
    fun `getRemainingBudget should return negative when overspent`() {
        // Given
        val budget = createBudgetEntity(1L, 5000.0, 202412)
        val totalExpense = 8000.0

        // When
        val result = useCase.getRemainingBudget(budget, totalExpense)

        // Then
        assertEquals(-3000.0, result, 0.01)
    }

    @Test
    fun `getRemainingBudget should return 0 when budget is null`() {
        // When
        val result = useCase.getRemainingBudget(null, 5000.0)

        // Then
        assertEquals(0.0, result, 0.01)
    }

    // 辅助方法：创建测试用 BudgetEntity
    private fun createBudgetEntity(
        id: Long,
        amount: Double,
        month: Int
    ): BudgetEntity {
        return BudgetEntity(
            id = id,
            amount = amount,
            month = month,
            categoryId = null,
            notifyThreshold = 80
        )
    }
}
