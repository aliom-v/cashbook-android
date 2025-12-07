package com.example.localexpense.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.localexpense.data.AppDatabase
import com.example.localexpense.ocr.OcrParser
import com.example.localexpense.parser.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 应用健康检查工具
 *
 * 功能：
 * 1. 检查数据库状态
 * 2. 检查服务状态（无障碍服务）
 * 3. 检查权限状态
 * 4. 检查内存状态
 * 5. 检查存储状态
 * 6. 检查 OCR 可用性
 * 7. 检查规则引擎状态
 *
 * 使用场景：
 * - 应用启动时自检
 * - 设置页面展示状态
 * - 问题排查
 * - 用户反馈时收集信息
 */
object AppHealthChecker {

    private const val TAG = "AppHealthChecker"

    // 阈值常量
    private const val MIN_FREE_STORAGE_MB = 50L      // 最小可用存储空间
    private const val MIN_FREE_MEMORY_MB = 30L       // 最小可用内存
    private const val LOW_MEMORY_THRESHOLD = 0.15    // 低内存阈值（15%）

    /**
     * 健康检查结果
     */
    data class HealthReport(
        val timestamp: Long = System.currentTimeMillis(),
        val overallStatus: Status,
        val checks: List<CheckResult>,
        val summary: String
    ) {
        enum class Status {
            HEALTHY,    // 健康
            WARNING,    // 警告（部分功能可能受影响）
            CRITICAL    // 严重（核心功能不可用）
        }

        override fun toString(): String {
            return buildString {
                appendLine("===== 应用健康报告 =====")
                appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(timestamp)}")
                appendLine("状态: ${overallStatus.name}")
                appendLine()
                appendLine("检查项:")
                checks.forEach { check ->
                    val icon = when (check.status) {
                        CheckStatus.PASS -> "✓"
                        CheckStatus.WARNING -> "⚠"
                        CheckStatus.FAIL -> "✗"
                    }
                    appendLine("  $icon ${check.name}: ${check.message}")
                }
                appendLine()
                appendLine("总结: $summary")
                appendLine("========================")
            }
        }
    }

    /**
     * 单项检查结果
     */
    data class CheckResult(
        val name: String,
        val status: CheckStatus,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )

    enum class CheckStatus {
        PASS,       // 通过
        WARNING,    // 警告
        FAIL        // 失败
    }

    /**
     * 执行完整健康检查
     */
    suspend fun performFullCheck(context: Context): HealthReport = withContext(Dispatchers.IO) {
        Logger.d(TAG) { "开始健康检查" }
        val startTime = System.currentTimeMillis()

        val checks = mutableListOf<CheckResult>()

        // 1. 检查无障碍服务
        checks.add(checkAccessibilityService(context))

        // 2. 检查悬浮窗权限
        checks.add(checkOverlayPermission(context))

        // 3. 检查数据库状态
        checks.add(checkDatabase(context))

        // 4. 检查内存状态
        checks.add(checkMemory(context))

        // 5. 检查存储空间
        checks.add(checkStorage(context))

        // 6. 检查 OCR 服务
        checks.add(checkOcrService())

        // 7. 检查规则引擎
        checks.add(checkRuleEngine())

        // 8. 检查通知权限
        checks.add(checkNotificationPermission(context))

        // 计算总体状态
        val failCount = checks.count { it.status == CheckStatus.FAIL }
        val warningCount = checks.count { it.status == CheckStatus.WARNING }

        val overallStatus = when {
            failCount > 0 -> HealthReport.Status.CRITICAL
            warningCount > 0 -> HealthReport.Status.WARNING
            else -> HealthReport.Status.HEALTHY
        }

        val summary = when (overallStatus) {
            HealthReport.Status.HEALTHY -> "所有检查项通过，应用运行正常"
            HealthReport.Status.WARNING -> "${warningCount}项警告，部分功能可能受影响"
            HealthReport.Status.CRITICAL -> "${failCount}项失败，核心功能不可用"
        }

        val elapsed = System.currentTimeMillis() - startTime
        Logger.d(TAG) { "健康检查完成，耗时 ${elapsed}ms，状态: ${overallStatus.name}" }

        HealthReport(
            overallStatus = overallStatus,
            checks = checks,
            summary = summary
        )
    }

    /**
     * 快速检查（仅检查核心功能）
     */
    suspend fun performQuickCheck(context: Context): HealthReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<CheckResult>()

        // 只检查核心功能
        checks.add(checkAccessibilityService(context))
        checks.add(checkOverlayPermission(context))
        checks.add(checkDatabase(context))

        val failCount = checks.count { it.status == CheckStatus.FAIL }
        val warningCount = checks.count { it.status == CheckStatus.WARNING }

        val overallStatus = when {
            failCount > 0 -> HealthReport.Status.CRITICAL
            warningCount > 0 -> HealthReport.Status.WARNING
            else -> HealthReport.Status.HEALTHY
        }

        HealthReport(
            overallStatus = overallStatus,
            checks = checks,
            summary = if (overallStatus == HealthReport.Status.HEALTHY) "核心功能正常" else "存在问题"
        )
    }

    /**
     * 检查无障碍服务
     */
    private fun checkAccessibilityService(context: Context): CheckResult {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am == null) {
                return CheckResult(
                    name = "无障碍服务",
                    status = CheckStatus.FAIL,
                    message = "无法获取无障碍服务管理器"
                )
            }

            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            val ourServiceEnabled = enabledServices.any { info ->
                info.resolveInfo?.serviceInfo?.packageName == context.packageName
            }

            if (ourServiceEnabled) {
                CheckResult(
                    name = "无障碍服务",
                    status = CheckStatus.PASS,
                    message = "已启用",
                    details = mapOf("enabled_services" to enabledServices.size)
                )
            } else {
                CheckResult(
                    name = "无障碍服务",
                    status = CheckStatus.FAIL,
                    message = "未启用，无法自动识别账单"
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "检查无障碍服务失败", e)
            CheckResult(
                name = "无障碍服务",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(context: Context): CheckResult {
        return try {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

            if (hasPermission) {
                CheckResult(
                    name = "悬浮窗权限",
                    status = CheckStatus.PASS,
                    message = "已授权"
                )
            } else {
                CheckResult(
                    name = "悬浮窗权限",
                    status = CheckStatus.WARNING,
                    message = "未授权，无法显示确认窗口"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                name = "悬浮窗权限",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 检查数据库状态
     */
    private suspend fun checkDatabase(context: Context): CheckResult {
        return try {
            val db = AppDatabase.getInstance(context)

            // 尝试执行简单查询
            val count = db.expenseDao().getExpenseCount()

            // 检查数据库文件
            val dbFile = context.getDatabasePath("local_expense_database")
            val dbSize = if (dbFile.exists()) dbFile.length() / 1024 else 0 // KB

            CheckResult(
                name = "数据库",
                status = CheckStatus.PASS,
                message = "正常 (${count}条记录, ${dbSize}KB)",
                details = mapOf(
                    "record_count" to count,
                    "size_kb" to dbSize
                )
            )
        } catch (e: Exception) {
            Logger.e(TAG, "数据库检查失败", e)
            CheckResult(
                name = "数据库",
                status = CheckStatus.FAIL,
                message = "异常: ${e.message}"
            )
        }
    }

    /**
     * 检查内存状态
     */
    private fun checkMemory(context: Context): CheckResult {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager == null) {
                return CheckResult(
                    name = "内存",
                    status = CheckStatus.WARNING,
                    message = "无法获取内存信息"
                )
            }

            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalMB = memInfo.totalMem / (1024 * 1024)
            val availMB = memInfo.availMem / (1024 * 1024)
            val usedPercent = ((totalMB - availMB) * 100 / totalMB).toInt()

            val runtime = Runtime.getRuntime()
            val appMaxMB = runtime.maxMemory() / (1024 * 1024)
            val appUsedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

            val status = when {
                memInfo.lowMemory -> CheckStatus.FAIL
                availMB < MIN_FREE_MEMORY_MB -> CheckStatus.WARNING
                usedPercent > 85 -> CheckStatus.WARNING
                else -> CheckStatus.PASS
            }

            val message = when (status) {
                CheckStatus.PASS -> "正常 (可用 ${availMB}MB, 应用 ${appUsedMB}MB/${appMaxMB}MB)"
                CheckStatus.WARNING -> "内存较低 (可用 ${availMB}MB)"
                CheckStatus.FAIL -> "内存不足，可能影响性能"
            }

            CheckResult(
                name = "内存",
                status = status,
                message = message,
                details = mapOf(
                    "total_mb" to totalMB,
                    "available_mb" to availMB,
                    "used_percent" to usedPercent,
                    "app_used_mb" to appUsedMB,
                    "low_memory" to memInfo.lowMemory
                )
            )
        } catch (e: Exception) {
            CheckResult(
                name = "内存",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 检查存储空间
     */
    private fun checkStorage(context: Context): CheckResult {
        return try {
            val dataDir = context.filesDir
            val stat = StatFs(dataDir.path)

            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableMB = availableBytes / (1024 * 1024)
            val totalMB = totalBytes / (1024 * 1024)
            val usedPercent = ((totalMB - availableMB) * 100 / totalMB).toInt()

            val status = when {
                availableMB < MIN_FREE_STORAGE_MB -> CheckStatus.FAIL
                availableMB < MIN_FREE_STORAGE_MB * 2 -> CheckStatus.WARNING
                else -> CheckStatus.PASS
            }

            val message = when (status) {
                CheckStatus.PASS -> "正常 (可用 ${availableMB}MB)"
                CheckStatus.WARNING -> "存储空间较低 (可用 ${availableMB}MB)"
                CheckStatus.FAIL -> "存储空间不足 (可用 ${availableMB}MB)"
            }

            CheckResult(
                name = "存储空间",
                status = status,
                message = message,
                details = mapOf(
                    "available_mb" to availableMB,
                    "total_mb" to totalMB,
                    "used_percent" to usedPercent
                )
            )
        } catch (e: Exception) {
            CheckResult(
                name = "存储空间",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 检查 OCR 服务
     */
    private fun checkOcrService(): CheckResult {
        return try {
            val isAvailable = OcrParser.isAvailable()
            val stats = OcrParser.getStats()

            if (isAvailable) {
                CheckResult(
                    name = "OCR 服务",
                    status = CheckStatus.PASS,
                    message = "可用 (成功率 ${String.format("%.1f", stats.successRate)}%)",
                    details = mapOf(
                        "success_count" to stats.successCount,
                        "failure_count" to stats.failureCount,
                        "success_rate" to stats.successRate
                    )
                )
            } else {
                CheckResult(
                    name = "OCR 服务",
                    status = CheckStatus.WARNING,
                    message = "不可用，将使用节点解析"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                name = "OCR 服务",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 检查规则引擎
     */
    private fun checkRuleEngine(): CheckResult {
        return try {
            val isInitialized = RuleEngine.isInitialized()

            if (isInitialized) {
                val stats = RuleEngine.getStats()
                CheckResult(
                    name = "规则引擎",
                    status = CheckStatus.PASS,
                    message = "已加载 ${stats.ruleCount} 条规则",
                    details = mapOf(
                        "rule_count" to stats.ruleCount,
                        "app_count" to stats.appCount
                    )
                )
            } else {
                CheckResult(
                    name = "规则引擎",
                    status = CheckStatus.WARNING,
                    message = "未初始化，将使用传统解析"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                name = "规则引擎",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission(context: Context): CheckResult {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? android.app.NotificationManager

            if (notificationManager == null) {
                return CheckResult(
                    name = "通知权限",
                    status = CheckStatus.WARNING,
                    message = "无法检查"
                )
            }

            val areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.areNotificationsEnabled()
            } else {
                true
            }

            if (areNotificationsEnabled) {
                CheckResult(
                    name = "通知权限",
                    status = CheckStatus.PASS,
                    message = "已授权"
                )
            } else {
                CheckResult(
                    name = "通知权限",
                    status = CheckStatus.WARNING,
                    message = "未授权，无法接收服务通知"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                name = "通知权限",
                status = CheckStatus.WARNING,
                message = "检查失败: ${e.message}"
            )
        }
    }

    /**
     * 获取设备信息（用于问题排查）
     */
    fun getDeviceInfo(context: Context): Map<String, String> {
        return mapOf(
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "sdk_version" to Build.VERSION.SDK_INT.toString(),
            "android_version" to Build.VERSION.RELEASE,
            "app_version" to try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                "${pInfo.versionName} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode})"
            } catch (e: Exception) {
                "unknown"
            }
        )
    }

    /**
     * 生成诊断报告（用于用户反馈）
     */
    suspend fun generateDiagnosticReport(context: Context): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()

        report.appendLine("===== 诊断报告 =====")
        report.appendLine()

        // 设备信息
        report.appendLine("【设备信息】")
        getDeviceInfo(context).forEach { (key, value) ->
            report.appendLine("  $key: $value")
        }
        report.appendLine()

        // 健康检查
        report.appendLine("【健康检查】")
        val healthReport = performFullCheck(context)
        healthReport.checks.forEach { check ->
            val icon = when (check.status) {
                CheckStatus.PASS -> "✓"
                CheckStatus.WARNING -> "⚠"
                CheckStatus.FAIL -> "✗"
            }
            report.appendLine("  $icon ${check.name}: ${check.message}")
        }
        report.appendLine()

        // 性能统计
        report.appendLine("【性能统计】")
        val perfReport = PerformanceMonitor.generateReport()
        report.appendLine(perfReport)

        report.appendLine("===================")

        report.toString()
    }
}
