package com.example.localexpense.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * 监听设置管理
 * 控制自动记账的监听状态和开始时间
 * 线程安全的单例实现
 */
object MonitorSettings {
    private const val PREFS_NAME = "monitor_settings"
    private const val KEY_MONITOR_ENABLED = "monitor_enabled"
    private const val KEY_MONITOR_START_TIME = "monitor_start_time"
    private const val KEY_MIN_AMOUNT = "min_amount"
    private const val KEY_DEBUG_LOG_ENABLED = "debug_log_enabled"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val lock = Any()

    private fun getPrefs(context: Context): SharedPreferences {
        // 双重检查锁定，确保线程安全
        return prefs ?: synchronized(lock) {
            prefs ?: context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .also { prefs = it }
        }
    }

    /**
     * 监听是否已启用
     */
    fun isMonitorEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MONITOR_ENABLED, false)
    }

    /**
     * 设置监听状态
     * @param enabled true=开始监听，false=停止监听
     */
    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_MONITOR_ENABLED, enabled)
            if (enabled) {
                // 开启时记录当前时间
                putLong(KEY_MONITOR_START_TIME, System.currentTimeMillis())
            }
            apply()
        }
    }

    /**
     * 获取监听开始时间
     * @return 开始时间戳，如果未开启返回0
     */
    fun getMonitorStartTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_MONITOR_START_TIME, 0)
    }

    /**
     * 获取格式化的开始时间
     */
    fun getFormattedStartTime(context: Context): String {
        val startTime = getMonitorStartTime(context)
        if (startTime == 0L) return "未开始"
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(startTime))
    }

    /**
     * 检查交易时间是否在监听开始时间之后（或在允许的误差范围内）
     *
     * 注意：允许监听启动前的微小误差是设计决策，原因：
     * 1. 系统时钟可能有轻微偏差
     * 2. 交易时间和监听启动时间可能在不同线程获取，存在微小时差
     * 3. 避免刚启动时的交易被误过滤
     *
     * @param context Context
     * @param transactionTime 交易时间戳
     * @return true 表示交易在监听范围内，应该记录
     */
    fun isAfterMonitorStart(context: Context, transactionTime: Long): Boolean {
        if (!isMonitorEnabled(context)) return false
        val startTime = getMonitorStartTime(context)
        // 允许500ms的系统时钟误差（降低误接受启动前交易的风险）
        return transactionTime >= (startTime - 500)
    }

    /**
     * 最小记录金额
     */
    fun getMinAmount(context: Context): Double {
        return getPrefs(context).getFloat(KEY_MIN_AMOUNT, 0.01f).toDouble()
    }

    fun setMinAmount(context: Context, amount: Double) {
        getPrefs(context).edit().putFloat(KEY_MIN_AMOUNT, amount.toFloat()).apply()
    }

    /**
     * 调试日志是否启用
     * 启用后会输出更详细的日志信息，包括识别的文本和金额
     */
    fun isDebugLogEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEBUG_LOG_ENABLED, false)
    }

    fun setDebugLogEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEBUG_LOG_ENABLED, enabled).apply()
    }
}
