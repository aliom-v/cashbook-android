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
    val categoryStats: List<CategoryStat> = emptyList(),
    val dailyStats: List<DailyStat> = emptyList(),
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

                    _state.value.copy(
                        expenses = expenses,
                        groupedExpenses = grouped,
                        categories = categories,
                        monthlyExpense = monthlyStats.first,
                        monthlyIncome = monthlyStats.second,
                        budget = budget,
                        isLoading = false,
                        error = null
                    )
                }.collect { newState ->
                    _state.value = newState
                }
            } catch (e: Exception) {
                updateError("加载数据失败: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 设置统计数据的响应式加载
     * 同时响应：1. 统计参数变化  2. 数据库记录变化
     */
    private fun setupStats() {
        viewModelScope.launch {
            // 合并统计参数变化和数据库变化，响应式更新统计数据
            combine(
                _statsPeriod,
                _statsDateMillis,
                repo.getAllFlow().map { it.size } // 监听数据库变化，使用 size 作为触发器
            ) { period, dateMillis, _ ->
                val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                val (start, end) = DateUtils.getDateRange(calendar, period)
                Triple(period, dateMillis, Pair(start, end))
            }.flatMapLatest { (period, dateMillis, range) ->
                val (start, end) = range
                combine(
                    repo.getCategoryStats("expense", start, end),
                    repo.getDailyStats("expense", start, end)
                ) { categoryStats, dailyStats ->
                    Triple(Pair(period, dateMillis), categoryStats, dailyStats)
                }
            }.collect { (params, categoryStats, dailyStats) ->
                val (period, dateMillis) = params
                _state.value = _state.value.copy(
                    statsPeriod = period,
                    statsDateMillis = dateMillis,
                    categoryStats = categoryStats,
                    dailyStats = dailyStats
                )
            }
        }
    }

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
                .collect { results ->
                    _state.value = _state.value.copy(searchResults = results)
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
            } catch (e: Exception) {
                updateError("删除失败: ${e.message}")
            }
        }
    }

    // Search - 使用防抖
    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        _searchQuery.value = query
    }

    // Calendar
    fun selectCalendarDate(date: String) {
        _state.value = _state.value.copy(selectedCalendarDate = date)
    }

    fun setCalendarMonth(calendar: Calendar) {
        _state.value = _state.value.copy(calendarMonthMillis = calendar.timeInMillis)
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
                repo.insertBudget(
                    BudgetEntity(
                        id = _state.value.budget?.id ?: 0,
                        amount = amount,
                        month = currentMonth
                    )
                )
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
        _state.value = _state.value.copy(error = message)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
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
