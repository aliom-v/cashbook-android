package com.example.localexpense.util

import android.util.Log
import com.example.localexpense.BuildConfig

/**
 * 统一日志工具类
 *
 * 安全特性：
 * 1. 生产环境自动禁用 Debug/Verbose 级别日志
 * 2. 提供敏感信息遮蔽功能
 * 3. Release 版本中异常堆栈不输出详情
 */
object Logger {
    private const val TAG = "LocalExpense"

    val isDebug = BuildConfig.DEBUG

    // 是否允许详细日志（可在运行时调整，用于特殊调试场景）
    @Volatile
    var verboseLogging = BuildConfig.DEBUG

    fun v(tag: String = TAG, message: () -> String) {
        if (verboseLogging) Log.v(tag, message())
    }

    fun d(tag: String = TAG, message: () -> String) {
        if (isDebug) Log.d(tag, message())
    }

    fun i(tag: String = TAG, message: String) {
        // Release 版本中由 ProGuard 移除
        Log.i(tag, message)
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null && isDebug) {
            // Debug 模式输出完整堆栈
            Log.w(tag, message, throwable)
        } else {
            // Release 模式只输出消息，不输出堆栈详情
            Log.w(tag, message)
        }
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null && isDebug) {
            // Debug 模式输出完整堆栈
            Log.e(tag, message, throwable)
        } else {
            // Release 模式只输出消息
            Log.e(tag, message)
        }
    }

    /**
     * 带标签的扩展函数
     */
    inline fun withTag(tag: String, block: TaggedLogger.() -> Unit) {
        TaggedLogger(tag).block()
    }

    // ========== 敏感信息遮蔽工具 ==========

    /**
     * 遮蔽金额（仅保留整数部分首位）
     * 例：123.45 -> 1**.**
     */
    fun maskAmount(amount: Double): String {
        if (!isDebug) return "***"
        val str = "%.2f".format(amount)
        return if (str.length > 1) str[0] + "*".repeat(str.length - 1) else "***"
    }

    /**
     * 遮蔽商户名称（仅保留首字）
     * 例：星巴克 -> 星**
     */
    fun maskMerchant(merchant: String): String {
        if (!isDebug) return "***"
        return if (merchant.isNotEmpty()) merchant[0] + "*".repeat((merchant.length - 1).coerceAtMost(5)) else "***"
    }

    /**
     * 遮蔽文本（用于原始识别文本等）
     */
    fun maskText(text: String, visibleChars: Int = 3): String {
        if (!isDebug) return "[已隐藏]"
        return if (text.length > visibleChars) {
            text.take(visibleChars) + "..." + "[${text.length}字符]"
        } else {
            text
        }
    }
}

/**
 * 带标签的日志器
 */
class TaggedLogger(private val tag: String) {
    fun v(message: () -> String) = Logger.v(tag, message)
    fun d(message: () -> String) = Logger.d(tag, message)
    fun i(message: String) = Logger.i(tag, message)
    fun w(message: String, throwable: Throwable? = null) = Logger.w(tag, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = Logger.e(tag, message, throwable)
}
