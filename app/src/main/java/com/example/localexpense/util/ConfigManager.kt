package com.example.localexpense.util

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

/**
 * 应用配置管理器
 *
 * 功能：
 * 1. 集中管理应用配置
 * 2. 响应式配置更新
 * 3. 配置持久化（使用 SecurePreferences）
 * 4. 配置变更监听
 *
 * 配置项：
 * - 自动记录开关
 * - 确认弹窗开关
 * - 通知开关
 * - OCR 开关
 * - 主题设置
 * - 预算提醒阈值
 */
object ConfigManager {

    private const val TAG = "ConfigManager"

    // Context 引用
    private var contextRef: WeakReference<Context>? = null

    // 配置状态
    private val _config = MutableStateFlow(AppConfig())
    val config: Flow<AppConfig> = _config.asStateFlow()

    // 当前配置快照
    val currentConfig: AppConfig get() = _config.value

    /**
     * 应用配置数据类
     */
    data class AppConfig(
        // 自动记录设置
        val autoRecordEnabled: Boolean = true,
        val showConfirmDialog: Boolean = true,
        val showNotification: Boolean = true,

        // OCR 设置
        val ocrEnabled: Boolean = true,
        val ocrAsBackup: Boolean = true, // OCR 仅作为备用方案

        // 预算设置
        val budgetWarningThreshold: Int = 80, // 预算警告阈值（百分比）
        val budgetNotificationEnabled: Boolean = true,

        // 重复检测设置
        val duplicateCheckEnabled: Boolean = true,
        val duplicateCheckIntervalMs: Long = Constants.DUPLICATE_CHECK_INTERVAL_MS,

        // 数据保留设置
        val dataRetentionDays: Int = 365, // 数据保留天数，0 表示永久

        // UI 设置
        val theme: Theme = Theme.SYSTEM,
        val hapticFeedbackEnabled: Boolean = true,

        // 隐私设置
        val maskAmountsInLog: Boolean = true,
        val collectCrashReports: Boolean = true
    ) {
        enum class Theme {
            LIGHT, DARK, SYSTEM
        }
    }

    // 配置键名
    private object Keys {
        const val AUTO_RECORD_ENABLED = "auto_record_enabled"
        const val SHOW_CONFIRM_DIALOG = "show_confirm_dialog"
        const val SHOW_NOTIFICATION = "show_notification"
        const val OCR_ENABLED = "ocr_enabled"
        const val OCR_AS_BACKUP = "ocr_as_backup"
        const val BUDGET_WARNING_THRESHOLD = "budget_warning_threshold"
        const val BUDGET_NOTIFICATION_ENABLED = "budget_notification_enabled"
        const val DUPLICATE_CHECK_ENABLED = "duplicate_check_enabled"
        const val DUPLICATE_CHECK_INTERVAL = "duplicate_check_interval"
        const val DATA_RETENTION_DAYS = "data_retention_days"
        const val THEME = "theme"
        const val HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        const val MASK_AMOUNTS_IN_LOG = "mask_amounts_in_log"
        const val COLLECT_CRASH_REPORTS = "collect_crash_reports"
    }

