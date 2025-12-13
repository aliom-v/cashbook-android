package com.example.localexpense

import com.example.localexpense.data.BatchDeleteResult
import com.example.localexpense.data.BatchInsertResult
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.domain.TransactionUseCases
import com.example.localexpense.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * TransactionUseCases 单元测试
 * v1.9.5 新增
 */
class TransactionUseCasesTest {

    private lateinit var repository: ITransactionRepository
    private lateinit var useCase: TransactionUseCases

    @Before
    fun setup() {
        repository = mock()
        useCase = TransactionUseCases(repository)
    }

    @Test
    fun `getAllTransactions should return flow from repository`() = runTest {
        // Given
        val expenses = listOf(
            createExpenseEntity(1L, 100.0, "商户A"),
            createExpenseEntity(2L, 200.0, "商户B")
        )
        whenever(repository.getAllFlow()).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getAllTransactions().first()

        // Then
        assertEquals(2, result.size)
        assertEquals(100.0, result[0].amount, 0.01)
        verify(repository).getAllFlow()
    }

    @Test
    fun `searchTransactions should call repository search`() = runTest {
        // Given
        val query = "测试"
        val expenses = listOf(createExpenseEntity(1L, 50.0, "测试商户"))
        whenever(repository.search(query)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.searchTransactions(query).first()

        // Then
        assertEquals(1, result.size)
        verify(repository).search(query)
    }

    @Test
    fun `addTransaction should insert new expense`() = runTest {
        // Given
        val expense = createExpenseEntity(0L, 99.99, "新商户")
        whenever(repository.insertExpense(expense)).thenReturn(123L)

        // When
        val result = useCase.addTransaction(expense)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(123L, result.getOrNull())
        verify(repository).insertExpense(expense)
        verify(repository, never()).updateExpense(any())
    }

    @Test
    fun `addTransaction should update existing expense`() = runTest {
        // Given
        val expense = createExpenseEntity(5L, 150.0, "已存在商户")
        whenever(repository.updateExpense(expense)).thenReturn(Unit)

        // When
        val result = useCase.addTransaction(expense)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull())
        verify(repository).updateExpense(expense)
        verify(repository, never()).insertExpense(any())
    }

    @Test
    fun `addTransaction should return failure on exception`() = runTest {
        // Given
        val expense = createExpenseEntity(0L, 100.0, "商户")
        whenever(repository.insertExpense(expense)).thenThrow(RuntimeException("数据库错误"))

        // When
        val result = useCase.addTransaction(expense)

        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `deleteTransaction should call repository delete`() = runTest {
        // Given
        val expense = createExpenseEntity(10L, 200.0, "待删除")
        whenever(repository.deleteExpense(expense)).thenReturn(Unit)

        // When
        val result = useCase.deleteTransaction(expense)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).deleteExpense(expense)
    }

    @Test
    fun `deleteTransactions batch should return result`() = runTest {
        // Given
        val ids = listOf(1L, 2L, 3L)
        val batchResult = BatchDeleteResult(3, 0, emptyList())
        whenever(repository.deleteExpensesBatch(ids)).thenReturn(batchResult)

        // When
        val result = useCase.deleteTransactions(ids)

        // Then
        assertEquals(3, result.successCount)
        assertEquals(0, result.failCount)
        verify(repository).deleteExpensesBatch(ids)
    }

    @Test
    fun `addTransactions batch should return result`() = runTest {
        // Given
        val expenses = listOf(
            createExpenseEntity(0L, 100.0, "商户1"),
            createExpenseEntity(0L, 200.0, "商户2")
        )
        val batchResult = BatchInsertResult(2, 0, emptyList())
        whenever(repository.insertExpensesBatch(expenses)).thenReturn(batchResult)

        // When
        val result = useCase.addTransactions(expenses)

        // Then
        assertEquals(2, result.successCount)
        assertEquals(0, result.failCount)
        verify(repository).insertExpensesBatch(expenses)
    }

