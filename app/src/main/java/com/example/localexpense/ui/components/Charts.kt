package com.example.localexpense.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localexpense.data.CategoryStat
import com.example.localexpense.data.DailyStat

/**
 * 饼图组件
 *
 * v1.9.6 性能优化：
 * - 使用 remember 缓存总额和百分比计算
 * - 预计算扇形角度，避免在 Canvas 中重复计算
 */
@Composable
fun PieChart(
    data: List<CategoryStat>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    // v1.9.6 优化：缓存总额计算
    val total = remember(data) { data.sumOf { it.total } }
    if (total == 0.0) return

    // v1.9.6 优化：预计算每个扇形的角度
    val sweepAngles = remember(data, total) {
        data.map { (it.total / total * 360f).toFloat() }
    }

    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "pie"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    val centerColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pie chart
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .padding(8.dp)
        ) {
            var startAngle = -90f
            // v1.9.6 优化：使用预计算的角度
            sweepAngles.forEachIndexed { index, angle ->
                val animatedAngle = angle * animatedProgress.value
                drawArc(
                    color = colors.getOrElse(index) { Color.Gray },
                    startAngle = startAngle,
                    sweepAngle = animatedAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                startAngle += animatedAngle
            }
            // Center hole - use surface color for dark mode support
            drawCircle(
                color = centerColor,
                radius = size.width * 0.3f
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Legend
        Column(modifier = Modifier.weight(1f)) {
            data.take(5).forEachIndexed { index, stat ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                colors.getOrElse(index) { Color.Gray },
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stat.category,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.0f%%".format(stat.total / total * 100),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 柱状图组件
 *
 * v1.9.6 性能优化：
 * - 使用 remember 缓存最大值和高度比例计算
 * - 预计算日期标签
 */
@Composable
fun BarChart(
    data: List<DailyStat>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    // v1.9.6 优化：缓存最大值计算
    val maxValue = remember(data) { data.maxOfOrNull { it.total } ?: 1.0 }

    // v1.9.6 优化：预计算每个柱子的高度比例
    val heightRatios = remember(data, maxValue) {
        data.map { (it.total / maxValue).toFloat() }
    }

    // v1.9.6 优化：预计算日期标签
    val dateLabels = remember(data) {
        data.map { it.date.takeLast(2) }
    }

    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "bar"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    Column(modifier = modifier) {
        // 使用BoxWithConstraints获取实际宽度，确保标签与柱子对齐
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            val totalWidth = constraints.maxWidth.toFloat()
            val barWidth = totalWidth / (data.size * 2)
            val spacing = barWidth
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                // v1.9.6 优化：使用预计算的高度比例
                heightRatios.forEachIndexed { index, ratio ->
                    val barHeight = ratio * size.height * animatedProgress.value
                    val x = index * (barWidth + spacing) + spacing / 2

                    drawRoundRect(
                        color = color.copy(alpha = 0.3f),
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            }
        }

        // X-axis labels - 使用相同的布局计算方式
        // v1.9.6 优化：使用预计算的日期标签
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            dateLabels.forEach { label ->
                // 每个标签占据 barWidth + spacing 的空间
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetProgressBar(
    spent: Double,
    budget: Double,
    modifier: Modifier = Modifier
) {
    val actualProgress = if (budget > 0) spent / budget else 0.0
    val displayProgress = actualProgress.coerceIn(0.0, 1.0)
    val isOverBudget = actualProgress > 1.0
    
    val color = when {
        actualProgress >= 1.0 -> Color(0xFFE53935)
        actualProgress >= 0.8 -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress = animateFloatAsState(
        targetValue = if (animationPlayed) displayProgress.toFloat() else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "budget"
    )

    LaunchedEffect(Unit) { animationPlayed = true }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "已花费 ¥%.2f".format(spent),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isOverBudget) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "预算 ¥%.2f".format(budget),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.value)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isOverBudget) "已超支 %.0f%%".format((actualProgress - 1) * 100) 
                   else "%.0f%%".format(actualProgress * 100),
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
