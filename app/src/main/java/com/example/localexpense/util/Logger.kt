package com.example.localexpense.util

import android.util.Log
import com.example.localexpense.BuildConfig

/**
 * 统一日志工具类
 * 生产环境自动禁用 Debug/Verbose 级别日志
 */
object Logger {
    private const val TAG = "LocalExpense"

    private val isDebug = BuildConfig.DEBUG

    fun v(tag: String = TAG, message: () -> String) {
        if (isDebug) Log.v(tag, message())
    }

    fun d(tag: String = TAG, message: () -> String) {
        if (isDebug) Log.d(tag, message())
    }

    fun i(tag: String = TAG, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * 带标签的扩展函数
     */
    inline fun withTag(tag: String, block: TaggedLogger.() -> Unit) {
        TaggedLogger(tag).block()
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
