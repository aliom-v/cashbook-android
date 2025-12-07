package com.example.localexpense.ui

import com.example.localexpense.data.*
import com.example.localexpense.util.DataExporter
import com.example.localexpense.util.DateUtils
import com.example.localexpense.util.FilterManager

/**
 * UI 状态管理
 *
 * 设计原则：
 * 1. 不可变状态 - 所有状态更新通过 copy() 创建新实例
 * 2. 单一数据源 - 所有 UI 状态集中管理
 * 3. 状态分离 - 数据状态、加载状态、错误状态分离
 * 4. 事件驱动 - UI 事件通过 UiEvent 传递
 */

// 类型别名，保持向后兼容
typealias UiState = MainUiState

/**
 * 主界面 UI 状态
 */
data class MainUiState(
    // 数据状态
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

    // 搜索状态
    val searchQuery: String = "",
    val searchResults: List<ExpenseEntity> = emptyList(),
    val isSearching: Boolean = false,

    // 日历状态
    val selectedCalendarDate: String = DateUtils.getTodayString(),
    val calendarMonthMillis: Long = System.currentTimeMillis(),

    // 统计筛选状态
    val statsPeriod: DateUtils.StatsPeriod = DateUtils.StatsPeriod.MONTH,
    val statsDateMillis: Long = System.currentTimeMillis(),

    // 筛选状态
    val isFiltering: Boolean = false,
    val filteredExpenses: List<ExpenseEntity> = emptyList(),
    val filterResult: FilterManager.FilterResult? = null,

    // 加载状态
    val loadingState: LoadingState = LoadingState.Loading,

    // 操作状态（用于显示操作中的加载指示器）
    val operationState: OperationState = OperationState.Idle
) {
    // 便捷属性
    val isLoading: Boolean get() = loadingState is LoadingState.Loading
    val hasError: Boolean get() = loadingState is LoadingState.Error
    val errorMessage: String? get() = (loadingState as? LoadingState.Error)?.message

    val isOperating: Boolean get() = operationState !is OperationState.Idle
    val operationMessage: String? get() = when (operationState) {
        is OperationState.Saving -> "保存中..."
        is OperationState.Deleting -> "删除中..."
        is OperationState.Exporting -> "导出中..."
        else -> null
    }

    // 显示用的交易列表（考虑筛选）
    val displayExpenses: List<ExpenseEntity>
        get() = if (isFiltering && filteredExpenses.isNotEmpty()) filteredExpenses else expenses

    // 筛选结果统计
    val filterSummary: String?
        get() = filterResult?.let { "筛选结果: ${it.totalCount} 条" }

    // 向后兼容的属性
    val error: String? get() = errorMessage
    val statsDate: Long get() = statsDateMillis
    val calendarMonth: Long get() = calendarMonthMillis

    // 预算使用率
    val budgetUsagePercent: Float
        get() = budget?.let {
            if (it.amount > 0) (monthlyExpense / it.amount * 100).toFloat().coerceIn(0f, 100f) else 0f
        } ?: 0f

    // 预算剩余
    val budgetRemaining: Double
        get() = budget?.let { (it.amount - monthlyExpense).coerceAtLeast(0.0) } ?: 0.0

    // 本月净收入
    val monthlyNet: Double get() = monthlyIncome - monthlyExpense
}

/**
 * 加载状态
 */
sealed class LoadingState {
    object Loading : LoadingState()
    object Success : LoadingState()
    data class Error(val message: String, val retryAction: (() -> Unit)? = null) : LoadingState()
}

/**
 * 操作状态
 */
sealed class OperationState {
    object Idle : OperationState()
    object Saving : OperationState()
    object Deleting : OperationState()
    object Exporting : OperationState()
    data class Error(val message: String) : OperationState()
}

/**
 * UI 事件（一次性事件，用于 Toast、导航等）
 */
sealed class UiEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : UiEvent()
    data class ShowSnackbar(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null) : UiEvent()
    object NavigateBack : UiEvent()
    data class NavigateToDetail(val expenseId: Long) : UiEvent()
    object RefreshComplete : UiEvent()
    data class ExportComplete(val filePath: String) : UiEvent()
    data class ExportSuccess(val filePath: String, val recordCount: Int) : UiEvent()
    data class ImportSuccess(val importedCount: Int, val skippedCount: Int, val failedCount: Int) : UiEvent()
    data class OperationSuccess(val message: String) : UiEvent()
    data class ConfirmAction(val title: String, val message: String, val onConfirm: () -> Unit) : UiEvent()
}

/**
 * 用户意图（用于 ViewModel 处理）
 */
sealed class UserIntent {
    // 交易操作
    data class AddExpense(val expense: ExpenseEntity) : UserIntent()
    data class UpdateExpense(val expense: ExpenseEntity) : UserIntent()
    data class DeleteExpense(val expense: ExpenseEntity) : UserIntent()

    // 搜索
    data class Search(val query: String) : UserIntent()
    object ClearSearch : UserIntent()

    // 日历
    data class SelectCalendarDate(val date: String) : UserIntent()
    data class SetCalendarMonth(val millis: Long) : UserIntent()

    // 统计
    data class SetStatsPeriod(val period: DateUtils.StatsPeriod) : UserIntent()
    data class SetStatsDate(val millis: Long) : UserIntent()
    object RefreshStats : UserIntent()

    // 预算
    data class SaveBudget(val amount: Double) : UserIntent()

    // 分类
    data class AddCategory(val category: CategoryEntity) : UserIntent()
    data class DeleteCategory(val category: CategoryEntity) : UserIntent()

    // 导出
    data class ExportData(
        val format: DataExporter.ExportFormat,
        val criteria: FilterManager.FilterCriteria? = null
    ) : UserIntent()
    data class ExportMonthlyReport(val year: Int, val month: Int) : UserIntent()

    // 导入
    data class ImportData(val uri: android.net.Uri) : UserIntent()
    object ClearAllData : UserIntent()

    // 筛选
    data class ApplyFilter(val criteria: FilterManager.FilterCriteria) : UserIntent()
    object ClearFilter : UserIntent()
    data class QuickFilter(val filterType: QuickFilterType) : UserIntent()

    // 批量操作
    object EnterSelectionMode : UserIntent()
    object ExitSelectionMode : UserIntent()
    data class ToggleSelection(val id: Long) : UserIntent()
    object SelectAll : UserIntent()
    object DeleteSelected : UserIntent()

    // 错误处理
    object ClearError : UserIntent()
    object Retry : UserIntent()
}

/**
 * 快捷筛选类型
 */
enum class QuickFilterType {
    TODAY,          // 今日
    THIS_WEEK,      // 本周
    THIS_MONTH,     // 本月
    LARGE_EXPENSE,  // 大额支出
    EXPENSE_ONLY,   // 仅支出
    INCOME_ONLY     // 仅收入
}

/**
 * 状态更新辅助扩展
 */
fun MainUiState.toLoading() = copy(loadingState = LoadingState.Loading)

fun MainUiState.toSuccess() = copy(loadingState = LoadingState.Success)

fun MainUiState.toError(message: String, retryAction: (() -> Unit)? = null) =
    copy(loadingState = LoadingState.Error(message, retryAction))

fun MainUiState.startOperation(state: OperationState) = copy(operationState = state)

fun MainUiState.endOperation() = copy(operationState = OperationState.Idle)

fun MainUiState.operationError(message: String) = copy(operationState = OperationState.Error(message))
