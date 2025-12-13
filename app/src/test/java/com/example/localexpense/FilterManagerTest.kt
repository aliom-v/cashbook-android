package com.example.localexpense

import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.util.FilterManager
import com.example.localexpense.util.FilterManager.FilterCriteria
import com.example.localexpense.util.FilterManager.TransactionTypeFilter
import com.example.localexpense.util.FilterManager.SortBy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * FilterManager 单元测试
 * v1.9.8: 新增筛选管理器测试
 */
class FilterManagerTest {

    private lateinit var testTransactions: List<ExpenseEntity>

    @Before
    fun setup() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        testTransactions = listOf(
            createExpense(1, 100.0, "expense", "餐饮", "微信", "商户A", now),
            createExpense(2, 200.0, "expense", "购物", "支付宝", "商户B", now - dayMs),
            createExpense(3, 500.0, "income", "工资", "手动", "公司", now - 2 * dayMs),
            createExpense(4, 50.0, "expense", "交通", "微信", "地铁", now - 3 * dayMs),
            createExpense(5, 1000.0, "expense", "购物", "支付宝", "商户C", now - 4 * dayMs, "大额消费"),
            createExpense(6, 300.0, "income", "红包", "微信", "朋友", now - 5 * dayMs)
        )
    }

    private fun createExpense(
        id: Long,
        amount: Double,
        type: String,
        category: String,
        channel: String,
        merchant: String,
        timestamp: Long,
        note: String = ""
    ): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = amount,
            type = type,
            category = category,
            channel = channel,
            merchant = merchant,
            timestamp = timestamp,
            note = note
        )
    }

    // ==================== FilterCriteria 测试 ====================

    @Test
    fun `FilterCriteria EMPTY should have no filters`() {
        assertFalse(FilterCriteria.EMPTY.hasAnyFilter())
    }

    @Test
    fun `FilterCriteria with startTime should have filter`() {
        val criteria = FilterCriteria(startTime = System.currentTimeMillis())
        assertTrue(criteria.hasAnyFilter())
    }

    @Test
    fun `FilterCriteria with minAmount should have filter`() {
        val criteria = FilterCriteria(minAmount = 100.0)
        assertTrue(criteria.hasAnyFilter())
    }

    @Test
    fun `FilterCriteria with transactionType should have filter`() {
        val criteria = FilterCriteria(transactionType = TransactionTypeFilter.EXPENSE)
        assertTrue(criteria.hasAnyFilter())
    }

    @Test
    fun `FilterCriteria with categories should have filter`() {
        val criteria = FilterCriteria(categories = setOf("餐饮"))
        assertTrue(criteria.hasAnyFilter())
    }

    @Test
    fun `FilterCriteria getDescription should return correct description`() {
        val criteria = FilterCriteria(
            transactionType = TransactionTypeFilter.EXPENSE,
            categories = setOf("餐饮", "购物")
        )
        val desc = criteria.getDescription()
        assertTrue(desc.contains("仅支出"))
        assertTrue(desc.contains("2个分类"))
    }

    // ==================== filter 方法测试 ====================

    @Test
    fun `filter with empty criteria should return all transactions`() {
        val result = FilterManager.filter(testTransactions, FilterCriteria.EMPTY)
        assertEquals(6, result.totalCount)
    }

    @Test
    fun `filter by transaction type EXPENSE should return only expenses`() {
        val criteria = FilterCriteria(transactionType = TransactionTypeFilter.EXPENSE)
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(4, result.totalCount)
        assertTrue(result.transactions.all { it.type == "expense" })
    }

    @Test
    fun `filter by transaction type INCOME should return only incomes`() {
        val criteria = FilterCriteria(transactionType = TransactionTypeFilter.INCOME)
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(2, result.totalCount)
        assertTrue(result.transactions.all { it.type == "income" })
    }

    @Test
    fun `filter by minAmount should return transactions above threshold`() {
        val criteria = FilterCriteria(minAmount = 200.0)
        val result = FilterManager.filter(testTransactions, criteria)

        assertTrue(result.transactions.all { it.amount >= 200.0 })
    }

    @Test
    fun `filter by maxAmount should return transactions below threshold`() {
        val criteria = FilterCriteria(maxAmount = 100.0)
        val result = FilterManager.filter(testTransactions, criteria)

        assertTrue(result.transactions.all { it.amount <= 100.0 })
    }

    @Test
    fun `filter by amount range should return transactions in range`() {
        val criteria = FilterCriteria(minAmount = 100.0, maxAmount = 500.0)
        val result = FilterManager.filter(testTransactions, criteria)

        assertTrue(result.transactions.all { it.amount in 100.0..500.0 })
    }

    @Test
    fun `filter by category should return matching transactions`() {
        val criteria = FilterCriteria(categories = setOf("购物"))
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(2, result.totalCount)
        assertTrue(result.transactions.all { it.category == "购物" })
    }

    @Test
    fun `filter by multiple categories should return matching transactions`() {
        val criteria = FilterCriteria(categories = setOf("餐饮", "交通"))
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(2, result.totalCount)
        assertTrue(result.transactions.all { it.category in setOf("餐饮", "交通") })
    }

    @Test
    fun `filter by channel should return matching transactions`() {
        val criteria = FilterCriteria(channels = setOf("微信"))
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(3, result.totalCount)
        assertTrue(result.transactions.all { it.channel == "微信" })
    }

    @Test
    fun `filter by merchantKeyword should return matching transactions`() {
        val criteria = FilterCriteria(merchantKeyword = "商户")
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(3, result.totalCount)
        assertTrue(result.transactions.all { it.merchant.contains("商户") })
    }

    @Test
    fun `filter by hasNoteOnly should return transactions with notes`() {
        val criteria = FilterCriteria(hasNoteOnly = true)
        val result = FilterManager.filter(testTransactions, criteria)

        assertEquals(1, result.totalCount)
        assertTrue(result.transactions.all { it.note.isNotBlank() })
    }

    // ==================== 排序测试 ====================

    @Test
    fun `filter with TIME_DESC should sort by time descending`() {
        val criteria = FilterCriteria(sortBy = SortBy.TIME_DESC)
        val result = FilterManager.filter(testTransactions, criteria)

        for (i in 0 until result.transactions.size - 1) {
            assertTrue(result.transactions[i].timestamp >= result.transactions[i + 1].timestamp)
        }
    }

    @Test
    fun `filter with AMOUNT_DESC should sort by amount descending`() {
        val criteria = FilterCriteria(sortBy = SortBy.AMOUNT_DESC)
        val result = FilterManager.filter(testTransactions, criteria)

        for (i in 0 until result.transactions.size - 1) {
            assertTrue(result.transactions[i].amount >= result.transactions[i + 1].amount)
        }
    }

    @Test
    fun `filter with AMOUNT_ASC should sort by amount ascending`() {
        val criteria = FilterCriteria(sortBy = SortBy.AMOUNT_ASC)
        val result = FilterManager.filter(testTransactions, criteria)

        for (i in 0 until result.transactions.size - 1) {
            assertTrue(result.transactions[i].amount <= result.transactions[i + 1].amount)
        }
    }

    // ==================== FilterResult 统计测试 ====================

    @Test
    fun `FilterResult should calculate correct totals`() {
        val result = FilterManager.filter(testTransactions, FilterCriteria.EMPTY)

        // 支出: 100 + 200 + 50 + 1000 = 1350
        assertEquals(1350.0, result.totalExpense, 0.01)
        // 收入: 500 + 300 = 800
        assertEquals(800.0, result.totalIncome, 0.01)
        // 净额: 800 - 1350 = -550
        assertEquals(-550.0, result.netAmount, 0.01)
    }

    // ==================== 快捷筛选预设测试 ====================

    @Test
    fun `todayCriteria should have time range for today`() {
        val criteria = FilterManager.todayCriteria()

        assertNotNull(criteria.startTime)
        assertNotNull(criteria.endTime)
        assertTrue(criteria.startTime!! < criteria.endTime!!)
        assertTrue(criteria.hasAnyFilter())
    }

    @Test
    fun `thisWeekCriteria should have time range for this week`() {
        val criteria = FilterManager.thisWeekCriteria()

        assertNotNull(criteria.startTime)
        assertNotNull(criteria.endTime)
        assertTrue(criteria.startTime!! < criteria.endTime!!)
    }

    @Test
    fun `thisMonthCriteria should have time range for this month`() {
        val criteria = FilterManager.thisMonthCriteria()

        assertNotNull(criteria.startTime)
        assertNotNull(criteria.endTime)
        assertTrue(criteria.startTime!! < criteria.endTime!!)
    }

    @Test
    fun `monthCriteria should have correct time range`() {
        val criteria = FilterManager.monthCriteria(2024, 6)

        assertNotNull(criteria.startTime)
        assertNotNull(criteria.endTime)

        val startCal = Calendar.getInstance().apply { timeInMillis = criteria.startTime!! }
        assertEquals(2024, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, startCal.get(Calendar.MONTH))
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `largeExpenseCriteria should filter large expenses`() {
        val criteria = FilterManager.largeExpenseCriteria(500.0)

        assertEquals(500.0, criteria.minAmount)
        assertEquals(TransactionTypeFilter.EXPENSE, criteria.transactionType)
        assertEquals(SortBy.AMOUNT_DESC, criteria.sortBy)
    }

    @Test
    fun `categoryCriteria should filter by category`() {
        val criteria = FilterManager.categoryCriteria("餐饮")

        assertEquals(setOf("餐饮"), criteria.categories)
    }

    @Test
    fun `channelCriteria should filter by channel`() {
        val criteria = FilterManager.channelCriteria("微信")

        assertEquals(setOf("微信"), criteria.channels)
    }

    // ==================== 分组统计测试 ====================

    @Test
    fun `groupByCategory should group transactions correctly`() {
        val grouped = FilterManager.groupByCategory(testTransactions)

        assertTrue(grouped.containsKey("餐饮"))
        assertTrue(grouped.containsKey("购物"))
        assertEquals(1, grouped["餐饮"]?.count)
        assertEquals(2, grouped["购物"]?.count)
    }

    @Test
    fun `groupByChannel should group transactions correctly`() {
        val grouped = FilterManager.groupByChannel(testTransactions)

        assertTrue(grouped.containsKey("微信"))
        assertTrue(grouped.containsKey("支付宝"))
        assertEquals(3, grouped["微信"]?.count)
        assertEquals(2, grouped["支付宝"]?.count)
    }

    @Test
    fun `groupByDate should group transactions correctly`() {
        val grouped = FilterManager.groupByDate(testTransactions)

        assertTrue(grouped.isNotEmpty())
        // 每个日期应该有统计数据
        grouped.values.forEach { stats ->
            assertTrue(stats.count > 0)
        }
    }

    @Test
    fun `CategoryStats should calculate amounts correctly`() {
        val grouped = FilterManager.groupByCategory(testTransactions)
        val shoppingStats = grouped["购物"]!!

        assertEquals(2, shoppingStats.count)
        assertEquals(1200.0, shoppingStats.totalAmount, 0.01) // 200 + 1000
        assertEquals(1200.0, shoppingStats.expenseAmount, 0.01)
        assertEquals(0.0, shoppingStats.incomeAmount, 0.01)
    }

    @Test
    fun `DayStats netAmount should be calculated correctly`() {
        val grouped = FilterManager.groupByDate(testTransactions)

        grouped.values.forEach { stats ->
            assertEquals(stats.incomeAmount - stats.expenseAmount, stats.netAmount, 0.01)
        }
    }
}