    /**
     * 初始化配置管理器
     */
    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        loadConfig()
        Logger.i(TAG, "ConfigManager 初始化完成")
    }

    /**
     * 从持久化存储加载配置
     */
    private fun loadConfig() {
        val context = contextRef?.get() ?: return

        try {
            _config.value = AppConfig(
                autoRecordEnabled = SecurePreferences.getBoolean(
                    context, Keys.AUTO_RECORD_ENABLED, true
                ),
                showConfirmDialog = SecurePreferences.getBoolean(
                    context, Keys.SHOW_CONFIRM_DIALOG, true
                ),
                showNotification = SecurePreferences.getBoolean(
                    context, Keys.SHOW_NOTIFICATION, true
                ),
                ocrEnabled = SecurePreferences.getBoolean(
                    context, Keys.OCR_ENABLED, true
                ),
                ocrAsBackup = SecurePreferences.getBoolean(
                    context, Keys.OCR_AS_BACKUP, true
                ),
                budgetWarningThreshold = SecurePreferences.getInt(
                    context, Keys.BUDGET_WARNING_THRESHOLD, 80
                ),
                budgetNotificationEnabled = SecurePreferences.getBoolean(
                    context, Keys.BUDGET_NOTIFICATION_ENABLED, true
                ),
                duplicateCheckEnabled = SecurePreferences.getBoolean(
                    context, Keys.DUPLICATE_CHECK_ENABLED, true
                ),
                duplicateCheckIntervalMs = SecurePreferences.getLong(
                    context, Keys.DUPLICATE_CHECK_INTERVAL, Constants.DUPLICATE_CHECK_INTERVAL_MS
                ),
                dataRetentionDays = SecurePreferences.getInt(
                    context, Keys.DATA_RETENTION_DAYS, 365
                ),
                theme = AppConfig.Theme.valueOf(
                    SecurePreferences.getString(context, Keys.THEME, AppConfig.Theme.SYSTEM.name)
                        ?: AppConfig.Theme.SYSTEM.name
                ),
                hapticFeedbackEnabled = SecurePreferences.getBoolean(
                    context, Keys.HAPTIC_FEEDBACK_ENABLED, true
                ),
                maskAmountsInLog = SecurePreferences.getBoolean(
                    context, Keys.MASK_AMOUNTS_IN_LOG, true
                ),
                collectCrashReports = SecurePreferences.getBoolean(
                    context, Keys.COLLECT_CRASH_REPORTS, true
                )
            )
            Logger.d(TAG) { "配置已加载" }
        } catch (e: Exception) {
            Logger.e(TAG, "加载配置失败，使用默认值", e)
            _config.value = AppConfig()
        }
    }

    /**
     * 保存配置到持久化存储
     */
    private fun saveConfig(config: AppConfig) {
        val context = contextRef?.get() ?: return

        try {
            SecurePreferences.putBoolean(context, Keys.AUTO_RECORD_ENABLED, config.autoRecordEnabled)
            SecurePreferences.putBoolean(context, Keys.SHOW_CONFIRM_DIALOG, config.showConfirmDialog)
            SecurePreferences.putBoolean(context, Keys.SHOW_NOTIFICATION, config.showNotification)
            SecurePreferences.putBoolean(context, Keys.OCR_ENABLED, config.ocrEnabled)
            SecurePreferences.putBoolean(context, Keys.OCR_AS_BACKUP, config.ocrAsBackup)
            SecurePreferences.putInt(context, Keys.BUDGET_WARNING_THRESHOLD, config.budgetWarningThreshold)
            SecurePreferences.putBoolean(context, Keys.BUDGET_NOTIFICATION_ENABLED, config.budgetNotificationEnabled)
            SecurePreferences.putBoolean(context, Keys.DUPLICATE_CHECK_ENABLED, config.duplicateCheckEnabled)
            SecurePreferences.putLong(context, Keys.DUPLICATE_CHECK_INTERVAL, config.duplicateCheckIntervalMs)
            SecurePreferences.putInt(context, Keys.DATA_RETENTION_DAYS, config.dataRetentionDays)
            SecurePreferences.putString(context, Keys.THEME, config.theme.name)
            SecurePreferences.putBoolean(context, Keys.HAPTIC_FEEDBACK_ENABLED, config.hapticFeedbackEnabled)
            SecurePreferences.putBoolean(context, Keys.MASK_AMOUNTS_IN_LOG, config.maskAmountsInLog)
            SecurePreferences.putBoolean(context, Keys.COLLECT_CRASH_REPORTS, config.collectCrashReports)

            Logger.d(TAG) { "配置已保存" }
        } catch (e: Exception) {
            Logger.e(TAG, "保存配置失败", e)
        }
    }

    /**
     * 更新配置
     */
    fun updateConfig(updater: (AppConfig) -> AppConfig) {
        _config.update { current ->
            val newConfig = updater(current)
            saveConfig(newConfig)
            newConfig
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 设置自动记录开关
     */
    fun setAutoRecordEnabled(enabled: Boolean) {
        updateConfig { it.copy(autoRecordEnabled = enabled) }
    }

    /**
     * 设置确认弹窗开关
     */
    fun setShowConfirmDialog(show: Boolean) {
        updateConfig { it.copy(showConfirmDialog = show) }
    }

    /**
     * 设置通知开关
     */
    fun setShowNotification(show: Boolean) {
        updateConfig { it.copy(showNotification = show) }
    }

    /**
     * 设置 OCR 开关
     */
    fun setOcrEnabled(enabled: Boolean) {
        updateConfig { it.copy(ocrEnabled = enabled) }
    }

    /**
     * 设置主题
     */
    fun setTheme(theme: AppConfig.Theme) {
        updateConfig { it.copy(theme = theme) }
    }

    /**
     * 设置预算警告阈值
     */
    fun setBudgetWarningThreshold(threshold: Int) {
        val validThreshold = threshold.coerceIn(50, 100)
        updateConfig { it.copy(budgetWarningThreshold = validThreshold) }
    }

    /**
     * 设置数据保留天数
     */
    fun setDataRetentionDays(days: Int) {
        val validDays = if (days <= 0) 0 else days.coerceIn(30, 3650)
        updateConfig { it.copy(dataRetentionDays = validDays) }
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        val defaultConfig = AppConfig()
        _config.value = defaultConfig
        saveConfig(defaultConfig)
        Logger.i(TAG, "配置已重置为默认值")
    }

    /**
     * 导出配置为 Map（用于备份）
     */
    fun exportConfig(): Map<String, Any> {
        val config = _config.value
        return mapOf(
            Keys.AUTO_RECORD_ENABLED to config.autoRecordEnabled,
            Keys.SHOW_CONFIRM_DIALOG to config.showConfirmDialog,
            Keys.SHOW_NOTIFICATION to config.showNotification,
            Keys.OCR_ENABLED to config.ocrEnabled,
            Keys.OCR_AS_BACKUP to config.ocrAsBackup,
            Keys.BUDGET_WARNING_THRESHOLD to config.budgetWarningThreshold,
            Keys.BUDGET_NOTIFICATION_ENABLED to config.budgetNotificationEnabled,
            Keys.DUPLICATE_CHECK_ENABLED to config.duplicateCheckEnabled,
            Keys.DUPLICATE_CHECK_INTERVAL to config.duplicateCheckIntervalMs,
            Keys.DATA_RETENTION_DAYS to config.dataRetentionDays,
            Keys.THEME to config.theme.name,
            Keys.HAPTIC_FEEDBACK_ENABLED to config.hapticFeedbackEnabled,
            Keys.MASK_AMOUNTS_IN_LOG to config.maskAmountsInLog,
            Keys.COLLECT_CRASH_REPORTS to config.collectCrashReports
        )
    }

    /**
     * 从 Map 导入配置（用于恢复）
     */
    fun importConfig(configMap: Map<String, Any>) {
        try {
            val newConfig = AppConfig(
                autoRecordEnabled = configMap[Keys.AUTO_RECORD_ENABLED] as? Boolean ?: true,
                showConfirmDialog = configMap[Keys.SHOW_CONFIRM_DIALOG] as? Boolean ?: true,
                showNotification = configMap[Keys.SHOW_NOTIFICATION] as? Boolean ?: true,
                ocrEnabled = configMap[Keys.OCR_ENABLED] as? Boolean ?: true,
                ocrAsBackup = configMap[Keys.OCR_AS_BACKUP] as? Boolean ?: true,
                budgetWarningThreshold = (configMap[Keys.BUDGET_WARNING_THRESHOLD] as? Number)?.toInt() ?: 80,
                budgetNotificationEnabled = configMap[Keys.BUDGET_NOTIFICATION_ENABLED] as? Boolean ?: true,
                duplicateCheckEnabled = configMap[Keys.DUPLICATE_CHECK_ENABLED] as? Boolean ?: true,
                duplicateCheckIntervalMs = (configMap[Keys.DUPLICATE_CHECK_INTERVAL] as? Number)?.toLong()
                    ?: Constants.DUPLICATE_CHECK_INTERVAL_MS,
                dataRetentionDays = (configMap[Keys.DATA_RETENTION_DAYS] as? Number)?.toInt() ?: 365,
                theme = try {
                    AppConfig.Theme.valueOf(configMap[Keys.THEME] as? String ?: AppConfig.Theme.SYSTEM.name)
                } catch (e: Exception) {
                    AppConfig.Theme.SYSTEM
                },
                hapticFeedbackEnabled = configMap[Keys.HAPTIC_FEEDBACK_ENABLED] as? Boolean ?: true,
                maskAmountsInLog = configMap[Keys.MASK_AMOUNTS_IN_LOG] as? Boolean ?: true,
                collectCrashReports = configMap[Keys.COLLECT_CRASH_REPORTS] as? Boolean ?: true
            )

            _config.value = newConfig
            saveConfig(newConfig)
            Logger.i(TAG, "配置已导入")
        } catch (e: Exception) {
            Logger.e(TAG, "导入配置失败", e)
        }
    }

    /**
     * 生成配置报告
     */
    fun generateReport(): String {
        val config = _config.value
        return buildString {
            appendLine("===== 应用配置 =====")
            appendLine()
            appendLine("【记录设置】")
            appendLine("  自动记录: ${if (config.autoRecordEnabled) "开启" else "关闭"}")
            appendLine("  确认弹窗: ${if (config.showConfirmDialog) "开启" else "关闭"}")
            appendLine("  通知提醒: ${if (config.showNotification) "开启" else "关闭"}")
            appendLine()
            appendLine("【OCR 设置】")
            appendLine("  OCR 识别: ${if (config.ocrEnabled) "开启" else "关闭"}")
            appendLine("  仅作备用: ${if (config.ocrAsBackup) "是" else "否"}")
            appendLine()
            appendLine("【预算设置】")
            appendLine("  警告阈值: ${config.budgetWarningThreshold}%")
            appendLine("  预算通知: ${if (config.budgetNotificationEnabled) "开启" else "关闭"}")
            appendLine()
            appendLine("【数据设置】")
            appendLine("  重复检测: ${if (config.duplicateCheckEnabled) "开启" else "关闭"}")
            appendLine("  检测间隔: ${config.duplicateCheckIntervalMs / 1000}秒")
            appendLine("  数据保留: ${if (config.dataRetentionDays == 0) "永久" else "${config.dataRetentionDays}天"}")
            appendLine()
            appendLine("【界面设置】")
            appendLine("  主题: ${config.theme.name}")
            appendLine("  触觉反馈: ${if (config.hapticFeedbackEnabled) "开启" else "关闭"}")
            appendLine()
            appendLine("【隐私设置】")
            appendLine("  金额脱敏: ${if (config.maskAmountsInLog) "开启" else "关闭"}")
            appendLine("  崩溃收集: ${if (config.collectCrashReports) "开启" else "关闭"}")
            appendLine("====================")
        }
    }
}
