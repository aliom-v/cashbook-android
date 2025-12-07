package com.example.localexpense.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localexpense.ui.QuickFilterType
import com.example.localexpense.util.FilterManager

/**
 * 筛选底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentCriteria: FilterManager.FilterCriteria,
    categories: List<String>,
    channels: List<String>,
    onApply: (FilterManager.FilterCriteria) -> Unit,
    onQuickFilter: (QuickFilterType) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var criteria by remember { mutableStateOf(currentCriteria) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = {
                    criteria = FilterManager.FilterCriteria.EMPTY
                    onClear()
                }) {
                    Text("清除筛选")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 快捷筛选
            Text(
                text = "快捷筛选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickFilterChip("今日", QuickFilterType.TODAY, onQuickFilter, onDismiss)
                QuickFilterChip("本周", QuickFilterType.THIS_WEEK, onQuickFilter, onDismiss)
                QuickFilterChip("本月", QuickFilterType.THIS_MONTH, onQuickFilter, onDismiss)
                QuickFilterChip("大额支出", QuickFilterType.LARGE_EXPENSE, onQuickFilter, onDismiss)
                QuickFilterChip("仅支出", QuickFilterType.EXPENSE_ONLY, onQuickFilter, onDismiss)
                QuickFilterChip("仅收入", QuickFilterType.INCOME_ONLY, onQuickFilter, onDismiss)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 交易类型
            Text(
                text = "交易类型",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterManager.TransactionTypeFilter.entries.forEach { type ->
                    FilterChip(
                        selected = criteria.transactionType == type,
                        onClick = {
                            criteria = criteria.copy(transactionType = type)
                        },
                        label = { Text(type.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 金额范围
            Text(
                text = "金额范围",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = criteria.minAmount?.toString() ?: "",
                    onValueChange = { value ->
                        criteria = criteria.copy(
                            minAmount = value.toDoubleOrNull()
                        )
                    },
                    label = { Text("最小金额") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    prefix = { Text("¥") }
                )
                Text("—")
                OutlinedTextField(
                    value = criteria.maxAmount?.toString() ?: "",
                    onValueChange = { value ->
                        criteria = criteria.copy(
                            maxAmount = value.toDoubleOrNull()
                        )
                    },
                    label = { Text("最大金额") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    prefix = { Text("¥") }
                )
            }

            // 分类筛选
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "分类筛选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category in criteria.categories,
                            onClick = {
                                val newCategories = if (category in criteria.categories) {
                                    criteria.categories - category
                                } else {
                                    criteria.categories + category
                                }
                                criteria = criteria.copy(categories = newCategories)
                            },
                            label = { Text(category) }
                        )
                    }
                }
            }

            // 渠道筛选
            if (channels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "渠道筛选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    channels.forEach { channel ->
                        FilterChip(
                            selected = channel in criteria.channels,
                            onClick = {
                                val newChannels = if (channel in criteria.channels) {
                                    criteria.channels - channel
                                } else {
                                    criteria.channels + channel
                                }
                                criteria = criteria.copy(channels = newChannels)
                            },
                            label = { Text(channel) }
                        )
                    }
                }
            }

            // 商户搜索
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = criteria.merchantKeyword ?: "",
                onValueChange = { value ->
                    criteria = criteria.copy(merchantKeyword = value.ifBlank { null })
                },
                label = { Text("商户关键词") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) }
            )

            // 排序方式
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterManager.SortBy.entries.forEach { sortBy ->
                    FilterChip(
                        selected = criteria.sortBy == sortBy,
                        onClick = {
                            criteria = criteria.copy(sortBy = sortBy)
                        },
                        label = { Text(sortBy.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onApply(criteria)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("应用筛选")
                }
            }
        }
    }
}

@Composable
private fun QuickFilterChip(
    label: String,
    filterType: QuickFilterType,
    onQuickFilter: (QuickFilterType) -> Unit,
    onDismiss: () -> Unit
) {
    AssistChip(
        onClick = {
            onQuickFilter(filterType)
            onDismiss()
        },
        label = { Text(label) }
    )
}

/**
 * 筛选结果栏
 */
@Composable
fun FilterResultBar(
    filterResult: FilterManager.FilterResult?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (filterResult == null) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "筛选结果: ${filterResult.totalCount} 条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (filterResult.totalExpense > 0 || filterResult.totalIncome > 0) {
                    Text(
                        text = " | 支出 ¥${String.format("%.2f", filterResult.totalExpense)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清除筛选",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * 批量选择操作栏
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消选择"
                    )
                }
                Text(
                    text = "已选择 $selectedCount 项",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row {
                TextButton(onClick = onSelectAll) {
                    Text("全选")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = selectedCount > 0
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}
