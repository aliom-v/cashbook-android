package com.example.localexpense.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * 服务生命周期管理器
 *
 * 功能：
 * 1. 无障碍服务状态监控
 * 2. 服务启用/禁用检查
 * 3. 跳转系统设置
 * 4. 服务状态变化监听
 *
 * 使用方式：
 * - 初始化: ServiceManager.init(context)
 * - 检查状态: ServiceManager.isAccessibilityServiceEnabled()
 * - 监听变化: ServiceManager.serviceState.collect { ... }
 */
object ServiceManager {

    private const val TAG = "ServiceManager"

    // 服务包名和类名
    private const val ACCESSIBILITY_SERVICE_CLASS = "com.example.localexpense.accessibility.ExpenseAccessibilityService"

    // 服务状态
    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: Flow<ServiceState> = _serviceState.asStateFlow()

    // Context 引用
    private var contextRef: WeakReference<Context>? = null

    // AccessibilityManager 状态监听器
    private var accessibilityStateListener: AccessibilityManager.AccessibilityStateChangeListener? = null

    /**
     * 服务状态
     */
    data class ServiceState(
        val isAccessibilityEnabled: Boolean = false,
        val isOverlayEnabled: Boolean = false,
        val isNotificationEnabled: Boolean = false,
        val lastCheckTime: Long = 0
    ) {
        /**
         * 检查所有必要权限是否已授予
         */
        val isFullyEnabled: Boolean
            get() = isAccessibilityEnabled && isOverlayEnabled

        /**
         * 获取缺少的权限列表
         */
        val missingPermissions: List<String>
            get() = buildList {
                if (!isAccessibilityEnabled) add("无障碍服务")
                if (!isOverlayEnabled) add("悬浮窗权限")
                if (!isNotificationEnabled) add("通知权限")
            }
    }

    /**
     * 初始化服务管理器
     */
    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)

        // 注册状态监听
        registerAccessibilityListener(context)

        // 初始检查
        refreshState()

        Logger.i(TAG, "ServiceManager 初始化完成")
    }

    /**
     * 注册无障碍服务状态监听
     */
    private fun registerAccessibilityListener(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am == null) {
            Logger.w(TAG, "无法获取 AccessibilityManager")
            return
        }

        // 移除旧监听器
        accessibilityStateListener?.let { am.removeAccessibilityStateChangeListener(it) }

        // 创建新监听器
        accessibilityStateListener = AccessibilityManager.AccessibilityStateChangeListener { enabled ->
            Logger.d(TAG) { "无障碍服务状态变化: $enabled" }
            refreshState()
        }

        am.addAccessibilityStateChangeListener(accessibilityStateListener!!)
    }

    /**
     * 刷新服务状态
     */
    fun refreshState() {
        val context = contextRef?.get() ?: return

        val isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        val isOverlayEnabled = isOverlayPermissionGranted(context)
        val isNotificationEnabled = NotificationHelper.areNotificationsEnabled(context)

        _serviceState.value = ServiceState(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isOverlayEnabled = isOverlayEnabled,
            isNotificationEnabled = isNotificationEnabled,
            lastCheckTime = System.currentTimeMillis()
        )

        Logger.d(TAG) {
            "服务状态: accessibility=$isAccessibilityEnabled, overlay=$isOverlayEnabled, notification=$isNotificationEnabled"
        }
    }

    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context? = null): Boolean {
        val ctx = context ?: contextRef?.get() ?: return false

        return try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false

            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            enabledServices.any { info ->
                info.resolveInfo?.serviceInfo?.let { serviceInfo ->
                    serviceInfo.packageName == ctx.packageName &&
                            serviceInfo.name == ACCESSIBILITY_SERVICE_CLASS
                } ?: false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "检查无障碍服务状态失败", e)
            false
        }
    }

    /**
     * 检查悬浮窗权限是否已授予
     */
    fun isOverlayPermissionGranted(context: Context? = null): Boolean {
        val ctx = context ?: contextRef?.get() ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else {
            true
        }
    }

    /**
     * 跳转到无障碍服务设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Logger.d(TAG) { "打开无障碍设置" }
        } catch (e: Exception) {
            Logger.e(TAG, "打开无障碍设置失败", e)
            // 降级到系统设置
            openSystemSettings(context)
        }
    }

    /**
     * 跳转到悬浮窗权限设置页面
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Logger.d(TAG) { "打开悬浮窗设置" }
            } catch (e: Exception) {
                Logger.e(TAG, "打开悬浮窗设置失败", e)
                openSystemSettings(context)
            }
        }
    }

    /**
     * 跳转到通知设置页面
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Logger.d(TAG) { "打开通知设置" }
        } catch (e: Exception) {
            Logger.e(TAG, "打开通知设置失败", e)
            openSystemSettings(context)
        }
    }

    /**
     * 跳转到应用详情设置页面
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Logger.d(TAG) { "打开应用设置" }
        } catch (e: Exception) {
            Logger.e(TAG, "打开应用设置失败", e)
            openSystemSettings(context)
        }
    }

    /**
     * 跳转到系统设置主页面
     */
    private fun openSystemSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "打开系统设置失败", e)
        }
    }

    /**
     * 跳转到电池优化设置
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                Logger.e(TAG, "打开电池优化设置失败", e)
                openSystemSettings(context)
            }
        }
    }

    /**
     * 检查是否忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE)
                as? android.os.PowerManager ?: return false
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * 获取服务组件名
     */
    fun getAccessibilityServiceComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, ACCESSIBILITY_SERVICE_CLASS)
    }

    /**
     * 释放资源
     */
    fun release() {
        val context = contextRef?.get()
        if (context != null) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            accessibilityStateListener?.let { am?.removeAccessibilityStateChangeListener(it) }
        }
        accessibilityStateListener = null
        contextRef = null
        Logger.d(TAG) { "ServiceManager 已释放" }
    }

    /**
     * 生成权限状态报告
     */
    fun generateStatusReport(): String {
        val state = _serviceState.value
        return buildString {
            appendLine("===== 服务状态报告 =====")
            appendLine("无障碍服务: ${if (state.isAccessibilityEnabled) "✓ 已启用" else "✗ 未启用"}")
            appendLine("悬浮窗权限: ${if (state.isOverlayEnabled) "✓ 已授权" else "✗ 未授权"}")
            appendLine("通知权限: ${if (state.isNotificationEnabled) "✓ 已授权" else "⚠ 未授权"}")
            appendLine()
            if (state.missingPermissions.isNotEmpty()) {
                appendLine("缺少权限: ${state.missingPermissions.joinToString(", ")}")
            } else {
                appendLine("所有必要权限已就绪")
            }
            appendLine("========================")
        }
    }
}
