package com.example.localexpense.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.localexpense.BuildConfig
import com.example.localexpense.data.*
import com.example.localexpense.util.Constants
import com.example.localexpense.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.*

private const val TAG = "MainViewModel"

/** 仅在 Debug 模式下输出日志 */
private inline fun logD(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(TAG, message())
    }
}

/**
 * UI状态
 */
data class UiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val groupedExpenses: Map<String, List<ExpenseEntity>> = emptyMap(),
    val categories: List<CategoryEntity> = emptyList(),
    val monthlyExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val budget: BudgetEntity? = null,
    // 支出统计
    val categoryStats: List<CategoryStat> = emptyList(),
    val dailyStats: List<DailyStat> = emptyList(),
    // 收入统计
    val incomeCategoryStats: List<CategoryStat> = emptyList(),
    val incomeDailyStats: List<DailyStat> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ExpenseEntity> = emptyList(),
    val selectedCalendarDate: String = DateUtils.getTodayString(),
    val calendarMonthMillis: Long = System.currentTimeMillis(),
    val statsPeriod: DateUtils.StatsPeriod = DateUtils.StatsPeriod.MONTH,
    val statsDateMillis: Long = System.currentTimeMillis(),
    // 加载和错误状态
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val calendarMonth: Calendar get() = Calendar.getInstance().apply { timeInMillis = calendarMonthMillis }
    val statsDate: Calendar get() = Calendar.getInstance().apply { timeInMillis = statsDateMillis }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    // 搜索查询的 Flow（用于防抖）
    private val _searchQuery = MutableStateFlow("")

    // 统计参数的 Flow，用于响应式更新统计数据
    private val _statsPeriod = MutableStateFlow(DateUtils.StatsPeriod.MONTH)
    private val _statsDateMillis = MutableStateFlow(System.currentTimeMillis())

    init {
        loadData()
        // 移除 initCategories()，已在 Repository 中自动初始化
        setupSearch()
        setupStats()
    }

    /**
     * 使用 combine 合并多个 Flow，确保状态一致性
     * 使用 stateIn 缓存 Flow 结果，避免重复订阅
     */
    private fun loadData() {
        viewModelScope.launch {
            try {
                // 缓存主要数据流，避免重复查询
                val expensesFlow = repo.getAllFlow()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                val categoriesFlow = repo.getAllCategories()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

                // 合并主要数据流
                combine(
                    expensesFlow,
                    categoriesFlow,
                    loadMonthlyStatsFlow(),
                    loadBudgetFlow()
                ) { expenses, categories, monthlyStats, budget ->
                    // 计算分组
                    val grouped = expenses.groupBy { DateUtils.formatDate(it.timestamp) }

                    // 返回更新数据用于原子更新
                    DataUpdate(expenses, grouped, categories, monthlyStats, budget)
                }.collect { data ->
                    // 使用 update 原子更新，避免并发状态覆盖
                    _state.update { current ->
                        current.copy(
                            expenses = data.expenses,
                            groupedExpenses = data.grouped,
                            categories = data.categories,
                            monthlyExpense = data.monthlyStats.first,
                            monthlyIncome = data.monthlyStats.second,
                            budget = data.budget,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                updateError("加载数据失败: ${e.message}")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    // 统计刷新触发器（手动触发或数据变化时触发）
    private val _statsRefreshTrigger = MutableStateFlow(0L)

    /**
     * 设置统计数据的响应式加载
     * 同时响应：1. 统计参数变化  2. 手动刷新触发
     * 同时加载支出和收入的统计数据
     *
     * 优化：不再监听整个数据库变化，改为通过 refreshStats() 手动触发
     * 这样可以避免频繁的统计查询，提高性能
     */
    private fun setupStats() {
        viewModelScope.launch {
            // 合并统计参数变化和刷新触发器
            combine(
                _statsPeriod,
                _statsDateMillis,
                _statsRefreshTrigger
            ) { period, dateMillis, _ ->
                val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                val (start, end) = DateUtils.getDateRange(calendar, period)
                Triple(period, dateMillis, Pair(start, end))
            }.flatMapLatest { (period, dateMillis, range) ->
                val (start, end) = range
                // 同时查询支出和收入的统计数据
                combine(
                    repo.getCategoryStats("expense", start, end),
                    repo.getDailyStats("expense", start, end),
                    repo.getCategoryStats("income", start, end),
                    repo.getDailyStats("income", start, end)
                ) { expenseCategoryStats, expenseDailyStats, incomeCategoryStats, incomeDailyStats ->
                    StatsData(
                        period = period,
                        dateMillis = dateMillis,
                        expenseCategoryStats = expenseCategoryStats,
                        expenseDailyStats = expenseDailyStats,
                        incomeCategoryStats = incomeCategoryStats,
                        incomeDailyStats = incomeDailyStats
                    )
                }
            }
            .catch { e ->
                // Flow 异常处理，避免整个统计流崩溃
                if (BuildConfig.DEBUG) Log.e(TAG, "统计数据加载失败", e)
                updateError("统计加载失败: ${e.message}")
            }
            .collect { statsData ->
                // 使用 update 原子更新，避免并发状态覆盖
                _state.update { current ->
                    current.copy(
                        statsPeriod = statsData.period,
                        statsDateMillis = statsData.dateMillis,
                        categoryStats = statsData.expenseCategoryStats,
                        dailyStats = statsData.expenseDailyStats,
                        incomeCategoryStats = statsData.incomeCategoryStats,
                        incomeDailyStats = statsData.incomeDailyStats
                    )
                }
            }
        }
    }

    /**
     * 手动刷新统计数据
     * 在添加/删除交易后调用
     */
    fun refreshStats() {
        _statsRefreshTrigger.value = System.currentTimeMillis()
    }

    // 主数据更新的临时数据类（用于原子更新）
    private data class DataUpdate(
        val expenses: List<ExpenseEntity>,
        val grouped: Map<String, List<ExpenseEntity>>,
        val categories: List<CategoryEntity>,
        val monthlyStats: Pair<Double, Double>,
        val budget: BudgetEntity?
    )

    // 统计数据的临时数据类
    private data class StatsData(
        val period: DateUtils.StatsPeriod,
        val dateMillis: Long,
        val expenseCategoryStats: List<CategoryStat>,
        val expenseDailyStats: List<DailyStat>,
        val incomeCategoryStats: List<CategoryStat>,
        val incomeDailyStats: List<DailyStat>
    )

    private fun loadMonthlyStatsFlow(): Flow<Pair<Double, Double>> {
        val (monthStart, monthEnd) = DateUtils.getCurrentMonthRange()
        return combine(
            repo.getTotalExpense(monthStart, monthEnd),
            repo.getTotalIncome(monthStart, monthEnd)
        ) { expense, income ->
            Pair(expense ?: 0.0, income ?: 0.0)
        }
    }

    private fun loadBudgetFlow(): Flow<BudgetEntity?> {
        val currentMonth = DateUtils.getCurrentMonthId()
        return repo.getTotalBudget(currentMonth)
    }

    /**
     * 设置搜索防抖
     */
    private fun setupSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(Constants.SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        repo.search(query)
                    }
                }
                .catch { e ->
                    // 搜索异常处理
                    if (BuildConfig.DEBUG) Log.e(TAG, "搜索失败", e)
                    emit(emptyList())
                }
                .collect { results ->
                    _state.update { it.copy(searchResults = results) }
                }
        }
    }

    // Expense operations
    fun addExpense(expense: ExpenseEntity) {
        logD { "addExpense: amount=${expense.amount}, type=${expense.type}, category=${expense.category}" }
        viewModelScope.launch {
            try {
                if (expense.id == 0L) {
                    val id = repo.insertExpense(expense)
                    logD { "Expense inserted with id: $id" }
                } else {
                    repo.updateExpense(expense)
                    logD { "Expense updated: id=${expense.id}" }
                }
                // 刷新统计数据
                refreshStats()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "保存失败", e)
                updateError("保存失败: ${e.message}")
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                repo.deleteExpense(expense)
                // 刷新统计数据
                refreshStats()
            } catch (e: Exception) {
                updateError("删除失败: ${e.message}")
            }
        }
    }

    // Search - 使用防抖
    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    // Calendar
    fun selectCalendarDate(date: String) {
        _state.update { it.copy(selectedCalendarDate = date) }
    }

    fun setCalendarMonth(calendar: Calendar) {
        _state.update { it.copy(calendarMonthMillis = calendar.timeInMillis) }
    }

    // Stats - 通过更新 Flow 触发响应式加载
    fun setStatsPeriod(period: DateUtils.StatsPeriod) {
        _statsPeriod.value = period
    }

    fun setStatsDate(calendar: Calendar) {
        _statsDateMillis.value = calendar.timeInMillis
    }

    // Budget
    fun saveBudget(amount: Double) {
        val currentMonth = DateUtils.getCurrentMonthId()
        viewModelScope.launch {
            try {
                val budget = BudgetEntity.create(
                    id = _state.value.budget?.id ?: 0,
                    amount = amount,
                    month = currentMonth
                )
                repo.insertBudget(budget)
            } catch (e: Exception) {
                updateError("保存预算失败: ${e.message}")
            }
        }
    }

    // Category
    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch {
            try {
                repo.insertCategory(category)
            } catch (e: Exception) {
                updateError("添加分类失败: ${e.message}")
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            try {
                repo.deleteCategory(category)
            } catch (e: Exception) {
                updateError("删除分类失败: ${e.message}")
            }
        }
    }

    // Error handling
    private fun updateError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = TransactionRepository.getInstance(context)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repo) as T
            }
        }
    }
}
