package com.example.localexpense.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.ui.theme.ExpenseTheme
import com.example.localexpense.ui.util.IconUtil
import com.example.localexpense.util.Constants
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
    editExpense: ExpenseEntity? = null
) {
    var isExpense by remember { mutableStateOf(editExpense?.type != "income") }
    var amount by remember { mutableStateOf(editExpense?.amount?.toString() ?: "") }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var merchant by remember { mutableStateOf(editExpense?.merchant ?: "") }
    var note by remember { mutableStateOf(editExpense?.note ?: "") }
    var selectedDate by remember { mutableStateOf(editExpense?.timestamp ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 使用 Theme 颜色
    val expenseColor = ExpenseTheme.colors.expense
    val incomeColor = ExpenseTheme.colors.income

    val filteredCategories = remember(categories, isExpense) {
        categories.filter { it.type == if (isExpense) "expense" else "income" }
    }

    // 当类型切换或分类列表变化时，更新选中的分类
    // 使用 filteredCategories 作为依赖，确保分类列表已过滤后再更新选中状态
    LaunchedEffect(filteredCategories, isExpense) {
        if (filteredCategories.isNotEmpty()) {
            // 编辑模式下尝试匹配原分类
            val matchedCategory = editExpense?.let { exp ->
                if ((exp.type == "expense") == isExpense) {
                    filteredCategories.find { it.name == exp.category }
                } else null
            }
            // 只有当当前选中的分类不在过滤后的列表中时才更新
            if (selectedCategory == null || selectedCategory !in filteredCategories) {
                selectedCategory = matchedCategory ?: filteredCategories.first()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editExpense != null) "编辑账单" else "添加账单",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = "支出",
                        selected = isExpense,
                        color = expenseColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        isExpense = true
                        // LaunchedEffect 会处理分类更新
                    }
                    TabButton(
                        text = "收入",
                        selected = !isExpense,
                        color = incomeColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        isExpense = false
                        // LaunchedEffect 会处理分类更新
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        // 过滤非法字符，只保留数字和一个小数点
                        val filtered = newValue.filter { c -> c.isDigit() || c == '.' }
                        // 确保只有一个小数点，且小数位不超过2位
                        val parts = filtered.split('.')
                        amount = when {
                            parts.size <= 1 -> filtered
                            parts.size == 2 -> "${parts[0]}.${parts[1].take(2)}"
                            else -> "${parts[0]}.${parts.drop(1).joinToString("").take(2)}"
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category grid
                Text("分类", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCategories) { category ->
                        CategoryItem(
                            category = category,
                            selected = selectedCategory?.id == category.id,
                            onClick = { selectedCategory = category }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Merchant input (限制长度)
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { if (it.length <= Constants.MAX_MERCHANT_NAME_LENGTH) merchant = it },
                    label = { Text("商户/来源") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = if (merchant.length >= Constants.MAX_MERCHANT_NAME_LENGTH - 5) {
                        { Text("${merchant.length}/${Constants.MAX_MERCHANT_NAME_LENGTH}") }
                    } else null
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Note input (限制长度)
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= Constants.MAX_NOTE_LENGTH) note = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = if (note.length >= Constants.MAX_NOTE_LENGTH - 10) {
                        { Text("${note.length}/${Constants.MAX_NOTE_LENGTH}") }
                    } else null
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Date selector
                val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("日期", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(dateFormat.format(Date(selectedDate)))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                // 验证金额：大于0且不超过上限（防止误输入）
                val maxAmount = 1000000.0  // 100万上限
                val amountValue = amount.toDoubleOrNull()
                val isAmountValid = amountValue != null && amountValue > 0 && amountValue <= maxAmount
                val amountError = when {
                    amountValue == null -> null
                    amountValue <= 0 -> "金额必须大于0"
                    amountValue > maxAmount -> "金额不能超过100万"
                    else -> null
                }

                // 显示金额错误提示
                if (amountError != null && amount.isNotEmpty()) {
                    Text(
                        text = amountError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        if (amountValue == null || amountValue <= 0 || amountValue > maxAmount) {
                            // 金额无效，不保存
                            return@Button
                        }
                        
                        onSave(
                            ExpenseEntity(
                                id = editExpense?.id ?: 0L,
                                amount = amountValue,
                                merchant = merchant.ifBlank { selectedCategory?.name ?: "未知" },
                                type = if (isExpense) "expense" else "income",
                                timestamp = selectedDate,
                                channel = "Manual",
                                category = selectedCategory?.name ?: "其他",
                                categoryId = selectedCategory?.id ?: 0L,
                                note = note
                            )
                        )
                        onDismiss()
                    },
                    enabled = isAmountValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpense) expenseColor else incomeColor
                    )
                ) {
                    Text("保存", fontSize = 16.sp)
                }
            }
        }
    }

    if (showDatePicker) {
        // 提取当前选中日期的年月日
        val currentCal = remember(selectedDate) {
            Calendar.getInstance().apply { timeInMillis = selectedDate }
        }
        // DatePicker 使用 UTC 时间，需要转换
        val initialUtcMillis = remember(selectedDate) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.YEAR, currentCal.get(Calendar.YEAR))
                set(Calendar.MONTH, currentCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 12) // 使用中午12点避免边界问题
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        // 从 UTC 时间提取年月日，设置为本地时间的那一天
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            // 保留原来的时分秒
                            set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, currentCal.get(Calendar.SECOND))
                        }
                        selectedDate = localCal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (selected) color else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 10.dp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CategoryItem(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) Color(category.color).copy(alpha = 0.15f)
                else Color.Transparent
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (selected) Color(category.color) else Color(category.color).copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = IconUtil.getIcon(category.icon),
                contentDescription = category.name,
                tint = if (selected) Color.White else Color(category.color),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.name,
            fontSize = 11.sp,
            color = if (selected) Color(category.color) else MaterialTheme.colorScheme.onSurface
        )
    }
}
