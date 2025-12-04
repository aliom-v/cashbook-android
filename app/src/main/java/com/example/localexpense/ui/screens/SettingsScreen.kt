package com.example.localexpense.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.ui.theme.ExpenseTheme
import com.example.localexpense.ui.util.IconUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    budget: BudgetEntity?,
    categories: List<CategoryEntity>,
    expenses: List<ExpenseEntity>,
    onSaveBudget: (Double) -> Unit,
    onAddCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 检测无障碍服务状态
    val isAccessibilityEnabled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAccessibilityEnabled.value = isAccessibilityServiceEnabled(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "设置",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Budget section
        item {
            SettingsCard(title = "预算管理") {
                SettingsItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "月度预算",
                    subtitle = if (budget != null) "¥%.2f".format(budget.amount) else "未设置",
                    onClick = { showBudgetDialog = true }
                )
            }
        }

        // Category section
        item {
            SettingsCard(title = "分类管理") {
                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "自定义分类",
                    subtitle = "${categories.size} 个分类",
                    onClick = { showCategoryDialog = true }
                )
            }
        }

        // Data section
        item {
            SettingsCard(title = "数据管理") {
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "导出数据",
                    subtitle = "导出为 CSV 文件",
                    onClick = { showExportDialog = true }
                )
            }
        }

        // Accessibility section
        item {
            SettingsCard(title = "自动记账") {
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "无障碍服务",
                    subtitle = if (isAccessibilityEnabled.value)
                        "已启用 - 正在监听微信/支付宝"
                    else
                        "未启用 - 点击开启自动记账",
                    onClick = onOpenAccessibility,
                    trailing = {
                        if (isAccessibilityEnabled.value) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已启用",
                                tint = ExpenseTheme.colors.income,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }

        // About section
        item {
            SettingsCard(title = "关于") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "版本",
                    subtitle = "1.0.0",
                    onClick = {}
                )
            }
        }
    }

    // Budget dialog
    if (showBudgetDialog) {
        BudgetDialog(
            currentBudget = budget?.amount ?: 0.0,
            onDismiss = { showBudgetDialog = false },
            onSave = {
                onSaveBudget(it)
                showBudgetDialog = false
            }
        )
    }

    // Category dialog
    if (showCategoryDialog) {
        CategoryManageDialog(
            categories = categories,
            onDismiss = { showCategoryDialog = false },
            onAdd = onAddCategory,
            onDelete = onDeleteCategory
        )
    }

    // Export dialog
    if (showExportDialog) {
        ExportDialog(
            expenses = expenses,
            context = context,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Medium)
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                trailing()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetDialog(
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var budgetText by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置月度预算") },
        text = {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("预算金额") },
                prefix = { Text("¥") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                budgetText.toDoubleOrNull()?.let { onSave(it) }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CategoryManageDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("expense") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类管理") },
        text = {
            Column {
                // Type tabs
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedType == "expense",
                        onClick = { selectedType = "expense" },
                        label = { Text("支出") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedType == "income",
                        onClick = { selectedType = "income" },
                        label = { Text("收入") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category list
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(categories.filter { it.type == selectedType }) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = IconUtil.getIcon(category.icon),
                                contentDescription = null,
                                tint = Color(category.color),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = category.name,
                                modifier = Modifier.weight(1f)
                            )
                            if (!category.isDefault) {
                                IconButton(
                                    onClick = { onDelete(category) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加分类")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    if (showAddDialog) {
        AddCategoryDialog(
            type = selectedType,
            onDismiss = { showAddDialog = false },
            onAdd = {
                onAdd(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddCategoryDialog(
    type: String,
    onDismiss: () -> Unit,
    onAdd: (CategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("MoreHoriz") }
    var selectedColor by remember { mutableStateOf(0xFF607D8B) }

    val colors = listOf(
        0xFFE53935, 0xFFFF9800, 0xFF4CAF50, 0xFF2196F3,
        0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4, 0xFF607D8B
    )

    // 可选图标列表
    val icons = listOf(
        "Restaurant" to "餐饮",
        "ShoppingBag" to "购物",
        "DirectionsCar" to "交通",
        "SportsEsports" to "娱乐",
        "Home" to "生活",
        "LocalHospital" to "医疗",
        "School" to "教育",
        "CardGiftcard" to "礼物",
        "SwapHoriz" to "转账",
        "TrendingUp" to "投资",
        "Work" to "工作",
        "Flight" to "旅行",
        "Pets" to "宠物",
        "FitnessCenter" to "健身",
        "LocalCafe" to "咖啡",
        "MoreHoriz" to "其他"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("选择图标", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(icons) { (iconName, _) ->
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { selectedIcon = iconName },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedIcon == iconName)
                                Color(selectedColor).copy(alpha = 0.2f)
                            else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = IconUtil.getIcon(iconName),
                                    contentDescription = null,
                                    tint = if (selectedIcon == iconName)
                                        Color(selectedColor)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("选择颜色", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { color ->
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedColor = color },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(color)
                        ) {
                            if (selectedColor == color) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(
                            CategoryEntity(
                                name = name,
                                icon = selectedIcon,
                                color = selectedColor,
                                type = type
                            )
                        )
                    }
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ExportDialog(
    expenses: List<ExpenseEntity>,
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出数据") },
        text = {
            Text("将导出 ${expenses.size} 条账单记录为 CSV 文件")
        },
        confirmButton = {
            TextButton(onClick = {
                exportToCsv(context, expenses)
                onDismiss()
            }) { Text("导出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun exportToCsv(context: Context, expenses: List<ExpenseEntity>) {
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fileName = "expenses_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
        
        // 转义 CSV 字段中的特殊字符
        fun escapeCsv(value: String): String {
            return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                "\"${value.replace("\"", "\"\"")}\""
            } else value
        }
        
        val csvContent = buildString {
            appendLine("日期,类型,分类,商户,金额,备注,来源")
            expenses.forEach { expense ->
                appendLine(
                    "${dateFormat.format(Date(expense.timestamp))}," +
                    "${if (expense.type == "expense") "支出" else "收入"}," +
                    "${escapeCsv(expense.category)}," +
                    "${escapeCsv(expense.merchant)}," +
                    "${expense.amount}," +
                    "${escapeCsv(expense.note)}," +
                    escapeCsv(expense.channel)
                )
            }
        }

        val file = File(context.getExternalFilesDir(null), fileName)
        // 添加UTF-8 BOM，确保Excel正确识别中文编码
        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM
            out.write(csvContent.toByteArray(Charsets.UTF_8))
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享账单"))

    } catch (e: Exception) {
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 检测无障碍服务是否已启用
 */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false

    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )

    val targetService = "${context.packageName}/.accessibility.ExpenseAccessibilityService"

    return enabledServices.any { service ->
        service.resolveInfo.serviceInfo.let {
            "${it.packageName}/${it.name}" == targetService ||
            it.name == "com.example.localexpense.accessibility.ExpenseAccessibilityService"
        }
    }
}
