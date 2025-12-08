package com.example.localexpense.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.ui.components.AccessibilityGuideBanner
import com.example.localexpense.ui.components.AccessibilityGuideDialog
import com.example.localexpense.ui.components.AddExpenseDialog
import com.example.localexpense.ui.components.BudgetProgressBar
import com.example.localexpense.ui.components.openAccessibilitySettings
import com.example.localexpense.ui.screens.*
import com.example.localexpense.ui.theme.ExpenseTheme
import com.example.localexpense.ui.util.IconUtil
import com.example.localexpense.util.CategoryNames
import com.example.localexpense.util.DateUtils
import com.example.localexpense.util.DateUtils.StatsPeriod
import java.util.*

enum class Screen { HOME, STATS, CALENDAR, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onIntent: (UserIntent) -> Unit,
    onAddExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onSearch: (String) -> Unit,
    onSelectCalendarDate: (String) -> Unit,
    onCalendarMonthChange: (Calendar) -> Unit,
    onStatsPeriodChange: (StatsPeriod) -> Unit,
    onStatsDateChange: (Calendar) -> Unit,
    onSaveBudget: (Double) -> Unit,
    onAddCategory: (com.example.localexpense.data.CategoryEntity) -> Unit,
    onDeleteCategory: (com.example.localexpense.data.CategoryEntity) -> Unit,
    onOpenAccessibility: () -> Unit,
    onImportData: ((android.net.Uri) -> Unit)? = null,
    onClearAllData: (() -> Unit)? = null,
    onClearError: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    // 使用可复用的 Hook 检测无障碍服务状态
    val isAccessibilityEnabled = com.example.localexpense.util.rememberAccessibilityServiceState()

