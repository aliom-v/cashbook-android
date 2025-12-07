package com.example.localexpense.util

import com.example.localexpense.data.ExpenseEntity
import java.util.Calendar

/**
 * 交易筛选管理器
 *
 * 功能：
 * 1. 多条件组合筛选
 * 2. 快捷筛选预设
 * 3. 筛选结果统计
 * 4. 筛选条件持久化
 *
 * 使用场景：
 * - 交易列表筛选
 * - 数据导出筛选
 * - 统计分析筛选
 */
object FilterManager {

    private const val TAG = "FilterManager"

    /**
     * 筛选条件
     */
    data class FilterCriteria(
        // 时间范围
        val startTime: Long? = null,
        val endTime: Long? = null,

        // 金额范围
        val minAmount: Double? = null,
        val maxAmount: Double? = null,

        // 类型筛选
        val transactionType: TransactionTypeFilter = TransactionTypeFilter.ALL,

        // 分类筛选 (多选)
        val categories: Set<String> = emptySet(),

        // 渠道筛选 (多选)
        val channels: Set<String> = emptySet(),

        // 商户关键词
        val merchantKeyword: String? = null,

        // 备注关键词
        val noteKeyword: String? = null,

        // 排序方式
        val sortBy: SortBy = SortBy.TIME_DESC,

        // 是否只显示有备注的
        val hasNoteOnly: Boolean = false
    ) {
        /**
         * 是否有任何筛选条件
         */
        fun hasAnyFilter(): Boolean {
            return startTime != null ||
                    endTime != null ||
                    minAmount != null ||
                    maxAmount != null ||
                    transactionType != TransactionTypeFilter.ALL ||
                    categories.isNotEmpty() ||
                    channels.isNotEmpty() ||
                    !merchantKeyword.isNullOrBlank() ||
                    !noteKeyword.isNullOrBlank() ||
                    hasNoteOnly
        }

        /**
         * 获取筛选条件描述
         */
        fun getDescription(): String {
            val parts = mutableListOf<String>()

            if (startTime != null || endTime != null) {
                parts.add("时间范围")
            }
            if (minAmount != null || maxAmount != null) {
                parts.add("金额范围")
            }
            if (transactionType != TransactionTypeFilter.ALL) {
                parts.add(transactionType.displayName)
            }
            if (categories.isNotEmpty()) {
                parts.add("${categories.size}个分类")
            }
            if (channels.isNotEmpty()) {
                parts.add("${channels.size}个渠道")
            }
            if (!merchantKeyword.isNullOrBlank()) {
                parts.add("商户筛选")
            }

            return if (parts.isEmpty()) "全部" else parts.joinToString(" + ")
        }

        companion object {
            val EMPTY = FilterCriteria()
        }
    }

    /**
     * 交易类型筛选
     */
    enum class TransactionTypeFilter(val displayName: String, val value: String?) {
        ALL("全部", null),
        EXPENSE("仅支出", "expense"),
        INCOME("仅收入", "income")
    }

    /**
     * 排序方式
     */
    enum class SortBy(val displayName: String) {
        TIME_DESC("时间降序"),
        TIME_ASC("时间升序"),
        AMOUNT_DESC("金额降序"),
        AMOUNT_ASC("金额升序"),
        MERCHANT_ASC("商户名称")
    }

    /**
     * 筛选结果
     */
    data class FilterResult(
        val transactions: List<ExpenseEntity>,
        val totalCount: Int,
        val totalExpense: Double,
        val totalIncome: Double,
        val criteria: FilterCriteria
    ) {
        val netAmount: Double get() = totalIncome - totalExpense
    }

    // ==================== 筛选方法 ====================

    /**
     * 应用筛选条件
     */
    fun filter(
        transactions: List<ExpenseEntity>,
        criteria: FilterCriteria
    ): FilterResult {
        var result = transactions.asSequence()

        // 时间范围
        criteria.startTime?.let { start ->
            result = result.filter { it.timestamp >= start }
        }
        criteria.endTime?.let { end ->
            result = result.filter { it.timestamp <= end }
        }

        // 金额范围
        criteria.minAmount?.let { min ->
            result = result.filter { it.amount >= min }
        }
        criteria.maxAmount?.let { max ->
            result = result.filter { it.amount <= max }
        }

        // 交易类型
        criteria.transactionType.value?.let { type ->
            result = result.filter { it.type == type }
        }

        // 分类筛选
        if (criteria.categories.isNotEmpty()) {
            result = result.filter { it.category in criteria.categories }
        }

        // 渠道筛选
        if (criteria.channels.isNotEmpty()) {
            result = result.filter { it.channel in criteria.channels }
        }

        // 商户关键词
        criteria.merchantKeyword?.takeIf { it.isNotBlank() }?.let { keyword ->
            result = result.filter { it.merchant.contains(keyword, ignoreCase = true) }
        }

        // 备注关键词
        criteria.noteKeyword?.takeIf { it.isNotBlank() }?.let { keyword ->
            result = result.filter { it.note?.contains(keyword, ignoreCase = true) == true }
        }

        // 只显示有备注的
        if (criteria.hasNoteOnly) {
            result = result.filter { !it.note.isNullOrBlank() }
        }

        // 转换为列表并排序
        val filteredList = result.toList()
        val sortedList = sortTransactions(filteredList, criteria.sortBy)

        // 统计
        val expenses = sortedList.filter { it.type == "expense" }
        val incomes = sortedList.filter { it.type == "income" }

        return FilterResult(
            transactions = sortedList,
            totalCount = sortedList.size,
            totalExpense = expenses.sumOf { it.amount },
            totalIncome = incomes.sumOf { it.amount },
            criteria = criteria
        )
    }

