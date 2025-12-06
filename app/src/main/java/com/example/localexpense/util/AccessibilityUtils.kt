package com.example.localexpense.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

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