    // Snackbar 状态用于错误提示
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示错误提示
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (currentScreen) {
                Screen.HOME -> {
                    TopAppBar(
                        title = { Text("本地记账", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { onIntent(UserIntent.ToggleSearchBar) }) {
                                Icon(Icons.Default.Search, "搜索")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                Screen.STATS -> {
                    TopAppBar(
                        title = { Text("统计", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                Screen.CALENDAR -> {
                    TopAppBar(
                        title = { Text("日历", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                Screen.SETTINGS -> {
                    TopAppBar(
                        title = { Text("设置", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "首页") },
                    label = { Text("首页") },
                    selected = currentScreen == Screen.HOME,
                    onClick = { currentScreen = Screen.HOME }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PieChart, "统计") },
                    label = { Text("统计") },
                    selected = currentScreen == Screen.STATS,
                    onClick = { currentScreen = Screen.STATS }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CalendarMonth, "日历") },
                    label = { Text("日历") },
                    selected = currentScreen == Screen.CALENDAR,
                    onClick = { currentScreen = Screen.CALENDAR }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "设置") },
                    label = { Text("设置") },
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = { currentScreen = Screen.SETTINGS }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen == Screen.HOME) {
                FloatingActionButton(
                    onClick = { onIntent(UserIntent.ShowAddDialog) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "添加账单")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.HOME -> {
                    HomeContent(
                        state = state,
                        onIntent = onIntent,
                        onSearch = onSearch,
                        onDeleteExpense = onDeleteExpense,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        onOpenAccessibility = onOpenAccessibility
                    )
                }
                Screen.STATS -> {
                    // 直接使用 ViewModel 中预计算的 categoryStats 合计值
                    // 避免在 UI 层重复遍历整个 expenses 列表
                    val periodExpense = remember(state.categoryStats) {
                        state.categoryStats.sumOf { it.total }
                    }
                    val periodIncome = remember(state.incomeCategoryStats) {
                        state.incomeCategoryStats.sumOf { it.total }
                    }

                    StatsScreen(
                        categoryStats = state.categoryStats,
                        dailyStats = state.dailyStats,
                        incomeCategoryStats = state.incomeCategoryStats,
                        incomeDailyStats = state.incomeDailyStats,
                        totalExpense = periodExpense,
                        totalIncome = periodIncome,
                        currentPeriod = state.statsPeriod,
                        currentDate = Calendar.getInstance().apply { timeInMillis = state.statsDate },
                        onPeriodChange = onStatsPeriodChange,
                        onDateChange = onStatsDateChange
                    )
                }
                Screen.CALENDAR -> {
                    CalendarScreen(
                        expenses = state.expenses,
                        selectedDate = state.selectedCalendarDate,
                        currentMonth = Calendar.getInstance().apply { timeInMillis = state.calendarMonth },
                        onDateSelect = onSelectCalendarDate,
                        onMonthChange = onCalendarMonthChange,
                        onExpenseClick = { expense ->
                            onIntent(UserIntent.ShowEditDialog(expense))
                        }
                    )
                }
                Screen.SETTINGS -> {
                    SettingsScreen(
                        budget = state.budget,
                        categories = state.categories,
                        expenses = state.expenses,
                        onSaveBudget = onSaveBudget,
                        onAddCategory = onAddCategory,
                        onDeleteCategory = onDeleteCategory,
                        onOpenAccessibility = onOpenAccessibility,
                        onImportData = onImportData,
                        onClearAllData = onClearAllData
                    )
                }
            }
        }
    }

    // Add/Edit dialog - 使用 ViewModel 管理的状态
    if (state.showAddDialog) {
        AddExpenseDialog(
            categories = state.categories,
            onDismiss = { onIntent(UserIntent.DismissDialog) },
            onSave = onAddExpense,
            editExpense = state.editingExpense
        )
    }
}

@Composable
private fun HomeContent(
    state: UiState,
    onIntent: (UserIntent) -> Unit,
    onSearch: (String) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    isAccessibilityEnabled: Boolean,
    onOpenAccessibility: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar - 使用 ViewModel 管理的状态
        AnimatedVisibility(
            visible = state.showSearchBar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索商户、备注、分类...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearch("") }) {
                                Icon(Icons.Default.Close, "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // 类型筛选 - 使用 ViewModel 管理的状态
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.searchTypeFilter == null,
                        onClick = { onIntent(UserIntent.SetSearchTypeFilter(null)) },
                        label = { Text("全部") },
                        leadingIcon = if (state.searchTypeFilter == null) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = state.searchTypeFilter == "expense",
                        onClick = { onIntent(UserIntent.SetSearchTypeFilter("expense")) },
                        label = { Text("支出") },
                        leadingIcon = if (state.searchTypeFilter == "expense") {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = state.searchTypeFilter == "income",
                        onClick = { onIntent(UserIntent.SetSearchTypeFilter("income")) },
                        label = { Text("收入") },
                        leadingIcon = if (state.searchTypeFilter == "income") {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        // 加载状态
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // Show search results or normal content
        if (state.searchQuery.isNotEmpty()) {
            // 应用类型筛选 - 使用 ViewModel 管理的状态
            val filteredResults = remember(state.searchResults, state.searchTypeFilter) {
                if (state.searchTypeFilter == null) {
                    state.searchResults
                } else {
                    state.searchResults.filter { it.type == state.searchTypeFilter }
                }
            }

            SearchResults(
                results = filteredResults,
                onExpenseClick = { expense -> onIntent(UserIntent.ShowEditDialog(expense)) },
                onDeleteExpense = onDeleteExpense
            )
        } else {
            // 无障碍服务未开启提示
            if (!isAccessibilityEnabled) {
                AccessibilityTipCard(onOpenAccessibility = onOpenAccessibility)
            }

            // Summary card
            SummaryCard(
                expense = state.monthlyExpense,
                income = state.monthlyIncome,
                budget = state.budget
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Expense list
            if (state.expenses.isEmpty()) {
                EmptyState()
            } else {
                ExpenseList(
                    groupedExpenses = state.groupedExpenses,
                    onExpenseClick = { expense -> onIntent(UserIntent.ShowEditDialog(expense)) },
                    onDeleteExpense = onDeleteExpense
                )
            }
        }
    }
}

@Composable
private fun AccessibilityTipCard(onOpenAccessibility: () -> Unit) {
    var showGuideDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 使用新的引导横幅
    AccessibilityGuideBanner(
        isEnabled = false,  // 这个组件只在未启用时显示
        onDismiss = { /* 用户关闭横幅 */ },
        onOpenSettings = { showGuideDialog = true }
    )

    // 详细引导对话框
    if (showGuideDialog) {
        AccessibilityGuideDialog(
            onDismiss = { showGuideDialog = false },
            onOpenSettings = {
                showGuideDialog = false
                openAccessibilitySettings(context)
            }
        )
    }
}

@Composable
private fun SummaryCard(
    expense: Double,
    income: Double,
    budget: com.example.localexpense.data.BudgetEntity?
) {
    val colors = ExpenseTheme.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "本月概览",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "支出",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "¥%.2f".format(expense),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.expense
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "收入",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "¥%.2f".format(income),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.income
                    )
                }
            }

            // Budget progress
            if (budget != null && budget.amount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                BudgetProgressBar(
                    spent = expense,
                    budget = budget.amount
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<ExpenseEntity>,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "未找到相关账单",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "search_header", contentType = "header") {
                Text(
                    text = "找到 ${results.size} 条记录",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(
                items = results,
                key = { it.id },
                contentType = { "expense" }
            ) { expense ->
                ExpenseItem(
                    expense = expense,
                    onClick = { onExpenseClick(expense) },
                    onDelete = { onDeleteExpense(expense) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无账单记录",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击右下角 + 添加第一笔账单",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ExpenseList(
    groupedExpenses: Map<String, List<ExpenseEntity>>,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    // 预计算日期列表，避免在滚动时重复计算
    val dateList = remember(groupedExpenses) {
        groupedExpenses.keys.toList()
    }

    // 预计算每日统计（在Composable上下文中）
    val dailyStats = remember(groupedExpenses) {
        groupedExpenses.mapValues { (_, expenses) ->
            val expense = expenses.filter { it.type == "expense" }.sumOf { it.amount }
            val income = expenses.filter { it.type == "income" }.sumOf { it.amount }
            expense to income
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dateList.forEach { date ->
            val dayExpenses = groupedExpenses[date] ?: return@forEach
            val (dayExpenseTotal, dayIncomeTotal) = dailyStats[date] ?: (0.0 to 0.0)

            // 日期头部项，使用 date 作为 key，contentType 区分不同类型的项
            item(key = "header_$date", contentType = "header") {
                val displayDate = remember(date) { DateUtils.formatDisplayDate(date) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayDate,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        if (dayIncomeTotal > 0) {
                            Text(
                                text = "收入 ¥%.2f".format(dayIncomeTotal),
                                fontSize = 13.sp,
                                color = ExpenseTheme.colors.income
                            )
                            if (dayExpenseTotal > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        if (dayExpenseTotal > 0) {
                            Text(
                                text = "支出 ¥%.2f".format(dayExpenseTotal),
                                fontSize = 13.sp,
                                color = ExpenseTheme.colors.expense
                            )
                        }
                        if (dayExpenseTotal == 0.0 && dayIncomeTotal == 0.0) {
                            Text(
                                text = "无记录",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(
                items = dayExpenses,
                key = { it.id },
                contentType = { "expense" }
            ) { expense ->
                ExpenseItem(
                    expense = expense,
                    onClick = { onExpenseClick(expense) },
                    onDelete = { onDeleteExpense(expense) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseItem(
    expense: ExpenseEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExpenseTheme.colors
    val isExpense = expense.type == "expense"
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除这笔${if (isExpense) "支出" else "收入"}记录吗？\n金额：¥%.2f".format(expense.amount))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isExpense) colors.expenseBackground else colors.incomeBackground,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val iconName = getCategoryIcon(expense.category)
                Icon(
                    imageVector = IconUtil.getIcon(iconName),
                    contentDescription = expense.category,
                    tint = if (isExpense) colors.expense else colors.income,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Row {
                    Text(
                        text = expense.category,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expense.note.isNotEmpty()) {
                        Text(
                            text = " · ${expense.note}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Amount and time
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isExpense) "-" else "+"}¥%.2f".format(expense.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isExpense) colors.expense else colors.income
                )
                Text(
                    text = DateUtils.formatTime(expense.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = colors.expense) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.expense) }
                    )
                }
            }
        }
    }
}

/**
 * 根据分类名称获取对应图标名称
 */
private fun getCategoryIcon(category: String): String = when (category) {
    CategoryNames.FOOD -> "Restaurant"
    CategoryNames.SHOPPING -> "ShoppingBag"
    CategoryNames.TRANSPORT -> "DirectionsCar"
    CategoryNames.ENTERTAINMENT -> "SportsEsports"
    CategoryNames.LIVING -> "Home"
    CategoryNames.MEDICAL -> "LocalHospital"
    CategoryNames.EDUCATION -> "School"
    CategoryNames.SALARY -> "AccountBalance"
    CategoryNames.BONUS -> "CardGiftcard"
    CategoryNames.RED_PACKET -> "CardGiftcard"
    CategoryNames.TRANSFER -> "SwapHoriz"
    CategoryNames.INVESTMENT -> "TrendingUp"
    CategoryNames.PART_TIME -> "Work"
    else -> "MoreHoriz"
}
