package com.example.localexpense.ui.screens

import androidx.compose.animation.*
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
import com.example.localexpense.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

// 使用 DateUtils.StatsPeriod 的类型别名
typealias StatsPeriod = DateUtils.StatsPeriod

@Composable
fun StatsScreen(
    categoryStats: List<CategoryStat>,
    dailyStats: List<DailyStat>,
    totalExpense: Double,
    totalIncome: Double,
    currentPeriod: DateUtils.StatsPeriod,
    currentDate: Calendar,
    onPeriodChange: (DateUtils.StatsPeriod) -> Unit,
    onDateChange: (Calendar) -> Unit
) {
    // 使用 Theme 中定义的分类颜色
    val pieColors = CategoryColorOptions.map { Color(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Period selector
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
                    text = formatPeriodDate(currentDate, currentPeriod),
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

        // Summary cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "支出",
                amount = totalExpense,
                color = ExpenseTheme.colors.expense,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "收入",
                amount = totalIncome,
                color = ExpenseTheme.colors.income,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pie chart
        if (categoryStats.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "支出分类",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PieChart(
                        data = categoryStats,
                        colors = pieColors
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bar chart
        if (dailyStats.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "每日趋势",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BarChart(
                        data = dailyStats.takeLast(7),
                        color = ExpenseTheme.colors.expense
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category details
        if (categoryStats.isNotEmpty()) {
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
                    categoryStats.forEachIndexed { index, stat ->
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
                            Text(
                                text = "¥%.2f".format(stat.total),
                                fontWeight = FontWeight.Medium,
                                color = ExpenseTheme.colors.expense
                            )
                        }
                        if (index < categoryStats.lastIndex) {
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = color
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

private fun formatPeriodDate(calendar: Calendar, period: DateUtils.StatsPeriod): String {
    return when (period) {
        DateUtils.StatsPeriod.DAY -> SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(calendar.time)
        DateUtils.StatsPeriod.WEEK -> {
            val weekStart = calendar.clone() as Calendar
            weekStart.firstDayOfWeek = Calendar.MONDAY
            // 找到本周一
            while (weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                weekStart.add(Calendar.DAY_OF_MONTH, -1)
            }
            val weekEnd = weekStart.clone() as Calendar
            weekEnd.add(Calendar.DAY_OF_MONTH, 6)  // 修复：使用 DAY_OF_MONTH
            val format = SimpleDateFormat("MM/dd", Locale.getDefault())
            "${format.format(weekStart.time)} - ${format.format(weekEnd.time)}"
        }
        DateUtils.StatsPeriod.MONTH -> SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(calendar.time)
    }
}
