package com.example.localexpense.ui

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * UI 扩展函数
 *
 * 提供 Compose UI 常用的扩展功能：
 * 1. 事件收集和处理
 * 2. Toast 显示
 * 3. Snackbar 显示
 * 4. LazyColumn 性能优化（v1.9.0）
 */

// ==================== LazyColumn 性能优化 (v1.9.0) ====================

/**
 * 检测列表是否滚动到底部
 * 用于实现分页加载
 *
 * @param buffer 提前触发的缓冲区大小
 */
@Composable
fun LazyListState.isScrolledToEnd(buffer: Int = 3): Boolean {
    val isAtEnd by remember(this) {
        derivedStateOf {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - buffer
        }
    }
    return isAtEnd
}

/**
 * 当滚动到底部时触发回调
 * 适用于无限滚动加载
 *
 * @param buffer 提前触发的缓冲区大小
 * @param onLoadMore 加载更多回调
 */
@Composable
fun LazyListState.OnScrollToEnd(
    buffer: Int = 3,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore = isScrolledToEnd(buffer)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
}

/**
 * 监听滚动方向
 *
 * @param onScrollDirectionChanged 滚动方向变化回调
 */
@Composable
fun LazyListState.ObserveScrollDirection(
    onScrollDirectionChanged: (ScrollDirection) -> Unit
) {
    var previousIndex by remember { mutableStateOf(firstVisibleItemIndex) }
    var previousOffset by remember { mutableStateOf(firstVisibleItemScrollOffset) }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val direction = when {
                    index > previousIndex -> ScrollDirection.DOWN
                    index < previousIndex -> ScrollDirection.UP
                    offset > previousOffset -> ScrollDirection.DOWN
                    offset < previousOffset -> ScrollDirection.UP
                    else -> ScrollDirection.NONE
                }

                if (direction != ScrollDirection.NONE) {
                    onScrollDirectionChanged(direction)
                }

                previousIndex = index
                previousOffset = offset
            }
    }
}

/**
 * 滚动方向
 */
enum class ScrollDirection {
    UP, DOWN, NONE
}

/**
 * 检测是否显示 FAB（根据滚动方向）
 * 向下滚动时隐藏，向上滚动时显示
 */
@Composable
fun LazyListState.shouldShowFab(): Boolean {
    var showFab by remember { mutableStateOf(true) }

    ObserveScrollDirection { direction ->
        showFab = when (direction) {
            ScrollDirection.UP -> true
            ScrollDirection.DOWN -> false
            ScrollDirection.NONE -> showFab
        }
    }

    return showFab
}

/**
 * 检测是否应该显示"回到顶部"按钮
 *
 * @param threshold 显示按钮的阈值（滚动超过多少项后显示）
 */
@Composable
fun LazyListState.shouldShowScrollToTop(threshold: Int = 5): Boolean {
    return remember(this) {
        derivedStateOf { firstVisibleItemIndex > threshold }
    }.value
}

/**
 * 收集 UI 事件并处理
 *
 * @param events 事件流
 * @param snackbarHostState Snackbar 状态（可选）
 * @param onNavigateBack 返回导航回调
 * @param onNavigateToDetail 跳转详情回调
 */
@Composable
fun CollectUiEvents(
    events: Flow<UiEvent>,
    snackbarHostState: SnackbarHostState? = null,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToDetail: ((Long) -> Unit)? = null,
    onExportComplete: ((String) -> Unit)? = null,
    onImportComplete: ((Int, Int, Int) -> Unit)? = null,
    onConfirmAction: ((String, String, () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        events.collectLatest { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }

                is UiEvent.ShowSnackbar -> {
                    snackbarHostState?.let { hostState ->
                        // 根据事件类型转换为 Compose SnackbarDuration
                        val duration = when (event.duration) {
                            SnackbarDurationType.SHORT -> SnackbarDuration.Short
                            SnackbarDurationType.LONG -> SnackbarDuration.Long
                            SnackbarDurationType.INDEFINITE -> SnackbarDuration.Indefinite
                        }

                        val result = hostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = duration
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            event.action?.invoke()
                        }
                    }
                }

                is UiEvent.NavigateBack -> {
                    onNavigateBack?.invoke()
                }

                is UiEvent.NavigateToDetail -> {
                    onNavigateToDetail?.invoke(event.expenseId)
                }

                is UiEvent.RefreshComplete -> {
                    // 可以添加刷新完成的动画或提示
                }

                is UiEvent.ExportComplete -> {
                    onExportComplete?.invoke(event.filePath)
                }

                is UiEvent.ExportSuccess -> {
                    Toast.makeText(
                        context,
                        "导出成功: ${event.recordCount} 条记录\n${event.filePath}",
                        Toast.LENGTH_LONG
                    ).show()
                    onExportComplete?.invoke(event.filePath)
                }

                is UiEvent.OperationSuccess -> {
                    // 成功操作可以选择性显示
                    // 默认静默处理，避免频繁提示
                }

                is UiEvent.ImportSuccess -> {
                    val message = buildString {
                        append("导入完成: ${event.importedCount} 条成功")
                        if (event.skippedCount > 0) {
                            append(", ${event.skippedCount} 条跳过")
                        }
                        if (event.failedCount > 0) {
                            append(", ${event.failedCount} 条失败")
                        }
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    onImportComplete?.invoke(event.importedCount, event.skippedCount, event.failedCount)
                }

                is UiEvent.ConfirmAction -> {
                    onConfirmAction?.invoke(event.title, event.message, event.onConfirm)
                }
            }
        }
    }
}

/**
 * 记住 SnackbarHostState
 */
@Composable
fun rememberSnackbarHostState(): SnackbarHostState {
    return remember { SnackbarHostState() }
}

/**
 * 格式化金额显示
 */
fun Double.formatAmount(): String {
    return "¥${String.format("%.2f", this)}"
}

/**
 * 格式化金额显示（带正负号）
 */
fun Double.formatAmountWithSign(isExpense: Boolean): String {
    val sign = if (isExpense) "-" else "+"
    return "$sign¥${String.format("%.2f", this)}"
}

/**
 * 格式化百分比
 */
fun Float.formatPercent(): String {
    return "${String.format("%.1f", this)}%"
}

/**
 * 截断文本
 */
fun String.truncate(maxLength: Int, suffix: String = "..."): String {
    return if (length > maxLength) {
        take(maxLength - suffix.length) + suffix
    } else {
        this
    }
}
