package com.example.localexpense.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.ui.theme.ExpenseTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(
    expenses: List<ExpenseEntity>,
    selectedDate: String,
    currentMonth: Calendar,
    onDateSelect: (String) -> Unit,
    onMonthChange: (Calendar) -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // 使用 remember 缓存分组结果
    val expensesByDate = remember(expenses) {
        expenses.groupBy { dateFormat.format(Date(it.timestamp)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Month navigation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val newMonth = currentMonth.clone() as Calendar
                        newMonth.add(Calendar.MONTH, -1)
                        onMonthChange(newMonth)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月")
                    }

                    Text(
                        text = SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(currentMonth.time),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    IconButton(onClick = {
                        val newMonth = currentMonth.clone() as Calendar
                        newMonth.add(Calendar.MONTH, 1)
                        onMonthChange(newMonth)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Weekday headers
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Calendar grid
                CalendarGrid(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    expensesByDate = expensesByDate,
                    onDateSelect = onDateSelect
                )
            }
        }

        // Selected date expenses
        val selectedExpenses = expensesByDate[selectedDate] ?: emptyList()
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val displayDate = try {
                    val date = dateFormat.parse(selectedDate)
                    SimpleDateFormat("MM月dd日", Locale.getDefault()).format(date!!)
                } catch (e: Exception) { selectedDate }

                Text(
                    text = "$displayDate 账单",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                if (selectedExpenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当日无账单",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val dayExpense = selectedExpenses.filter { it.type == "expense" }.sumOf { it.amount }
                    val dayIncome = selectedExpenses.filter { it.type == "income" }.sumOf { it.amount }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "支出 ¥%.2f".format(dayExpense),
                            fontSize = 13.sp,
                            color = ExpenseTheme.colors.expense
                        )
                        Text(
                            text = "收入 ¥%.2f".format(dayIncome),
                            fontSize = 13.sp,
                            color = ExpenseTheme.colors.income
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedExpenses, key = { it.id }) { expense ->
                            ExpenseListItem(
                                expense = expense,
                                onClick = { onExpenseClick(expense) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: Calendar,
    selectedDate: String,
    expensesByDate: Map<String, List<ExpenseEntity>>,
    onDateSelect: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = dateFormat.format(Date())

    val firstDayOfMonth = currentMonth.clone() as Calendar
    firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
    
    // Adjust to Monday as first day of week
    var firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 2
    if (firstDayOfWeek < 0) firstDayOfWeek += 7
    
    val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

    Column {
        for (week in 0 until (totalCells / 7)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0 until 7) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - firstDayOfWeek + 1

                    if (dayNumber in 1..daysInMonth) {
                        val cal = currentMonth.clone() as Calendar
                        cal.set(Calendar.DAY_OF_MONTH, dayNumber)
                        val dateStr = dateFormat.format(cal.time)
                        val hasExpense = expensesByDate.containsKey(dateStr)
                        val isSelected = dateStr == selectedDate
                        val isToday = dateStr == today

                        CalendarDay(
                            day = dayNumber,
                            isSelected = isSelected,
                            isToday = isToday,
                            hasExpense = hasExpense,
                            modifier = Modifier.weight(1f),
                            onClick = { onDateSelect(dateStr) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasExpense: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (hasExpense) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            if (isSelected) Color.White else ExpenseTheme.colors.expense,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun ExpenseListItem(
    expense: ExpenseEntity,
    onClick: () -> Unit
) {
    val isExpense = expense.type == "expense"
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "${expense.category} · ${timeFormat.format(Date(expense.timestamp))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${if (isExpense) "-" else "+"}¥%.2f".format(expense.amount),
                fontWeight = FontWeight.Bold,
                color = if (isExpense) ExpenseTheme.colors.expense else ExpenseTheme.colors.income
            )
        }
    }
}
