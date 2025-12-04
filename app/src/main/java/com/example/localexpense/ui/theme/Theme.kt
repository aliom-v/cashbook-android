package com.example.localexpense.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 应用扩展颜色 - 用于语义化的颜色定义
 */
data class ExtendedColors(
    // 收入/支出颜色
    val expense: Color,
    val expenseBackground: Color,
    val income: Color,
    val incomeBackground: Color,

    // 分类默认颜色
    val categoryRed: Color,
    val categoryOrange: Color,
    val categoryGreen: Color,
    val categoryBlue: Color,
    val categoryPurple: Color,
    val categoryPink: Color,
    val categoryCyan: Color,
    val categoryGray: Color
)

val LightExtendedColors = ExtendedColors(
    expense = Color(0xFFE53935),
    expenseBackground = Color(0xFFFFEBEE),
    income = Color(0xFF43A047),
    incomeBackground = Color(0xFFE8F5E9),

    categoryRed = Color(0xFFE53935),
    categoryOrange = Color(0xFFFF9800),
    categoryGreen = Color(0xFF4CAF50),
    categoryBlue = Color(0xFF2196F3),
    categoryPurple = Color(0xFF9C27B0),
    categoryPink = Color(0xFFE91E63),
    categoryCyan = Color(0xFF00BCD4),
    categoryGray = Color(0xFF607D8B)
)

val DarkExtendedColors = ExtendedColors(
    expense = Color(0xFFEF5350),
    expenseBackground = Color(0xFF3E2723),
    income = Color(0xFF66BB6A),
    incomeBackground = Color(0xFF1B5E20),

    categoryRed = Color(0xFFEF5350),
    categoryOrange = Color(0xFFFFB74D),
    categoryGreen = Color(0xFF66BB6A),
    categoryBlue = Color(0xFF42A5F5),
    categoryPurple = Color(0xFFAB47BC),
    categoryPink = Color(0xFFEC407A),
    categoryCyan = Color(0xFF26C6DA),
    categoryGray = Color(0xFF78909C)
)

/**
 * 分类颜色列表（用于选择器）
 */
val CategoryColorOptions = listOf(
    0xFFE53935,
    0xFFFF9800,
    0xFF4CAF50,
    0xFF2196F3,
    0xFF9C27B0,
    0xFFE91E63,
    0xFF00BCD4,
    0xFF607D8B
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF4CAF50),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color.White,
    error = Color(0xFFE53935),
    onError = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF1B5E20),
    secondaryContainer = Color(0xFF2E7D32),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFFE65100),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFFB71C1C),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

@Composable
fun LocalExpenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

/**
 * 扩展属性：获取扩展颜色
 */
object ExpenseTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
