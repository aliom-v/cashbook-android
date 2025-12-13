package com.example.localexpense.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localexpense.data.*
import android.net.Uri
import com.example.localexpense.domain.BudgetUseCases
import com.example.localexpense.domain.CategoryUseCases
import com.example.localexpense.domain.StatisticsUseCases
import com.example.localexpense.domain.TransactionUseCases
import com.example.localexpense.util.Constants
import com.example.localexpense.util.DataExporter
import com.example.localexpense.util.DataImporter
import com.example.localexpense.util.DateUtils
import com.example.localexpense.util.FilterManager
import com.example.localexpense.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * 主界面 ViewModel
 *
 * 状态管理策略：
 * 1. 使用 StateFlow 管理 UI 状态（可观察、可缓存）
 * 2. 使用 Channel 发送一次性事件（Toast、导航等）
 * 3. 使用 UserIntent 处理用户操作（单向数据流）
 * 4. 异常处理统一通过 UseCase 层封装
 *
 * 性能优化：
 * 1. 使用 stateIn 缓存 Flow，避免重复订阅
 * 2. 使用 flatMapLatest 取消旧的查询
 * 3. 使用 debounce 防抖搜索
 * 4. 使用 distinctUntilChanged 避免重复更新
 *
 * 架构优化 (v1.9.4)：
 * - 使用 UseCase 层替代直接依赖 Repository
 * - 提高代码可测试性和解耦性
 */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel @Inject constructor(
    private val transactionUseCases: TransactionUseCases,
    private val statisticsUseCases: StatisticsUseCases,
    private val categoryUseCases: CategoryUseCases,
    private val budgetUseCases: BudgetUseCases,
    application: Application
) : ViewModel() {

    private val appContext: Context = application.applicationContext

    companion object {
        private const val TAG = "MainViewModel"

        // Flow 缓存配置（增加超时时间，减少内存抖动）
        private const val FLOW_STOP_TIMEOUT_MS = 10000L

        // 数据加载自动重试配置
        private const val AUTO_RETRY_MAX_ATTEMPTS = 3
        private const val AUTO_RETRY_DELAY_MS = 2000L
    }

    // UI 状态
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    // 一次性事件通道
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    // 搜索查询 Flow（用于防抖）
    private val _searchQuery = MutableStateFlow("")

    // 统计参数 Flow
    private val _statsPeriod = MutableStateFlow(DateUtils.StatsPeriod.MONTH)
    private val _statsDateMillis = MutableStateFlow(System.currentTimeMillis())

    // 统计刷新触发器
    private val _statsRefreshTrigger = MutableStateFlow(0L)

    // 重试动作
    private var retryAction: (() -> Unit)? = null

    // 自动重试计数器
    private var autoRetryCount = 0

    init {
        Logger.d(TAG) { "ViewModel 初始化" }
        loadData()
        setupSearch()
        setupStats()
    }

    // 筛选条件
    private val _filterCriteria = MutableStateFlow(FilterManager.FilterCriteria.EMPTY)
    val filterCriteria: StateFlow<FilterManager.FilterCriteria> = _filterCriteria.asStateFlow()

    // 批量选择
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // 是否处于批量选择模式
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /**
     * 处理用户意图
     */
    fun handleIntent(intent: UserIntent) {
        Logger.d(TAG) { "处理意图: ${intent::class.simpleName}" }

        when (intent) {
            // 交易操作
            is UserIntent.AddExpense -> addExpense(intent.expense)
            is UserIntent.UpdateExpense -> updateExpense(intent.expense)
            is UserIntent.DeleteExpense -> deleteExpense(intent.expense)

            // 对话框操作
            is UserIntent.ShowAddDialog -> showAddDialog()
            is UserIntent.ShowEditDialog -> showEditDialog(intent.expense)
            is UserIntent.DismissDialog -> dismissDialog()

            // 搜索
            is UserIntent.Search -> search(intent.query)
            is UserIntent.ClearSearch -> clearSearch()
            is UserIntent.ToggleSearchBar -> toggleSearchBar()
            is UserIntent.SetSearchTypeFilter -> setSearchTypeFilter(intent.type)

            // 日历
            is UserIntent.SelectCalendarDate -> selectCalendarDate(intent.date)
            is UserIntent.SetCalendarMonth -> setCalendarMonth(intent.millis)

            // 统计
            is UserIntent.SetStatsPeriod -> setStatsPeriod(intent.period)
            is UserIntent.SetStatsDate -> setStatsDate(intent.millis)
            is UserIntent.RefreshStats -> refreshStats()

            // 预算
            is UserIntent.SaveBudget -> saveBudget(intent.amount)

            // 分类
            is UserIntent.AddCategory -> addCategory(intent.category)
            is UserIntent.DeleteCategory -> deleteCategory(intent.category)

            // 导出
            is UserIntent.ExportData -> exportData(intent.format, intent.criteria)
            is UserIntent.ExportMonthlyReport -> exportMonthlyReport(intent.year, intent.month)

            // 导入
            is UserIntent.ImportData -> importData(intent.uri)
            is UserIntent.ClearAllData -> clearAllData()

            // 筛选
            is UserIntent.ApplyFilter -> applyFilter(intent.criteria)
            is UserIntent.ClearFilter -> clearFilter()
            is UserIntent.QuickFilter -> applyQuickFilter(intent.filterType)

            // 批量操作
            is UserIntent.EnterSelectionMode -> enterSelectionMode()
            is UserIntent.ExitSelectionMode -> exitSelectionMode()
            is UserIntent.ToggleSelection -> toggleSelection(intent.id)
            is UserIntent.SelectAll -> selectAll()
            is UserIntent.DeleteSelected -> deleteSelected()

            // 错误处理
            is UserIntent.ClearError -> clearError()
            is UserIntent.Retry -> retry()
        }
    }

    // ==================== 数据加载 ====================

    /**
     * 加载主要数据（带自动重试）
     */
    private fun loadData() {
        viewModelScope.launch {
            try {
                // 重置重试计数
                autoRetryCount = 0

                // 缓存主要数据流（使用 UseCase）
                val expensesFlow = transactionUseCases.getAllTransactions()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), emptyList())

                val categoriesFlow = categoryUseCases.getAllCategories()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), emptyList())

                // 合并数据流
                combine(
                    expensesFlow,
                    categoriesFlow,
                    loadMonthlyStatsFlow(),
                    loadBudgetFlow()
                ) { expenses, categories, monthlyStats, budget ->
                    DataBundle(expenses, categories, monthlyStats, budget)
                }
                    .catch { e ->
                        Logger.e(TAG, "数据加载异常", e)
                        handleLoadError(e)
                    }
                    .collect { data ->
                        // 数据加载成功，重置重试计数
                        autoRetryCount = 0
                        updateStateWithData(data)
                    }
            } catch (e: Exception) {
                Logger.e(TAG, "数据加载失败", e)
                handleLoadError(e)
            }
        }
    }

    /**
     * 更新状态
     */
    private fun updateStateWithData(data: DataBundle) {
        val grouped = data.expenses.groupBy { DateUtils.formatDate(it.timestamp) }

        _state.update { current ->
            current.copy(
                expenses = data.expenses,
                groupedExpenses = grouped,
                categories = data.categories,
                monthlyExpense = data.monthlyStats.first,
                monthlyIncome = data.monthlyStats.second,
                budget = data.budget,
                loadingState = LoadingState.Success
            )
        }
    }

    /**
     * 处理加载错误（支持自动重试）
     */
    private fun handleLoadError(e: Throwable) {
        retryAction = { loadData() }

        // 检查是否可以自动重试
        if (autoRetryCount < AUTO_RETRY_MAX_ATTEMPTS) {
            autoRetryCount++
            Logger.w(TAG, "数据加载失败，${AUTO_RETRY_DELAY_MS}ms 后自动重试 ($autoRetryCount/$AUTO_RETRY_MAX_ATTEMPTS)")

            // 显示重试中状态
            _state.update {
                it.copy(loadingState = LoadingState.Loading)
            }

            // 延迟后自动重试
            viewModelScope.launch {
                kotlinx.coroutines.delay(AUTO_RETRY_DELAY_MS)
                if (autoRetryCount <= AUTO_RETRY_MAX_ATTEMPTS) {
                    loadData()
                }
            }
        } else {
            // 超过最大重试次数，显示错误
            Logger.e(TAG, "数据加载失败，已达最大重试次数")
            _state.update {
                it.toError("加载数据失败: ${e.message}，请点击重试", retryAction)
            }
        }
    }

    /**
     * 加载月度统计（优化：使用单次查询代替两次独立查询）
     */
    private fun loadMonthlyStatsFlow(): Flow<Pair<Double, Double>> {
        val (monthStart, monthEnd) = DateUtils.getCurrentMonthRange()
        // 使用 UseCase 合并查询，减少数据库访问次数
        return statisticsUseCases.getStatsForRange(monthStart, monthEnd).map { stat ->
            Pair(stat.totalExpense, stat.totalIncome)
        }
    }

    private fun loadBudgetFlow(): Flow<BudgetEntity?> {
        return budgetUseCases.getCurrentMonthBudget()
    }

    // ==================== 搜索 ====================

    /**
     * 设置搜索（带防抖）
     */
    private fun setupSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(Constants.SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .onEach { query ->
                    if (query.isNotBlank()) {
                        _state.update { it.copy(isSearching = true) }
                    }
                }
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        transactionUseCases.searchTransactions(query)
                    }
                }
                .catch { e ->
                    Logger.e(TAG, "搜索失败", e)
                    emit(emptyList())
                }
                .collect { results ->
                    _state.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false
                        )
                    }
                }
        }
    }

    private fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    private fun clearSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        _searchQuery.value = ""
    }

    // ==================== 对话框操作 ====================

    private fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, editingExpense = null) }
    }

    private fun showEditDialog(expense: ExpenseEntity) {
        _state.update { it.copy(showAddDialog = true, editingExpense = expense) }
    }

    private fun dismissDialog() {
        _state.update { it.copy(showAddDialog = false, editingExpense = null) }
    }

    private fun toggleSearchBar() {
        _state.update { it.copy(showSearchBar = !it.showSearchBar) }
    }

    private fun setSearchTypeFilter(type: String?) {
        _state.update { it.copy(searchTypeFilter = type) }
    }

    // ==================== 统计 ====================

    /**
     * 设置统计数据的响应式加载
     */
    private fun setupStats() {
        viewModelScope.launch {
            combine(
                _statsPeriod,
                _statsDateMillis,
                _statsRefreshTrigger
            ) { period, dateMillis, _ ->
                val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                val (start, end) = DateUtils.getDateRange(calendar, period)
                StatsParams(period, dateMillis, start, end)
            }
                .flatMapLatest { params ->
                    combine(
                        statisticsUseCases.getCategoryStats("expense", params.start, params.end),
                        statisticsUseCases.getDailyStats("expense", params.start, params.end),
                        statisticsUseCases.getCategoryStats("income", params.start, params.end),
                        statisticsUseCases.getDailyStats("income", params.start, params.end)
                    ) { expenseCat, expenseDaily, incomeCat, incomeDaily ->
                        StatsBundle(params, expenseCat, expenseDaily, incomeCat, incomeDaily)
                    }
                }
                .catch { e ->
                    Logger.e(TAG, "统计数据加载失败", e)
                    sendEvent(UiEvent.ShowToast("统计加载失败", isError = true))
                }
                .collect { stats ->
                    _state.update { current ->
                        current.copy(
                            statsPeriod = stats.params.period,
                            statsDateMillis = stats.params.dateMillis,
                            categoryStats = stats.expenseCategoryStats,
                            dailyStats = stats.expenseDailyStats,
                            incomeCategoryStats = stats.incomeCategoryStats,
                            incomeDailyStats = stats.incomeDailyStats
                        )
                    }
                }
        }
    }

    private fun setStatsPeriod(period: DateUtils.StatsPeriod) {
        _statsPeriod.value = period
    }

    private fun setStatsDate(millis: Long) {
        _statsDateMillis.value = millis
    }

    private fun refreshStats() {
        _statsRefreshTrigger.value = System.currentTimeMillis()
    }

    // ==================== 交易操作 ====================

    private fun addExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Saving) }

            val result = transactionUseCases.addTransaction(expense)

            result.fold(
                onSuccess = { id ->
                    Logger.d(TAG) { "交易保存成功: id=$id" }
                    _state.update { it.endOperation() }
                    sendEvent(UiEvent.OperationSuccess("保存成功"))
                    refreshStats()
                },
                onFailure = { e ->
                    Logger.e(TAG, "交易保存失败", e)
                    _state.update { it.operationError("保存失败: ${e.message}") }
                    sendEvent(UiEvent.ShowToast("保存失败", isError = true))
                }
            )
        }
    }

    private fun updateExpense(expense: ExpenseEntity) {
        addExpense(expense) // 复用添加逻辑
    }

    private fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Deleting) }

            val result = transactionUseCases.deleteTransaction(expense)

            result.fold(
                onSuccess = {
                    Logger.d(TAG) { "交易删除成功: id=${expense.id}" }
                    _state.update { it.endOperation() }
                    // 优化：使用较长的 Snackbar 持续时间，让用户有足够时间点击撤销
                    sendEvent(UiEvent.ShowSnackbar(
                        message = "已删除",
                        actionLabel = "撤销",
                        action = { addExpense(expense.copy(id = 0)) },
                        duration = SnackbarDurationType.LONG
                    ))
                    refreshStats()
                },
                onFailure = { e ->
                    Logger.e(TAG, "交易删除失败", e)
                    _state.update { it.operationError("删除失败: ${e.message}") }
                    sendEvent(UiEvent.ShowToast("删除失败", isError = true))
                }
            )
        }
    }

    // ==================== 日历 ====================

    private fun selectCalendarDate(date: String) {
        _state.update { it.copy(selectedCalendarDate = date) }
    }

    private fun setCalendarMonth(millis: Long) {
        _state.update { it.copy(calendarMonthMillis = millis) }
    }

    // ==================== 预算 ====================

    private fun saveBudget(amount: Double) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Saving) }

            val result = budgetUseCases.saveCurrentMonthBudget(
                amount = amount,
                existingBudgetId = _state.value.budget?.id
            )

            result.fold(
                onSuccess = {
                    _state.update { it.endOperation() }
                    sendEvent(UiEvent.OperationSuccess("预算已保存"))
                },
                onFailure = { e ->
                    Logger.e(TAG, "预算保存失败", e)
                    _state.update { it.operationError("保存预算失败") }
                    sendEvent(UiEvent.ShowToast("保存预算失败", isError = true))
                }
            )
        }
    }

    // ==================== 分类 ====================

    private fun addCategory(category: CategoryEntity) {
        viewModelScope.launch {
            val result = categoryUseCases.addCategory(category)

            result.fold(
                onSuccess = {
                    sendEvent(UiEvent.OperationSuccess("分类已添加"))
                },
                onFailure = { e ->
                    Logger.e(TAG, "添加分类失败", e)
                    sendEvent(UiEvent.ShowToast("添加分类失败", isError = true))
                }
            )
        }
    }

    private fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            val result = categoryUseCases.deleteCategory(category)

            result.fold(
                onSuccess = {
                    sendEvent(UiEvent.OperationSuccess("分类已删除"))
                },
                onFailure = { e ->
                    Logger.e(TAG, "删除分类失败", e)
                    sendEvent(UiEvent.ShowToast("删除分类失败", isError = true))
                }
            )
        }
    }

    // ==================== 错误处理 ====================

    private fun clearError() {
        _state.update { it.copy(loadingState = LoadingState.Success, operationState = OperationState.Idle) }
    }

    private fun retry() {
        retryAction?.invoke()
    }

    // ==================== 导出功能 ====================

    /**
     * 导出数据
     */
    private fun exportData(
        format: DataExporter.ExportFormat,
        criteria: FilterManager.FilterCriteria?
    ) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Exporting) }

            try {
                // 获取要导出的数据
                val transactions = if (criteria != null && criteria.hasAnyFilter()) {
                    FilterManager.filter(_state.value.expenses, criteria).transactions
                } else {
                    _state.value.expenses
                }

                if (transactions.isEmpty()) {
                    _state.update { it.endOperation() }
                    sendEvent(UiEvent.ShowToast("没有可导出的数据", isError = true))
                    return@launch
                }

                val options = DataExporter.ExportOptions(format = format)
                val result = DataExporter.exportTransactions(appContext, transactions, options)

                when (result) {
                    is DataExporter.ExportResult.Success -> {
                        _state.update { it.endOperation() }
                        sendEvent(UiEvent.ExportSuccess(
                            filePath = result.filePath,
                            recordCount = result.recordCount
                        ))
                    }
                    is DataExporter.ExportResult.Failure -> {
                        _state.update { it.operationError(result.error) }
                        sendEvent(UiEvent.ShowToast(result.error, isError = true))
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "导出失败", e)
                _state.update { it.operationError("导出失败: ${e.message}") }
                sendEvent(UiEvent.ShowToast("导出失败", isError = true))
            }
        }
    }

    /**
     * 导出月度报表
     */
    private fun exportMonthlyReport(year: Int, month: Int) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Exporting) }

            try {
                val criteria = FilterManager.monthCriteria(year, month)
                val transactions = FilterManager.filter(_state.value.expenses, criteria).transactions

                if (transactions.isEmpty()) {
                    _state.update { it.endOperation() }
                    sendEvent(UiEvent.ShowToast("该月份没有数据", isError = true))
                    return@launch
                }

                val result = DataExporter.exportMonthlyReport(appContext, transactions, year, month)

                when (result) {
                    is DataExporter.ExportResult.Success -> {
                        _state.update { it.endOperation() }
                        sendEvent(UiEvent.ExportSuccess(
                            filePath = result.filePath,
                            recordCount = result.recordCount
                        ))
                    }
                    is DataExporter.ExportResult.Failure -> {
                        _state.update { it.operationError(result.error) }
                        sendEvent(UiEvent.ShowToast(result.error, isError = true))
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "导出月度报表失败", e)
                _state.update { it.operationError("导出失败: ${e.message}") }
                sendEvent(UiEvent.ShowToast("导出失败", isError = true))
            }
        }
    }

    // ==================== 导入功能 ====================

    /**
     * 导入数据
     */
    private fun importData(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Saving) }

            try {
                val result = DataImporter.importFromUri(appContext, uri)

                when (result) {
                    is DataImporter.ImportResult.Success -> {
                        if (result.transactions.isEmpty()) {
                            _state.update { it.endOperation() }
                            sendEvent(UiEvent.ShowToast("没有可导入的数据", isError = true))
                            return@launch
                        }

                        // 验证数据
                        val validation = DataImporter.validateImportData(result.transactions)
                        if (validation.warnings.isNotEmpty()) {
                            Logger.w(TAG, "导入数据警告: ${validation.warnings}")
                        }

                        // 使用 UseCase 批量插入
                        val batchResult = transactionUseCases.addTransactions(result.transactions)

                        _state.update { it.endOperation() }
                        refreshStats()

                        sendEvent(UiEvent.ImportSuccess(
                            importedCount = batchResult.successCount,
                            skippedCount = result.skippedCount,
                            failedCount = result.failedCount + batchResult.failCount
                        ))
                    }
                    is DataImporter.ImportResult.Failure -> {
                        _state.update { it.operationError(result.error) }
                        sendEvent(UiEvent.ShowToast(result.error, isError = true))
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "导入失败", e)
                _state.update { it.operationError("导入失败: ${e.message}") }
                sendEvent(UiEvent.ShowToast("导入失败", isError = true))
            }
        }
    }

    /**
     * 清除所有数据
     */
    private fun clearAllData() {
        sendEvent(UiEvent.ConfirmAction(
            title = "清除所有数据",
            message = "确定要删除所有记账数据吗？此操作不可恢复！",
            onConfirm = {
                viewModelScope.launch {
                    _state.update { it.startOperation(OperationState.Deleting) }

                    val result = transactionUseCases.deleteAllTransactions()
                    result.fold(
                        onSuccess = {
                            _state.update { it.endOperation() }
                            refreshStats()
                            sendEvent(UiEvent.ShowToast("所有数据已清除"))
                        },
                        onFailure = { e ->
                            Logger.e(TAG, "清除数据失败", e)
                            _state.update { it.operationError("清除失败: ${e.message}") }
                            sendEvent(UiEvent.ShowToast("清除失败", isError = true))
                        }
                    )
                }
            }
        ))
    }

    // ==================== 筛选功能 ====================

    /**
     * 应用筛选条件
     */
    private fun applyFilter(criteria: FilterManager.FilterCriteria) {
        _filterCriteria.value = criteria
        val result = FilterManager.filter(_state.value.expenses, criteria)

        _state.update { current ->
            current.copy(
                filteredExpenses = result.transactions,
                filterResult = result,
                isFiltering = criteria.hasAnyFilter()
            )
        }

        if (criteria.hasAnyFilter()) {
            sendEvent(UiEvent.ShowToast("已筛选 ${result.totalCount} 条记录"))
        }
    }

    /**
     * 清除筛选
     */
    private fun clearFilter() {
        _filterCriteria.value = FilterManager.FilterCriteria.EMPTY
        _state.update { current ->
            current.copy(
                filteredExpenses = emptyList(),
                filterResult = null,
                isFiltering = false
            )
        }
    }

    /**
     * 快捷筛选
     */
    private fun applyQuickFilter(filterType: QuickFilterType) {
        val criteria = when (filterType) {
            QuickFilterType.TODAY -> FilterManager.todayCriteria()
            QuickFilterType.THIS_WEEK -> FilterManager.thisWeekCriteria()
            QuickFilterType.THIS_MONTH -> FilterManager.thisMonthCriteria()
            QuickFilterType.LARGE_EXPENSE -> FilterManager.largeExpenseCriteria(100.0)
            QuickFilterType.EXPENSE_ONLY -> FilterManager.FilterCriteria(
                transactionType = FilterManager.TransactionTypeFilter.EXPENSE
            )
            QuickFilterType.INCOME_ONLY -> FilterManager.FilterCriteria(
                transactionType = FilterManager.TransactionTypeFilter.INCOME
            )
        }
        applyFilter(criteria)
    }

    /**
     * 获取筛选后的交易列表
     * 修复：筛选结果为空时返回空列表而不是全部数据
     */
    fun getDisplayExpenses(): List<ExpenseEntity> {
        val currentState = _state.value
        return when {
            // 筛选模式：返回筛选结果（包括空列表）
            currentState.isFiltering -> currentState.filteredExpenses
            // 非筛选模式：返回全部数据
            else -> currentState.expenses
        }
    }

    // ==================== 批量操作 ====================

    /**
     * 进入选择模式
     */
    private fun enterSelectionMode() {
        _isSelectionMode.value = true
        _selectedIds.value = emptySet()
    }

    /**
     * 退出选择模式
     */
    private fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    /**
     * 切换选择状态
     */
    private fun toggleSelection(id: Long) {
        _selectedIds.update { current ->
            if (id in current) {
                current - id
            } else {
                current + id
            }
        }
    }

    /**
     * 全选
     */
    private fun selectAll() {
        val allIds = getDisplayExpenses().map { it.id }.toSet()
        _selectedIds.value = allIds
    }

    /**
     * 删除选中的交易（使用事务保护的批量删除）
     * 优化：添加确认对话框和撤销功能
     */
    private fun deleteSelected() {
        val selectedIdList = _selectedIds.value.toList()
        if (selectedIdList.isEmpty()) {
            sendEvent(UiEvent.ShowToast("请先选择要删除的记录", isError = true))
            return
        }

        // 保存要删除的交易记录，用于撤销
        val transactionsToDelete = _state.value.expenses.filter { it.id in selectedIdList }
        val count = transactionsToDelete.size

        // 显示确认对话框
        sendEvent(UiEvent.ConfirmAction(
            title = "确认删除",
            message = "确定要删除选中的 $count 条记录吗？",
            onConfirm = {
                performBatchDelete(selectedIdList, transactionsToDelete)
            }
        ))
    }

    /**
     * 执行批量删除操作
     */
    private fun performBatchDelete(ids: List<Long>, transactionsBackup: List<ExpenseEntity>) {
        viewModelScope.launch {
            _state.update { it.startOperation(OperationState.Deleting) }

            try {
                // 使用 UseCase 事务保护的批量删除
                val result = transactionUseCases.deleteTransactions(ids)

                _state.update { it.endOperation() }
                exitSelectionMode()
                refreshStats()

                if (result.hasErrors) {
                    sendEvent(UiEvent.ShowToast(
                        "删除失败: ${result.errors.firstOrNull() ?: "未知错误"}",
                        isError = true
                    ))
                } else {
                    // 显示带撤销功能的 Snackbar（使用较长持续时间）
                    sendEvent(UiEvent.ShowSnackbar(
                        message = "已删除 ${result.successCount} 条记录",
                        actionLabel = "撤销",
                        action = {
                            // 恢复被删除的记录
                            restoreDeletedTransactions(transactionsBackup)
                        },
                        duration = SnackbarDurationType.LONG
                    ))
                }

            } catch (e: Exception) {
                Logger.e(TAG, "批量删除失败", e)
                _state.update { it.operationError("批量删除失败: ${e.message}") }
                sendEvent(UiEvent.ShowToast("批量删除失败", isError = true))
            }
        }
    }

    /**
     * 恢复被删除的交易记录（撤销操作）
     */
    private fun restoreDeletedTransactions(transactions: List<ExpenseEntity>) {
        viewModelScope.launch {
            // 使用恢复中状态
            _state.update { it.startOperation(OperationState.Restoring) }

            try {
                // 使用 UseCase 批量插入恢复记录（使用 copy(id=0) 创建新记录）
                val restoredEntities = transactions.map { it.copy(id = 0) }
                val result = transactionUseCases.addTransactions(restoredEntities)

                _state.update { it.endOperation() }

                if (result.hasErrors) {
                    sendEvent(UiEvent.ShowToast(
                        "部分恢复失败: 成功 ${result.successCount} 条，失败 ${result.failCount} 条",
                        isError = true
                    ))
                } else {
                    sendEvent(UiEvent.ShowToast("已恢复 ${result.successCount} 条记录"))
                }
                refreshStats()
            } catch (e: Exception) {
                Logger.e(TAG, "恢复记录失败", e)
                _state.update { it.endOperation() }
                sendEvent(UiEvent.ShowToast("恢复失败: ${e.message}", isError = true))
            }
        }
    }

    // ==================== 事件发送 ====================

    private fun sendEvent(event: UiEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    // ==================== 数据类 ====================

    private data class DataBundle(
        val expenses: List<ExpenseEntity>,
        val categories: List<CategoryEntity>,
        val monthlyStats: Pair<Double, Double>,
        val budget: BudgetEntity?
    )

    private data class StatsParams(
        val period: DateUtils.StatsPeriod,
        val dateMillis: Long,
        val start: Long,
        val end: Long
    )

    private data class StatsBundle(
        val params: StatsParams,
        val expenseCategoryStats: List<CategoryStat>,
        val expenseDailyStats: List<DailyStat>,
        val incomeCategoryStats: List<CategoryStat>,
        val incomeDailyStats: List<DailyStat>
    )

    // ==================== 生命周期 ====================

    override fun onCleared() {
        super.onCleared()
        Logger.d(TAG) { "ViewModel 销毁" }
    }
}
