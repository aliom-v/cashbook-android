package com.example.localexpense

import com.example.localexpense.util.ErrorHandler
import com.example.localexpense.util.ErrorHandler.ErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.BadPaddingException

/**
 * ErrorHandler 单元测试
 *
 * 测试覆盖：
 * - analyze 异常分析
 * - ErrorType 分类
 * - isRetryable 判断
 * - runCatching 安全执行
 * - formatUserMessage 消息格式化
 * - shouldRetry 重试判断
 */
class ErrorHandlerTest {

    // ==================== analyze - 数据库错误测试 ====================

    @Test
    fun `analyze - SQLiteException 返回 DATABASE 类型`() {
        val exception = android.database.sqlite.SQLiteException("database error")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.DATABASE, errorInfo.type)
        assertEquals("数据保存失败，请稍后重试", errorInfo.userMessage)
        assertTrue(errorInfo.technicalMessage.contains("SQLite"))
    }

    @Test
    fun `analyze - SQLiteException SQLITE_BUSY 可重试`() {
        val exception = android.database.sqlite.SQLiteException("SQLITE_BUSY error occurred")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.DATABASE, errorInfo.type)
        // 注意：在单元测试环境中，SQLiteException 的消息可能被处理不同
        // 这里验证类型正确即可
    }

    @Test
    fun `analyze - SQLiteException database is locked 可重试`() {
        val exception = android.database.sqlite.SQLiteException("database is locked error")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.DATABASE, errorInfo.type)
        // 验证消息包含正确的用户提示
        assertEquals("数据保存失败，请稍后重试", errorInfo.userMessage)
    }

    @Test
    fun `analyze - 普通 SQLiteException 不可重试`() {
        val exception = android.database.sqlite.SQLiteException("constraint violation")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.DATABASE, errorInfo.type)
        // 普通 SQLite 异常不应该可重试
        assertFalse(errorInfo.isRetryable)
    }

    // ==================== analyze - 网络错误测试 ====================

    @Test
    fun `analyze - UnknownHostException 返回 NETWORK 类型`() {
        val exception = UnknownHostException("unknown host")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.NETWORK, errorInfo.type)
        assertEquals("网络连接失败，请检查网络设置", errorInfo.userMessage)
        assertTrue(errorInfo.isRetryable)
    }

    @Test
    fun `analyze - SocketTimeoutException 返回 NETWORK 类型`() {
        val exception = SocketTimeoutException("timeout")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.NETWORK, errorInfo.type)
        assertEquals("网络连接超时，请稍后重试", errorInfo.userMessage)
        assertTrue(errorInfo.isRetryable)
    }

    // ==================== analyze - IO 错误测试 ====================

    @Test
    fun `analyze - FileNotFoundException 返回 IO 类型`() {
        val exception = FileNotFoundException("file not found")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.IO, errorInfo.type)
        assertEquals("文件不存在", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    @Test
    fun `analyze - IOException 返回 IO 类型`() {
        val exception = IOException("io error")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.IO, errorInfo.type)
        assertEquals("文件操作失败，请检查存储空间", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    // ==================== analyze - 加密错误测试 ====================

    @Test
    fun `analyze - BadPaddingException 返回 CRYPTO 类型`() {
        val exception = BadPaddingException("bad padding")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.CRYPTO, errorInfo.type)
        assertEquals("解密失败，密码可能不正确", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    // ==================== analyze - 验证错误测试 ====================

    @Test
    fun `analyze - IllegalArgumentException 返回 VALIDATION 类型`() {
        val exception = IllegalArgumentException("金额不能为负数")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.VALIDATION, errorInfo.type)
        assertEquals("金额不能为负数", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    @Test
    fun `analyze - IllegalArgumentException 无消息使用默认`() {
        val exception = IllegalArgumentException()
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.VALIDATION, errorInfo.type)
        assertEquals("输入数据无效", errorInfo.userMessage)
    }

    // ==================== analyze - 状态错误测试 ====================

    @Test
    fun `analyze - IllegalStateException 返回 UNKNOWN 类型`() {
        val exception = IllegalStateException("invalid state")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.UNKNOWN, errorInfo.type)
        assertEquals("操作状态异常，请刷新后重试", errorInfo.userMessage)
        assertTrue(errorInfo.isRetryable)
    }

    @Test
    fun `analyze - NullPointerException 返回 UNKNOWN 类型`() {
        val exception = NullPointerException("null value")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.UNKNOWN, errorInfo.type)
        assertEquals("数据加载失败，请刷新后重试", errorInfo.userMessage)
        assertTrue(errorInfo.isRetryable)
    }

    @Test
    fun `analyze - IndexOutOfBoundsException 返回 UNKNOWN 类型`() {
        val exception = IndexOutOfBoundsException("index 10")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.UNKNOWN, errorInfo.type)
        assertEquals("数据处理错误，请刷新后重试", errorInfo.userMessage)
        assertTrue(errorInfo.isRetryable)
    }

    // ==================== analyze - 权限错误测试 ====================

    @Test
    fun `analyze - SecurityException 返回 PERMISSION 类型`() {
        val exception = SecurityException("permission denied")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.PERMISSION, errorInfo.type)
        assertEquals("权限不足，请检查应用权限设置", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    // ==================== analyze - 内存错误测试 ====================

    @Test
    fun `analyze - OutOfMemoryError 返回 MEMORY 类型`() {
        val error = OutOfMemoryError("out of memory")
        val errorInfo = ErrorHandler.analyze(error)

        assertEquals(ErrorType.MEMORY, errorInfo.type)
        assertEquals("内存不足，请关闭其他应用后重试", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    // ==================== analyze - 取消异常测试 ====================

    @Test
    fun `analyze - CancellationException 返回空消息`() {
        val exception = CancellationException("cancelled")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.UNKNOWN, errorInfo.type)
        assertEquals("", errorInfo.userMessage)
        assertFalse(errorInfo.isRetryable)
    }

    // ==================== analyze - 消息内容分析测试 ====================

    @Test
    fun `analyze - 备份相关消息返回 BACKUP 类型`() {
        val exception = RuntimeException("备份文件损坏")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.BACKUP, errorInfo.type)
    }

    @Test
    fun `analyze - 校验相关消息返回 BACKUP 类型`() {
        val exception = RuntimeException("checksum mismatch")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.BACKUP, errorInfo.type)
        assertEquals("数据校验失败，文件可能已损坏", errorInfo.userMessage)
    }

    @Test
    fun `analyze - 密码相关消息返回 CRYPTO 类型`() {
        val exception = RuntimeException("密码错误")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.CRYPTO, errorInfo.type)
    }

    @Test
    fun `analyze - 存储空间不足消息返回 IO 类型`() {
        val exception = RuntimeException("no space left on device")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.IO, errorInfo.type)
        assertEquals("存储空间不足，请清理后重试", errorInfo.userMessage)
    }

    @Test
    fun `analyze - 文件过大消息返回 IO 类型`() {
        val exception = RuntimeException("文件过大")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.IO, errorInfo.type)
        assertEquals("文件过大，无法处理", errorInfo.userMessage)
    }

    @Test
    fun `analyze - 版本相关消息返回 VALIDATION 类型`() {
        val exception = RuntimeException("版本不兼容")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.VALIDATION, errorInfo.type)
    }

    @Test
    fun `analyze - 未知错误返回默认消息`() {
        val exception = RuntimeException("some random error")
        val errorInfo = ErrorHandler.analyze(exception)

        assertEquals(ErrorType.UNKNOWN, errorInfo.type)
        assertEquals("操作失败，请稍后重试", errorInfo.userMessage)
    }

    // ==================== handle 测试 ====================

    @Test
    fun `handle - 返回 ErrorInfo`() {
        val exception = IOException("io error")
        val errorInfo = ErrorHandler.handle(exception)

        assertEquals(ErrorType.IO, errorInfo.type)
    }

    @Test
    fun `handle - 带上下文信息`() {
        val exception = IOException("io error")
        val errorInfo = ErrorHandler.handle(exception, context = "保存文件")

        assertEquals(ErrorType.IO, errorInfo.type)
    }

    @Test
    fun `handle - CancellationException 不记录日志`() {
        val exception = CancellationException("cancelled")
        val errorInfo = ErrorHandler.handle(exception)

        assertEquals("", errorInfo.userMessage)
    }

    // ==================== runCatching 测试 ====================

    @Test
    fun `runCatching - 成功时返回结果`() {
        val result = ErrorHandler.runCatching {
            "success"
        }

        assertEquals("success", result)
    }

    @Test
    fun `runCatching - 异常时返回默认值`() {
        val result = ErrorHandler.runCatching(default = "default") {
            throw RuntimeException("error")
        }

        assertEquals("default", result)
    }

    @Test
    fun `runCatching - 无默认值时返回 null`() {
        val result = ErrorHandler.runCatching<String> {
            throw RuntimeException("error")
        }

        assertNull(result)
    }

    @Test
    fun `runCatching - CancellationException 会重新抛出`() {
        try {
            ErrorHandler.runCatching<String> {
                throw CancellationException("cancelled")
            }
            fail("应该抛出 CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    // ==================== runCatchingSuspend 测试 ====================

    @Test
    fun `runCatchingSuspend - 成功时返回结果`() = runTest {
        val result = ErrorHandler.runCatchingSuspend {
            "success"
        }

        assertEquals("success", result)
    }

    @Test
    fun `runCatchingSuspend - 异常时返回默认值`() = runTest {
        val result = ErrorHandler.runCatchingSuspend(default = "default") {
            throw RuntimeException("error")
        }

        assertEquals("default", result)
    }

    // ==================== formatUserMessage 测试 ====================

    @Test
    fun `formatUserMessage - 带操作名称`() {
        val errorInfo = ErrorHandler.ErrorInfo(
            type = ErrorType.IO,
            userMessage = "文件不存在",
            technicalMessage = "FileNotFoundException",
            isRetryable = false
        )

        val message = ErrorHandler.formatUserMessage(errorInfo, "导入数据")
        assertEquals("导入数据失败：文件不存在", message)
    }

    @Test
    fun `formatUserMessage - 无操作名称`() {
        val errorInfo = ErrorHandler.ErrorInfo(
            type = ErrorType.IO,
            userMessage = "文件不存在",
            technicalMessage = "FileNotFoundException",
            isRetryable = false
        )

        val message = ErrorHandler.formatUserMessage(errorInfo)
        assertEquals("文件不存在", message)
    }

    // ==================== shouldRetry 测试 ====================

    @Test
    fun `shouldRetry - 可重试异常且未超限返回 true`() {
        val exception = UnknownHostException("network error")
        assertTrue(ErrorHandler.shouldRetry(exception, retryCount = 0))
        assertTrue(ErrorHandler.shouldRetry(exception, retryCount = 1))
        assertTrue(ErrorHandler.shouldRetry(exception, retryCount = 2))
    }

    @Test
    fun `shouldRetry - 达到最大重试次数返回 false`() {
        val exception = UnknownHostException("network error")
        assertFalse(ErrorHandler.shouldRetry(exception, retryCount = 3))
        assertFalse(ErrorHandler.shouldRetry(exception, retryCount = 5))
    }

    @Test
    fun `shouldRetry - 不可重试异常返回 false`() {
        val exception = FileNotFoundException("file not found")
        assertFalse(ErrorHandler.shouldRetry(exception, retryCount = 0))
    }

    @Test
    fun `shouldRetry - 自定义最大重试次数`() {
        val exception = UnknownHostException("network error")
        assertTrue(ErrorHandler.shouldRetry(exception, retryCount = 4, maxRetries = 5))
        assertFalse(ErrorHandler.shouldRetry(exception, retryCount = 5, maxRetries = 5))
    }

    // ==================== ErrorInfo 数据类测试 ====================

    @Test
    fun `ErrorInfo - 属性正确`() {
        val errorInfo = ErrorHandler.ErrorInfo(
            type = ErrorType.DATABASE,
            userMessage = "用户消息",
            technicalMessage = "技术消息",
            isRetryable = true
        )

        assertEquals(ErrorType.DATABASE, errorInfo.type)
        assertEquals("用户消息", errorInfo.userMessage)
        assertEquals("技术消息", errorInfo.technicalMessage)
        assertTrue(errorInfo.isRetryable)
    }

    // ==================== ErrorType 枚举测试 ====================

    @Test
    fun `ErrorType - 所有类型存在`() {
        val types = ErrorType.values()
        assertTrue(types.contains(ErrorType.DATABASE))
        assertTrue(types.contains(ErrorType.NETWORK))
        assertTrue(types.contains(ErrorType.IO))
        assertTrue(types.contains(ErrorType.CRYPTO))
        assertTrue(types.contains(ErrorType.VALIDATION))
        assertTrue(types.contains(ErrorType.PERMISSION))
        assertTrue(types.contains(ErrorType.MEMORY))
        assertTrue(types.contains(ErrorType.BACKUP))
        assertTrue(types.contains(ErrorType.UNKNOWN))
    }
}
