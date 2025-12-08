package com.example.localexpense.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 无障碍服务工具类
 */
object AccessibilityUtils {

    private const val SERVICE_CLASS = "com.example.localexpense.accessibility.ExpenseAccessibilityService"

    /**
     * 检测无障碍服务是否已启用
     */
    fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false

        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        val targetService = "${context.packageName}/.accessibility.ExpenseAccessibilityService"

        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.let {
                "${it.packageName}/${it.name}" == targetService ||
                it.name == SERVICE_CLASS
            }
        }
    }
}

/**
 * Composable Hook: 监测无障碍服务状态
 *
 * 功能：
 * 1. 自动检测无障碍服务是否已启用
 * 2. 在页面恢复时（ON_RESUME）自动刷新状态
 * 3. 避免重复代码，提供统一的无障碍状态检测
 *
 * @return 无障碍服务是否已启用
 */
@Composable
fun rememberAccessibilityServiceState(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 使用 refreshKey 触发重新检测
    var refreshKey by remember { mutableIntStateOf(0) }

    // 计算无障碍服务状态
    val isEnabled = remember(refreshKey) {
        AccessibilityUtils.isServiceEnabled(context)
    }

    // 监听生命周期，当页面重新可见时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return isEnabled
}
