package com.example.localexpense.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.data.backup.DataBackupManager
import com.example.localexpense.domain.Result
import com.example.localexpense.ui.theme.ExpenseTheme
import com.example.localexpense.ui.util.IconUtil
import com.example.localexpense.util.MonitorSettings
import kotlinx.coroutines.launch
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
    onOpenAccessibility: () -> Unit,
    onImportData: ((Uri) -> Unit)? = null,
    onClearAllData: (() -> Unit)? = null
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 备份管理器
    val backupManager = remember {
        DataBackupManager(context, TransactionRepository.getInstance(context))
    }

    // 检测无障碍服务状态 - 使用 key 触发重新检测
    var refreshKey by remember { mutableStateOf(0) }
    val isAccessibilityEnabled = remember(refreshKey) {
        isAccessibilityServiceEnabled(context)
    }
    
    // 监听生命周期，当页面重新可见时刷新状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (onImportData != null) {
                    SettingsItem(
                        icon = Icons.Default.FileUpload,
                        title = "导入数据",
                        subtitle = "从 CSV/JSON 文件导入",
                        onClick = { showImportDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "备份数据",
                    subtitle = "完整备份到 JSON 文件",
                    onClick = { showBackupDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsItem(
                    icon = Icons.Default.Restore,
                    title = "恢复数据",
                    subtitle = "从 JSON 备份文件恢复",
                    onClick = { showRestoreDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsItem(
                    icon = Icons.Default.CleaningServices,
                    title = "清理旧数据",
                    subtitle = "删除指定时间前的账单",
                    onClick = { showCleanupDialog = true }
                )
                if (onClearAllData != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsItem(
                        icon = Icons.Default.DeleteForever,
                        title = "清除所有数据",
                        subtitle = "删除全部账单记录",
                        onClick = { showClearAllDialog = true }
                    )
                }
            }
        }

        // Accessibility section
        item {
            // 监听状态 - 使用 refreshKey 触发刷新（从无障碍设置返回后自动更新）
            var isMonitorEnabled by remember(refreshKey) {
                mutableStateOf(MonitorSettings.isMonitorEnabled(context))
            }
            val monitorStartTime = remember(isMonitorEnabled, refreshKey) {
                MonitorSettings.getFormattedStartTime(context)
            }
            // 显示提示对话框
            var showAccessibilityPrompt by remember { mutableStateOf(false) }

            SettingsCard(title = "自动记账") {
                // 监听开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isMonitorEnabled && isAccessibilityEnabled)
                            ExpenseTheme.colors.income
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isMonitorEnabled && isAccessibilityEnabled -> "监听中"
                                isMonitorEnabled && !isAccessibilityEnabled -> "等待启用无障碍"
                                else -> "开始监听"
                            },
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when {
                                isMonitorEnabled && isAccessibilityEnabled -> "开始于 $monitorStartTime"
                                isMonitorEnabled && !isAccessibilityEnabled -> "请先开启无障碍服务"
                                else -> "点击开始记录新交易"
                            },
                            fontSize = 13.sp,
                            color = if (isMonitorEnabled && !isAccessibilityEnabled)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMonitorEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !isAccessibilityEnabled) {
                                // 无障碍服务未启用，显示提示对话框
                                showAccessibilityPrompt = true
                            } else {
                                MonitorSettings.setMonitorEnabled(context, enabled)
                                isMonitorEnabled = enabled
                                if (enabled) {
                                    Toast.makeText(context, "开始监听，之后的交易将被记录", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "已停止监听", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                // 无障碍未启用提示对话框
                if (showAccessibilityPrompt) {
                    AlertDialog(
                        onDismissRequest = { showAccessibilityPrompt = false },
                        icon = {
                            Icon(
                                Icons.Default.Accessibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { Text("需要开启无障碍服务") },
                        text = {
                            Text("自动记账功能需要无障碍服务支持。\n\n开启后，监听功能将自动启用。")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showAccessibilityPrompt = false
                                    // 先开启监听（等待无障碍服务启用后生效）
                                    MonitorSettings.setMonitorEnabled(context, true)
                                    isMonitorEnabled = true
                                    // 跳转到无障碍设置
                                    onOpenAccessibility()
                                }
                            ) {
                                Text("去开启")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAccessibilityPrompt = false }) {
                                Text("取消")
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 无障碍服务
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "无障碍服务",
                    subtitle = if (isAccessibilityEnabled)
                        "已启用"
                    else
                        "未启用 - 点击开启",
                    onClick = onOpenAccessibility,
                    trailing = {
                        if (isAccessibilityEnabled) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已启用",
                                tint = ExpenseTheme.colors.income,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 支持的应用
                SettingsItem(
                    icon = Icons.Default.Apps,
                    title = "支持的应用",
                    subtitle = "微信、支付宝、云闪付",
                    onClick = {}
                )
            }
        }

        // About section
        item {
            // 调试日志开关
            var isDebugLogEnabled by remember {
                mutableStateOf(MonitorSettings.isDebugLogEnabled(context))
            }

            // 动态获取版本号
            val versionName = remember {
                try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    packageInfo.versionName ?: "未知"
                } catch (e: Exception) {
                    "未知"
                }
            }

            SettingsCard(title = "关于") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "版本",
                    subtitle = versionName,
                    onClick = {}
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 数据统计
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "数据统计",
                    subtitle = "${expenses.size} 条账单 · ${categories.size} 个分类",
                    onClick = {}
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 调试日志开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "调试日志",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isDebugLogEnabled) "已启用详细日志" else "仅记录基本日志",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDebugLogEnabled,
                        onCheckedChange = { enabled ->
                            MonitorSettings.setDebugLogEnabled(context, enabled)
                            isDebugLogEnabled = enabled
                        }
                    )
                }
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

    // Backup dialog
    if (showBackupDialog) {
        BackupDialog(
            backupManager = backupManager,
            context = context,
            scope = scope,
            onDismiss = { showBackupDialog = false }
        )
    }

    // Restore dialog
    if (showRestoreDialog) {
        RestoreDialog(
            backupManager = backupManager,
            context = context,
            scope = scope,
            onDismiss = { showRestoreDialog = false }
        )
    }

    // Cleanup dialog
    if (showCleanupDialog) {
        CleanupDialog(
            context = context,
            scope = scope,
            onDismiss = { showCleanupDialog = false }
        )
    }

    // Import dialog
    if (showImportDialog && onImportData != null) {
        com.example.localexpense.ui.components.ImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { uri ->
                onImportData(uri)
            }
        )
    }

    // Clear all data dialog
    if (showClearAllDialog && onClearAllData != null) {
        com.example.localexpense.ui.components.ConfirmDialog(
            title = "清除所有数据",
            message = "确定要删除所有账单记录吗？此操作不可恢复！\n\n建议先备份数据再执行此操作。",
            confirmText = "确认删除",
            isDestructive = true,
            onConfirm = onClearAllData,
            onDismiss = { showClearAllDialog = false }
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
                onValueChange = { newValue ->
                    // 只允许数字和最多一个小数点，且小数点后最多2位
                    val filtered = newValue.filter { c -> c.isDigit() || c == '.' }
                    val dotCount = filtered.count { it == '.' }
                    if (dotCount <= 1) {
                        // 检查小数位数不超过2位
                        val parts = filtered.split('.')
                        if (parts.size <= 1 || parts[1].length <= 2) {
                            budgetText = filtered
                        }
                    }
                },
                label = { Text("预算金额") },
                prefix = { Text("¥") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    budgetText.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(it) }
                },
                enabled = budgetText.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("保存") }
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
    return com.example.localexpense.util.AccessibilityUtils.isServiceEnabled(context)
}

/**
 * 备份对话框
 */
@Composable
private fun BackupDialog(
    backupManager: DataBackupManager,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }

    // 创建文件选择器
    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                when (val result = backupManager.exportToUri(uri)) {
                    is Result.Success -> {
                        Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    is Result.Error -> {
                        Toast.makeText(context, "备份失败: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Loading -> {}
                }
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("备份数据") },
        text = {
            Column {
                Text("将所有账单、分类和预算数据备份到 JSON 文件。")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "备份文件可用于数据迁移或恢复。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在备份...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    createFileLauncher.launch(backupManager.generateBackupFileName())
                },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Backup, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("选择保存位置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("取消")
            }
        }
    )
}

/**
 * 恢复对话框
 */
@Composable
private fun RestoreDialog(
    backupManager: DataBackupManager,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var mergeMode by remember { mutableStateOf(false) }

    // 文件选择器
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                when (val result = backupManager.importFromUri(uri, merge = mergeMode)) {
                    is Result.Success -> {
                        Toast.makeText(context, result.data.summary, Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                    is Result.Error -> {
                        Toast.makeText(context, "恢复失败: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Loading -> {}
                }
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("恢复数据") },
        text = {
            Column {
                Text("从 JSON 备份文件恢复数据。")
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mergeMode = !mergeMode }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = mergeMode,
                        onCheckedChange = { mergeMode = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("合并模式")
                        Text(
                            if (mergeMode) "保留现有数据，追加导入数据"
                            else "清空现有数据后导入（谨慎操作）",
                            fontSize = 12.sp,
                            color = if (mergeMode)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在恢复...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    openFileLauncher.launch(arrayOf("application/json"))
                },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("选择备份文件")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("取消")
            }
        }
    )
}

/**
 * 清理数据对话框
 */
@Composable
private fun CleanupDialog(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    val repository = remember { TransactionRepository.getInstance(context) }
    var selectedPeriod by remember { mutableStateOf(3) } // 默认3个月
    var isLoading by remember { mutableStateOf(false) }
    var countToDelete by remember { mutableStateOf<Int?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    val periodOptions = listOf(
        1 to "1 个月前",
        3 to "3 个月前",
        6 to "6 个月前",
        12 to "1 年前"
    )

    // 计算要删除的记录数
    LaunchedEffect(selectedPeriod) {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.MONTH, -selectedPeriod)
        val beforeTimestamp = calendar.timeInMillis
        countToDelete = repository.countExpensesBeforeDate(beforeTimestamp)
    }

    if (showConfirm) {
        // 确认删除对话框
        AlertDialog(
            onDismissRequest = { if (!isLoading) showConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("即将删除 ${countToDelete ?: 0} 条账单记录，此操作不可撤销！")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "建议在删除前先备份数据。",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val calendar = java.util.Calendar.getInstance()
                            calendar.add(java.util.Calendar.MONTH, -selectedPeriod)
                            val deletedCount = repository.deleteExpensesBeforeDate(calendar.timeInMillis)
                            Toast.makeText(context, "已删除 $deletedCount 条记录", Toast.LENGTH_SHORT).show()
                            isLoading = false
                            onDismiss()
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("确认删除")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }, enabled = !isLoading) {
                    Text("取消")
                }
            }
        )
    } else {
        // 选择时间段对话框
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("清理旧数据") },
            text = {
                Column {
                    Text("选择要删除的数据时间范围：")
                    Spacer(modifier = Modifier.height(12.dp))

                    periodOptions.forEach { (months, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPeriod = months }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedPeriod == months,
                                onClick = { selectedPeriod = months }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (countToDelete != null) "将删除 $countToDelete 条记录" else "计算中...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = true },
                    enabled = (countToDelete ?: 0) > 0
                ) {
                    Text("下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        )
    }
}
