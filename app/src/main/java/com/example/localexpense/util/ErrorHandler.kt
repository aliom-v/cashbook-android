package com.example.localexpense.util

import android.content.Context
import android.database.sqlite.SQLiteException
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.BadPaddingException

/**
 * 统一错误处理工具类
 *
 * 功能：
 * 1. 将技术异常转换为用户友好的提示信息
 * 2. 根据异常类型决定是否需要重试
 * 3. 提供统一的错误日志记录
 * 4. 支持错误回调和 Toast 提示
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * 错误类型枚举
     */
    enum class ErrorType {
        DATABASE,       // 数据库错误
        NETWORK,        // 网络错误
        IO,             // 文件IO错误
        CRYPTO,         // 加解密错误
        VALIDATION,     // 输入验证错误
        PERMISSION,     // 权限错误
        MEMORY,         // 内存不足
        BACKUP,         // 备份/恢复错误
        UNKNOWN         // 未知错误
    }

    /**
     * 错误信息数据类
     */
    data class ErrorInfo(
        val type: ErrorType,
        val userMessage: String,      // 展示给用户的消息
        val technicalMessage: String, // 技术详情（用于日志）
        val isRetryable: Boolean      // 是否可重试
    )

    /**
     * 分析异常并返回错误信息
     */
    fun analyze(throwable: Throwable): ErrorInfo {
        return when (throwable) {
            // 协程取消不是错误
            is CancellationException -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                userMessage = "",
                technicalMessage = "协程已取消",
                isRetryable = false
            )

            // 数据库错误
            is SQLiteException -> ErrorInfo(
                type = ErrorType.DATABASE,
                userMessage = "数据保存失败，请稍后重试",
                technicalMessage = "SQLite异常: ${throwable.message}",
                isRetryable = isRetryableSqliteException(throwable)
            )
            is android.database.sqlite.SQLiteDatabaseLockedException -> ErrorInfo(
                type = ErrorType.DATABASE,
                userMessage = "数据库繁忙，请稍后重试",
                technicalMessage = "数据库锁定: ${throwable.message}",
                isRetryable = true
            )

            // 网络错误
            is UnknownHostException -> ErrorInfo(
                type = ErrorType.NETWORK,
                userMessage = "网络连接失败，请检查网络设置",
                technicalMessage = "DNS解析失败: ${throwable.message}",
                isRetryable = true
            )
            is SocketTimeoutException -> ErrorInfo(
                type = ErrorType.NETWORK,
                userMessage = "网络连接超时，请稍后重试",
                technicalMessage = "Socket超时: ${throwable.message}",
                isRetryable = true
            )

            // IO错误
            is java.io.FileNotFoundException -> ErrorInfo(
                type = ErrorType.IO,
                userMessage = "文件不存在",
                technicalMessage = "文件未找到: ${throwable.message}",
                isRetryable = false
            )
            is IOException -> ErrorInfo(
                type = ErrorType.IO,
                userMessage = "文件操作失败，请检查存储空间",
                technicalMessage = "IO异常: ${throwable.message}",
                isRetryable = false
            )

            // 加解密错误
            is BadPaddingException -> ErrorInfo(
                type = ErrorType.CRYPTO,
                userMessage = "解密失败，密码可能不正确",
                technicalMessage = "解密填充错误: ${throwable.message}",
                isRetryable = false
            )
            is java.security.GeneralSecurityException -> ErrorInfo(
                type = ErrorType.CRYPTO,
                userMessage = "加密操作失败",
                technicalMessage = "安全异常: ${throwable.message}",
                isRetryable = false
            )

            // 验证错误
            is IllegalArgumentException -> ErrorInfo(
                type = ErrorType.VALIDATION,
                userMessage = throwable.message ?: "输入数据无效",
                technicalMessage = "参数错误: ${throwable.message}",
                isRetryable = false
            )

            // 非法状态（通常是程序逻辑错误）
            is IllegalStateException -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                userMessage = "操作状态异常，请刷新后重试",
                technicalMessage = "状态异常: ${throwable.message}",
                isRetryable = true
            )

            // 空指针（通常是数据加载问题）
            is NullPointerException -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                userMessage = "数据加载失败，请刷新后重试",
                technicalMessage = "空指针: ${throwable.message}",
                isRetryable = true
            )

            // 数组越界
            is IndexOutOfBoundsException -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                userMessage = "数据处理错误，请刷新后重试",
                technicalMessage = "索引越界: ${throwable.message}",
                isRetryable = true
            )

            // 权限错误
            is SecurityException -> ErrorInfo(
                type = ErrorType.PERMISSION,
                userMessage = "权限不足，请检查应用权限设置",
                technicalMessage = "安全异常: ${throwable.message}",
                isRetryable = false
            )

            // 内存错误
            is OutOfMemoryError -> ErrorInfo(
                type = ErrorType.MEMORY,
                userMessage = "内存不足，请关闭其他应用后重试",
                technicalMessage = "内存溢出: ${throwable.message}",
                isRetryable = false
            )

            // 其他错误 - 根据消息内容判断
            else -> analyzeByMessage(throwable)
        }
    }

    /**
     * 根据错误消息内容分析错误类型
     */
    private fun analyzeByMessage(throwable: Throwable): ErrorInfo {
        val message = throwable.message?.lowercase() ?: ""

        return when {
            // 备份相关错误
            message.contains("备份") || message.contains("backup") -> ErrorInfo(
                type = ErrorType.BACKUP,
                userMessage = throwable.message ?: "备份操作失败",
                technicalMessage = "${throwable::class.simpleName}: ${throwable.message}",
                isRetryable = false
            )

            // 校验和错误
            message.contains("校验") || message.contains("checksum") -> ErrorInfo(
                type = ErrorType.BACKUP,
                userMessage = "数据校验失败，文件可能已损坏",
                technicalMessage = "校验失败: ${throwable.message}",
                isRetryable = false
            )

            // 密码错误
            message.contains("密码") || message.contains("password") -> ErrorInfo(
                type = ErrorType.CRYPTO,
                userMessage = throwable.message ?: "密码错误或不符合要求",
                technicalMessage = "密码相关错误: ${throwable.message}",
                isRetryable = false
            )

            // 存储空间不足
            message.contains("no space") || message.contains("存储空间") -> ErrorInfo(
                type = ErrorType.IO,
                userMessage = "存储空间不足，请清理后重试",
                technicalMessage = "存储空间不足: ${throwable.message}",
                isRetryable = false
            )

            // 文件过大
            message.contains("过大") || message.contains("too large") -> ErrorInfo(
                type = ErrorType.IO,
                userMessage = "文件过大，无法处理",
                technicalMessage = "文件过大: ${throwable.message}",
                isRetryable = false
            )

            // 版本不兼容
            message.contains("版本") || message.contains("version") -> ErrorInfo(
                type = ErrorType.VALIDATION,
                userMessage = throwable.message ?: "版本不兼容，请更新应用",
                technicalMessage = "版本错误: ${throwable.message}",
                isRetryable = false
            )

            // 默认错误
            else -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                userMessage = "操作失败，请稍后重试",
                technicalMessage = "${throwable::class.simpleName}: ${throwable.message}",
                isRetryable = false
            )
        }
    }

    /**
     * 判断 SQLite 异常是否可重试
     */
    private fun isRetryableSqliteException(e: SQLiteException): Boolean {
        val message = e.message ?: return false
        return message.contains("SQLITE_BUSY", ignoreCase = true) ||
                message.contains("SQLITE_LOCKED", ignoreCase = true) ||
                message.contains("database is locked", ignoreCase = true)
    }

    /**
     * 处理异常并记录日志
     */
    fun handle(
        throwable: Throwable,
        context: String = "",
        showToast: Boolean = false,
        toastContext: Context? = null
    ): ErrorInfo {
        // 协程取消不需要处理
        if (throwable is CancellationException) {
            return analyze(throwable)
        }

        val errorInfo = analyze(throwable)

        // 记录日志
        val logMessage = if (context.isNotEmpty()) {
            "[$context] ${errorInfo.technicalMessage}"
        } else {
            errorInfo.technicalMessage
        }

        when (errorInfo.type) {
            ErrorType.VALIDATION -> Logger.w(TAG, logMessage)
            else -> Logger.e(TAG, logMessage, throwable)
        }

        // 显示 Toast（如果需要）
        if (showToast && toastContext != null && errorInfo.userMessage.isNotEmpty()) {
            showToastOnMainThread(toastContext, errorInfo.userMessage)
        }

        return errorInfo
    }

    /**
     * 在主线程显示 Toast
     */
    private fun showToastOnMainThread(context: Context, message: String) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 安全执行代码块，捕获异常并处理
     */
    inline fun <T> runCatching(
        context: String = "",
        default: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e // 不捕获协程取消
        } catch (e: Exception) {
            handle(e, context)
            default
        }
    }

    /**
     * 安全执行挂起代码块
     */
    suspend inline fun <T> runCatchingSuspend(
        context: String = "",
        default: T? = null,
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handle(e, context)
            default
        }
    }

    /**
     * 创建用户友好的错误消息
     * 用于需要自定义消息格式的场景
     */
    fun formatUserMessage(errorInfo: ErrorInfo, operation: String = ""): String {
        return if (operation.isNotEmpty()) {
            "${operation}失败：${errorInfo.userMessage}"
        } else {
            errorInfo.userMessage
        }
    }

    /**
     * 判断是否应该重试
     */
    fun shouldRetry(throwable: Throwable, retryCount: Int, maxRetries: Int = 3): Boolean {
        if (retryCount >= maxRetries) return false
        val errorInfo = analyze(throwable)
        return errorInfo.isRetryable
    }
}
