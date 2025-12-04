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
import com.example.localexpense.ui.components.AddExpenseDialog
import com.example.localexpense.ui.components.BudgetProgressBar
import com.example.localexpense.ui.screens.*
import com.example.localexpense.ui.theme.ExpenseTheme
import com.example.localexpense.ui.util.IconUtil
import com.example.localexpense.util.DateUtils
import com.example.localexpense.util.DateUtils.StatsPeriod
import java.text.SimpleDateFormat
import java.util.*

enum class Screen { HOME, STATS, CALENDAR, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
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
    onClearError: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

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
                            IconButton(onClick = { showSearchBar = !showSearchBar }) {
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
                    onClick = { showAddDialog = true },
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
                        showSearchBar = showSearchBar,
                        onSearch = onSearch,
                        onExpenseClick = { expense ->
                            editExpense = expense
                            showAddDialog = true
                        },
                        onDeleteExpense = onDeleteExpense
                    )
                }
                Screen.STATS -> {
                    val (start, end) = DateUtils.getDateRange(state.statsDate, state.statsPeriod)
                    val periodExpense = state.expenses
                        .filter { it.type == "expense" && it.timestamp in start until end }
                        .sumOf { it.amount }
                    val periodIncome = state.expenses
                        .filter { it.type == "income" && it.timestamp in start until end }
                        .sumOf { it.amount }

                    StatsScreen(
                        categoryStats = state.categoryStats,
                        dailyStats = state.dailyStats,
                        totalExpense = periodExpense,
                        totalIncome = periodIncome,
                        currentPeriod = state.statsPeriod,
                        currentDate = state.statsDate,
                        onPeriodChange = onStatsPeriodChange,
                        onDateChange = onStatsDateChange
                    )
                }
                Screen.CALENDAR -> {
                    CalendarScreen(
                        expenses = state.expenses,
                        selectedDate = state.selectedCalendarDate,
                        currentMonth = state.calendarMonth,
                        onDateSelect = onSelectCalendarDate,
                        onMonthChange = onCalendarMonthChange,
                        onExpenseClick = { expense ->
                            editExpense = expense
                            showAddDialog = true
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
                        onOpenAccessibility = onOpenAccessibility
                    )
                }
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog) {
        AddExpenseDialog(
            categories = state.categories,
            onDismiss = {
                showAddDialog = false
                editExpense = null
            },
            onSave = onAddExpense,
            editExpense = editExpense
        )
    }
}

@Composable
private fun HomeContent(
    state: UiState,
    showSearchBar: Boolean,
    onSearch: (String) -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        AnimatedVisibility(
            visible = showSearchBar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
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
            SearchResults(
                results = state.searchResults,
                onExpenseClick = onExpenseClick,
                onDeleteExpense = onDeleteExpense
            )
        } else {
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
                    expenses = state.expenses,
                    onExpenseClick = onExpenseClick,
                    onDeleteExpense = onDeleteExpense
                )
            }
        }
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
            item {
                Text(
                    text = "找到 ${results.size} 条记录",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(results, key = { it.id }) { expense ->
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
    expenses: List<ExpenseEntity>,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    // 使用 remember 缓存分组结果，只有当 expenses 变化时才重新计算
    // 使用 DateUtils 确保线程安全
    val groupedExpenses = remember(expenses) {
        expenses.groupBy { DateUtils.formatDate(it.timestamp) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedExpenses.forEach { (date, dayExpenses) ->
            item {
                // 使用 DateUtils 格式化显示日期
                val displayDate = DateUtils.formatDisplayDate(date)

                val dayTotal = dayExpenses.filter { it.type == "expense" }.sumOf { it.amount }

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
                    Text(
                        text = "支出 ¥%.2f".format(dayTotal),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(dayExpenses, key = { it.id }) { expense ->
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
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
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
                val category = expense.category
                val iconName = when (category) {
                    "餐饮" -> "Restaurant"
                    "购物" -> "ShoppingBag"
                    "交通" -> "DirectionsCar"
                    "娱乐" -> "SportsEsports"
                    "生活" -> "Home"
                    "医疗" -> "LocalHospital"
                    "教育" -> "School"
                    "工资" -> "AccountBalance"
                    "奖金" -> "CardGiftcard"
                    "红包" -> "CardGiftcard"
                    "转账" -> "SwapHoriz"
                    "投资" -> "TrendingUp"
                    "兼职" -> "Work"
                    else -> "MoreHoriz"
                }
                Icon(
                    imageVector = IconUtil.getIcon(iconName),
                    contentDescription = category,
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
                    text = timeFormat.format(Date(expense.timestamp)),
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