    @Test
    fun `getTransactionsByDateRange should call repository`() = runTest {
        // Given
        val start = 1000L
        val end = 2000L
        val expenses = listOf(createExpenseEntity(1L, 50.0, "商户"))
        whenever(repository.getByDateRange(start, end)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getTransactionsByDateRange(start, end).first()

        // Then
        assertEquals(1, result.size)
        verify(repository).getByDateRange(start, end)
    }

    @Test
    fun `getRecentTransactions should call repository with limit`() = runTest {
        // Given
        val limit = 5
        val expenses = listOf(createExpenseEntity(1L, 100.0, "商户"))
        whenever(repository.getRecent(limit)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getRecentTransactions(limit).first()

        // Then
        assertEquals(1, result.size)
        verify(repository).getRecent(limit)
    }

    @Test
    fun `deleteAllTransactions should call repository`() = runTest {
        // Given
        whenever(repository.deleteAllExpenses()).thenReturn(Unit)

        // When
        val result = useCase.deleteAllTransactions()

        // Then
        assertTrue(result.isSuccess)
        verify(repository).deleteAllExpenses()
    }

    @Test
    fun `getTransactionsByDate should call repository`() = runTest {
        // Given
        val date = "2024-12-12"
        val expenses = listOf(createExpenseEntity(1L, 100.0, "商户"))
        whenever(repository.getByDate(date)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getTransactionsByDate(date).first()

        // Then
        assertEquals(1, result.size)
        verify(repository).getByDate(date)
    }

    // ==================== v1.9.7 新增测试 ====================

    @Test
    fun `getTransactionsByChannel should call repository with channel`() = runTest {
        // Given
        val channel = "微信支付"
        val limit = 100
        val expenses = listOf(
            createExpenseEntity(1L, 100.0, "商户A"),
            createExpenseEntity(2L, 200.0, "商户B")
        )
        whenever(repository.getByChannel(channel, limit)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getTransactionsByChannel(channel, limit).first()

        // Then
        assertEquals(2, result.size)
        verify(repository).getByChannel(channel, limit)
    }

    @Test
    fun `getTransactionsByChannel should use default limit`() = runTest {
        // Given
        val channel = "支付宝"
        val expenses = listOf(createExpenseEntity(1L, 50.0, "商户"))
        whenever(repository.getByChannel(channel, 200)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getTransactionsByChannel(channel).first()

        // Then
        assertEquals(1, result.size)
        verify(repository).getByChannel(channel, 200)
    }

    @Test
    fun `getLargeExpenses should call repository with minAmount`() = runTest {
        // Given
        val minAmount = 1000.0
        val limit = 50
        val expenses = listOf(
            createExpenseEntity(1L, 1500.0, "大额商户A"),
            createExpenseEntity(2L, 2000.0, "大额商户B")
        )
        whenever(repository.getLargeExpenses(minAmount, limit)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getLargeExpenses(minAmount, limit).first()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.amount >= minAmount })
        verify(repository).getLargeExpenses(minAmount, limit)
    }

    @Test
    fun `getLargeExpenses should use default limit`() = runTest {
        // Given
        val minAmount = 500.0
        val expenses = listOf(createExpenseEntity(1L, 800.0, "商户"))
        whenever(repository.getLargeExpenses(minAmount, 100)).thenReturn(flowOf(expenses))

        // When
        val result = useCase.getLargeExpenses(minAmount).first()

        // Then
        assertEquals(1, result.size)
        verify(repository).getLargeExpenses(minAmount, 100)
    }

    @Test
    fun `getLargeExpenses should return empty list when no large expenses`() = runTest {
        // Given
        val minAmount = 10000.0
        whenever(repository.getLargeExpenses(minAmount, 100)).thenReturn(flowOf(emptyList()))

        // When
        val result = useCase.getLargeExpenses(minAmount).first()

        // Then
        assertTrue(result.isEmpty())
        verify(repository).getLargeExpenses(minAmount, 100)
    }

    // 辅助方法：创建测试用 ExpenseEntity
    private fun createExpenseEntity(
        id: Long,
        amount: Double,
        merchant: String,
        type: String = "expense",
        category: String = "其他"
    ): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = amount,
            merchant = merchant,
            type = type,
            category = category,
            categoryId = 1L,
            timestamp = System.currentTimeMillis(),
            note = "",
            rawText = "",
            channel = "manual"
        )
    }
}
