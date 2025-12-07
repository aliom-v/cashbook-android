package com.example.localexpense.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.localexpense.util.DataExporter
import java.util.Calendar

/**
 * 数据导出对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExportAll: (DataExporter.ExportFormat) -> Unit,
    onExportMonthly: (year: Int, month: Int) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFormat by remember { mutableStateOf(DataExporter.ExportFormat.CSV) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出数据") },
        text = {
            Column {
                // 选项卡
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("全部导出") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("月度报表") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // 全部导出 - 选择格式
                        Text(
                            text = "选择导出格式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FormatOption(
                            icon = Icons.Default.Description,
                            title = "CSV 格式",
                            description = "通用表格格式，可用 Excel 打开",
                            selected = selectedFormat == DataExporter.ExportFormat.CSV,
                            onClick = { selectedFormat = DataExporter.ExportFormat.CSV }
                        )

                        FormatOption(
                            icon = Icons.Default.TableChart,
                            title = "TSV 格式",
                            description = "Tab 分隔，Excel 友好",
                            selected = selectedFormat == DataExporter.ExportFormat.TSV,
                            onClick = { selectedFormat = DataExporter.ExportFormat.TSV }
                        )

                        FormatOption(
                            icon = Icons.Default.Code,
                            title = "JSON 格式",
                            description = "程序化数据交换格式",
                            selected = selectedFormat == DataExporter.ExportFormat.JSON,
                            onClick = { selectedFormat = DataExporter.ExportFormat.JSON }
                        )
                    }
                    1 -> {
                        // 月度报表 - 选择年月
                        Text(
                            text = "选择导出月份",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 年份选择
                            var yearExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = yearExpanded,
                                onExpandedChange = { yearExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = "${selectedYear}年",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("年份") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = yearExpanded,
                                    onDismissRequest = { yearExpanded = false }
                                ) {
                                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                    (currentYear downTo currentYear - 5).forEach { year ->
                                        DropdownMenuItem(
                                            text = { Text("${year}年") },
                                            onClick = {
                                                selectedYear = year
                                                yearExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 月份选择
                            var monthExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = monthExpanded,
                                onExpandedChange = { monthExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = "${selectedMonth}月",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("月份") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = monthExpanded,
                                    onDismissRequest = { monthExpanded = false }
                                ) {
                                    (1..12).forEach { month ->
                                        DropdownMenuItem(
                                            text = { Text("${month}月") },
                                            onClick = {
                                                selectedMonth = month
                                                monthExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "将生成包含汇总、分类统计和明细的完整报表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> onExportAll(selectedFormat)
                        1 -> onExportMonthly(selectedYear, selectedMonth)
                    }
                    onDismiss()
                }
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun FormatOption(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