    /**
     * 排序交易
     */
    private fun sortTransactions(
        transactions: List<ExpenseEntity>,
        sortBy: SortBy
    ): List<ExpenseEntity> {
        return when (sortBy) {
            SortBy.TIME_DESC -> transactions.sortedByDescending { it.timestamp }
            SortBy.TIME_ASC -> transactions.sortedBy { it.timestamp }
            SortBy.AMOUNT_DESC -> transactions.sortedByDescending { it.amount }
            SortBy.AMOUNT_ASC -> transactions.sortedBy { it.amount }
            SortBy.MERCHANT_ASC -> transactions.sortedBy { it.merchant }
        }
    }

    // ==================== 快捷筛选预设 ====================

    /**
     * 今日交易
     */
    fun todayCriteria(): FilterCriteria {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis - 1

        return FilterCriteria(startTime = startOfDay, endTime = endOfDay)
    }

    /**
     * 本周交易
     */
    fun thisWeekCriteria(): FilterCriteria {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val endOfWeek = calendar.timeInMillis - 1

        return FilterCriteria(startTime = startOfWeek, endTime = endOfWeek)
    }

    /**
     * 本月交易
     */
    fun thisMonthCriteria(): FilterCriteria {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1

        return FilterCriteria(startTime = startOfMonth, endTime = endOfMonth)
    }

    /**
     * 指定月份交易
     */
    fun monthCriteria(year: Int, month: Int): FilterCriteria {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1

        return FilterCriteria(startTime = startOfMonth, endTime = endOfMonth)
    }

    /**
     * 大额支出 (超过指定金额)
     */
    fun largeExpenseCriteria(threshold: Double = 100.0): FilterCriteria {
        return FilterCriteria(
            minAmount = threshold,
            transactionType = TransactionTypeFilter.EXPENSE,
            sortBy = SortBy.AMOUNT_DESC
        )
    }

    /**
     * 指定分类
     */
    fun categoryCriteria(category: String): FilterCriteria {
        return FilterCriteria(categories = setOf(category))
    }

    /**
     * 指定渠道
     */
    fun channelCriteria(channel: String): FilterCriteria {
        return FilterCriteria(channels = setOf(channel))
    }

    // ==================== 统计辅助 ====================

    /**
     * 按分类分组统计
     */
    fun groupByCategory(transactions: List<ExpenseEntity>): Map<String, CategoryStats> {
        return transactions.groupBy { it.category }
            .mapValues { (category, txList) ->
                CategoryStats(
                    category = category,
                    count = txList.size,
                    totalAmount = txList.sumOf { it.amount },
                    expenseAmount = txList.filter { it.type == "expense" }.sumOf { it.amount },
                    incomeAmount = txList.filter { it.type == "income" }.sumOf { it.amount }
                )
            }
    }

    /**
     * 按渠道分组统计
     */
    fun groupByChannel(transactions: List<ExpenseEntity>): Map<String, ChannelStats> {
        return transactions.groupBy { it.channel }
            .mapValues { (channel, txList) ->
                ChannelStats(
                    channel = channel,
                    count = txList.size,
                    totalAmount = txList.sumOf { it.amount },
                    expenseAmount = txList.filter { it.type == "expense" }.sumOf { it.amount },
                    incomeAmount = txList.filter { it.type == "income" }.sumOf { it.amount }
                )
            }
    }

    /**
     * 按日期分组统计
     */
    fun groupByDate(transactions: List<ExpenseEntity>): Map<String, DayStats> {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return transactions.groupBy { dateFormat.format(java.util.Date(it.timestamp)) }
            .mapValues { (date, txList) ->
                DayStats(
                    date = date,
                    count = txList.size,
                    expenseAmount = txList.filter { it.type == "expense" }.sumOf { it.amount },
                    incomeAmount = txList.filter { it.type == "income" }.sumOf { it.amount }
                )
            }
    }

    // ==================== 统计数据类 ====================

    data class CategoryStats(
        val category: String,
        val count: Int,
        val totalAmount: Double,
        val expenseAmount: Double,
        val incomeAmount: Double
    )

    data class ChannelStats(
        val channel: String,
        val count: Int,
        val totalAmount: Double,
        val expenseAmount: Double,
        val incomeAmount: Double
    )

    data class DayStats(
        val date: String,
        val count: Int,
        val expenseAmount: Double,
        val incomeAmount: Double
    ) {
        val netAmount: Double get() = incomeAmount - expenseAmount
    }
}
