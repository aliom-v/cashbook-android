package com.example.localexpense.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localexpense.data.CategoryStat
import com.example.localexpense.data.DailyStat
import com.example.localexpense.ui.components.BarChart
import com.example.localexpense.ui.components.PieChart
import com.example.localexpense.ui.theme.CategoryColorOptions
import com.example.localexpense.ui.theme.ExpenseTheme
import com.example.localexpense.util.AmountUtils
import com.example.localexpense.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

// 使用 DateUtils.StatsPeriod 的类型别名
typealias StatsPeriod = DateUtils.StatsPeriod

// 统计类型：支出或收入
enum class StatsType { EXPENSE, INCOME }

/**
 * 统计页面
 *
 * v1.9.6 性能优化：
 * - 使用 derivedStateOf 缓存计算结果，减少重组
 * - 使用 remember 缓存颜色列表和日期格式化结果
 * - 优化分类明细列表渲染
 */
@Composable
fun StatsScreen(
    categoryStats: List<CategoryStat>,
    dailyStats: List<DailyStat>,
    incomeCategoryStats: List<CategoryStat>,
    incomeDailyStats: List<DailyStat>,
    totalExpense: Double,
    totalIncome: Double,
    currentPeriod: DateUtils.StatsPeriod,
    currentDate: Calendar,
    onPeriodChange: (DateUtils.StatsPeriod) -> Unit,
    onDateChange: (Calendar) -> Unit
) {
    // 当前显示的统计类型（支出/收入）
    var statsType by remember { mutableStateOf(StatsType.EXPENSE) }

    // v1.9.6 优化：使用 derivedStateOf 缓存计算结果
    val currentCategoryStats by remember(statsType, categoryStats, incomeCategoryStats) {
        derivedStateOf {
            if (statsType == StatsType.EXPENSE) categoryStats else incomeCategoryStats
        }
    }

    val currentDailyStats by remember(statsType, dailyStats, incomeDailyStats) {
        derivedStateOf {
            if (statsType == StatsType.EXPENSE) dailyStats else incomeDailyStats
        }
    }

    val currentTotal by remember(statsType, totalExpense, totalIncome) {
        derivedStateOf {
            if (statsType == StatsType.EXPENSE) totalExpense else totalIncome
        }
    }

    // 缓存颜色，避免每次重组都重新计算
    val expenseColor = ExpenseTheme.colors.expense
    val incomeColor = ExpenseTheme.colors.income
    val currentColor by remember(statsType) {
        derivedStateOf {
            if (statsType == StatsType.EXPENSE) expenseColor else incomeColor
        }
    }

    // 缓存颜色列表，避免每次重组都重新创建
    val pieColors = remember { CategoryColorOptions.map { Color(it) } }

    // v1.9.6 优化：缓存日期显示文本
    val periodDateText = remember(currentDate, currentPeriod) {
        formatPeriodDate(currentDate, currentPeriod)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Period selector (日/周/月)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            DateUtils.StatsPeriod.entries.forEach { period ->
                val selected = currentPeriod == period
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPeriodChange(period) },
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (period) {
                            DateUtils.StatsPeriod.DAY -> "日"
                            DateUtils.StatsPeriod.WEEK -> "周"
                            DateUtils.StatsPeriod.MONTH -> "月"
                        },
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date navigation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newDate = currentDate.clone() as Calendar
                    when (currentPeriod) {
                        DateUtils.StatsPeriod.DAY -> newDate.add(Calendar.DAY_OF_MONTH, -1)
                        DateUtils.StatsPeriod.WEEK -> newDate.add(Calendar.WEEK_OF_YEAR, -1)
                        DateUtils.StatsPeriod.MONTH -> newDate.add(Calendar.MONTH, -1)
                    }
                    onDateChange(newDate)
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一个")
                }

                Text(
                    text = periodDateText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )

                IconButton(onClick = {
                    val newDate = currentDate.clone() as Calendar
                    when (currentPeriod) {
                        DateUtils.StatsPeriod.DAY -> newDate.add(Calendar.DAY_OF_MONTH, 1)
                        DateUtils.StatsPeriod.WEEK -> newDate.add(Calendar.WEEK_OF_YEAR, 1)
                        DateUtils.StatsPeriod.MONTH -> newDate.add(Calendar.MONTH, 1)
                    }
                    onDateChange(newDate)
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一个")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary cards (支出和收入总览)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "支出",
                amount = totalExpense,
                color = ExpenseTheme.colors.expense,
                selected = statsType == StatsType.EXPENSE,
                onClick = { statsType = StatsType.EXPENSE },
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "收入",
                amount = totalIncome,
                color = ExpenseTheme.colors.income,
                selected = statsType == StatsType.INCOME,
                onClick = { statsType = StatsType.INCOME },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pie chart (根据选择显示支出或收入)
        if (currentCategoryStats.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (statsType == StatsType.EXPENSE) "支出分类" else "收入分类",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PieChart(
                        data = currentCategoryStats,
                        colors = pieColors
                    )
                }
            }
        } else {
            // 无数据提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (statsType == StatsType.EXPENSE) "暂无支出数据" else "暂无收入数据",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bar chart (每日趋势)
        if (currentDailyStats.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (statsType == StatsType.EXPENSE) "支出趋势" else "收入趋势",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BarChart(
                        data = currentDailyStats.takeLast(7),
                        color = currentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category details (分类明细)
        if (currentCategoryStats.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "分类明细",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    currentCategoryStats.forEachIndexed { index, stat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        pieColors.getOrElse(index) { Color.Gray },
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stat.category,
                                modifier = Modifier.weight(1f)
                            )
                            // 显示百分比（使用 AmountUtils 进行精确计算）
                            if (currentTotal > 0) {
                                Text(
                                    text = "%.1f%%".format(AmountUtils.percentage(stat.total, currentTotal)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                text = "¥%.2f".format(stat.total),
                                fontWeight = FontWeight.Medium,
                                color = currentColor
                            )
                        }
                        if (index < currentCategoryStats.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Double,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) BorderStroke(2.dp, color) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "¥%.2f".format(amount),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// 缓存日期格式化器，避免重复创建
private object DateFormatterCache {
    val dayFormat: SimpleDateFormat by lazy { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }
    val weekFormat: SimpleDateFormat by lazy { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val monthFormat: SimpleDateFormat by lazy { SimpleDateFormat("yyyy年MM月", Locale.getDefault()) }
}

private fun formatPeriodDate(calendar: Calendar, period: DateUtils.StatsPeriod): String {
    return when (period) {
        DateUtils.StatsPeriod.DAY -> DateFormatterCache.dayFormat.format(calendar.time)
        DateUtils.StatsPeriod.WEEK -> {
            val weekStart = calendar.clone() as Calendar
            weekStart.firstDayOfWeek = Calendar.MONDAY
            // 找到本周一
            while (weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                weekStart.add(Calendar.DAY_OF_MONTH, -1)
            }
            val weekEnd = weekStart.clone() as Calendar
            weekEnd.add(Calendar.DAY_OF_MONTH, 6)
            "${DateFormatterCache.weekFormat.format(weekStart.time)} - ${DateFormatterCache.weekFormat.format(weekEnd.time)}"
        }
        DateUtils.StatsPeriod.MONTH -> DateFormatterCache.monthFormat.format(calendar.time)
    }
}
