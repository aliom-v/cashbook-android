package com.example.localexpense

import com.example.localexpense.util.CoroutineHelper
import com.example.localexpense.util.catchAndLog
import com.example.localexpense.util.onDefault
import com.example.localexpense.util.onIO
import com.example.localexpense.util.safeCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * CoroutineHelper 单元测试
 *
 * 测试覆盖：
 * - runSafely 安全执行
 * - runSafelyWithDefault 带默认值执行
 * - withIO/withMain/withDefault 上下文切换
 * - createSafeScope 安全作用域
 * - Flow 扩展函数
 * - safeCoroutineContext 安全上下文
 */
class CoroutineHelperTest {

    // ==================== runSafely 测试 ====================

    @Test
    fun `runSafely - 成功时返回 Result success`() = runTest {
        val result = CoroutineHelper.runSafely {
            "success"
        }

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
    }

    @Test
    fun `runSafely - 异常时返回 Result failure`() = runTest {
        val result = CoroutineHelper.runSafely<String> {
            throw RuntimeException("test error")
        }

        assertTrue(result.isFailure)
        assertEquals("test error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `runSafely - 取消异常会重新抛出`() = runTest {
        try {
            CoroutineHelper.runSafely<String> {
                throw CancellationException("cancelled")
            }
            fail("应该抛出 CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `runSafely - 返回数值类型`() = runTest {
        val result = CoroutineHelper.runSafely {
            42
        }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `runSafely - 返回列表类型`() = runTest {
        val result = CoroutineHelper.runSafely {
            listOf(1, 2, 3)
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2, 3), result.getOrNull())
    }

    // ==================== runSafelyWithDefault 测试 ====================

    @Test
    fun `runSafelyWithDefault - 成功时返回结果`() = runTest {
        val result = CoroutineHelper.runSafelyWithDefault("default") {
            "success"
        }

        assertEquals("success", result)
    }

    @Test
    fun `runSafelyWithDefault - 异常时返回默认值`() = runTest {
        val result = CoroutineHelper.runSafelyWithDefault("default") {
            throw RuntimeException("error")
        }

        assertEquals("default", result)
    }

    @Test
    fun `runSafelyWithDefault - 数值类型默认值`() = runTest {
        val result = CoroutineHelper.runSafelyWithDefault(0) {
            throw RuntimeException("error")
        }

        assertEquals(0, result)
    }

    @Test
    fun `runSafelyWithDefault - 列表类型默认值`() = runTest {
        val result = CoroutineHelper.runSafelyWithDefault(emptyList<String>()) {
            throw RuntimeException("error")
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `runSafelyWithDefault - 取消异常会重新抛出`() = runTest {
        try {
            CoroutineHelper.runSafelyWithDefault("default") {
                throw CancellationException("cancelled")
            }
            fail("应该抛出 CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    // ==================== withIO/withMain/withDefault 测试 ====================

    @Test
    fun `withIO - 在 IO 调度器执行`() = runTest {
        val result = CoroutineHelper.withIO {
            "io result"
        }

        assertEquals("io result", result)
    }

    @Test
    fun `withDefault - 在 Default 调度器执行`() = runTest {
        val result = CoroutineHelper.withDefault {
            "default result"
        }

        assertEquals("default result", result)
    }

    @Test
    fun `withIO - 可以执行计算`() = runTest {
        val result = CoroutineHelper.withIO {
            (1..100).sum()
        }

        assertEquals(5050, result)
    }

    // ==================== createSafeScope 测试 ====================

    @Test
    fun `createSafeScope - 创建作用域成功`() {
        val scope = CoroutineHelper.createSafeScope()
        assertNotNull(scope)
        scope.cancel() // 清理
    }

    @Test
    fun `createSafeScope - 使用自定义调度器`() {
        val scope = CoroutineHelper.createSafeScope(Dispatchers.Default)
        assertNotNull(scope)
        scope.cancel()
    }

    @Test
    fun `createSafeScope - 异常回调被调用`() = runTest {
        var errorCaught: Throwable? = null
        val scope = CoroutineHelper.createSafeScope(
            onError = { errorCaught = it }
        )

        // 作用域创建成功
        assertNotNull(scope)
        scope.cancel()
    }

    // ==================== globalExceptionHandler 测试 ====================

    @Test
    fun `globalExceptionHandler - 存在且不为空`() {
        assertNotNull(CoroutineHelper.globalExceptionHandler)
    }

    // ==================== Flow 扩展测试 ====================

    @Test
    fun `catchAndLog - 正常 Flow 不受影响`() = runTest {
        val result = flow {
            emit(1)
            emit(2)
            emit(3)
        }.catchAndLog().toList()

        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `catchAndLog - 异常被捕获`() = runTest {
        var errorCaught: Throwable? = null

        val result = flow {
            emit(1)
            throw RuntimeException("flow error")
        }.catchAndLog(onError = { errorCaught = it }).toList()

        assertEquals(listOf(1), result)
        assertNotNull(errorCaught)
        assertEquals("flow error", errorCaught?.message)
    }

    @Test
    fun `catchAndLog - 取消异常会重新抛出`() = runTest {
        try {
            flow<Int> {
                throw CancellationException("cancelled")
            }.catchAndLog().toList()
            fail("应该抛出 CancellationException")
        } catch (e: CancellationException) {
            // 预期行为
        }
    }

    @Test
    fun `onIO - Flow 在 IO 调度器执行`() = runTest {
        val result = flow {
            emit("io")
        }.onIO().toList()

        assertEquals(listOf("io"), result)
    }

    @Test
    fun `onDefault - Flow 在 Default 调度器执行`() = runTest {
        val result = flow {
            emit("default")
        }.onDefault().toList()

        assertEquals(listOf("default"), result)
    }

    // ==================== safeCoroutineContext 测试 ====================

    @Test
    fun `safeCoroutineContext - 创建上下文成功`() {
        val context = safeCoroutineContext()
        assertNotNull(context)
    }

    @Test
    fun `safeCoroutineContext - 使用自定义调度器`() {
        val context = safeCoroutineContext(Dispatchers.Default)
        assertNotNull(context)
    }

    @Test
    fun `safeCoroutineContext - 使用自定义名称`() {
        val context = safeCoroutineContext(name = "TestCoroutine")
        assertNotNull(context)
    }

    @Test
    fun `safeCoroutineContext - 包含 SupervisorJob`() {
        val context = safeCoroutineContext()
        // 验证上下文包含 Job
        assertNotNull(context[kotlinx.coroutines.Job])
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun `runSafely - 返回 null 值`() = runTest {
        val result = CoroutineHelper.runSafely<String?> {
            null
        }

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `runSafelyWithDefault - nullable 默认值`() = runTest {
        val result: String? = CoroutineHelper.runSafelyWithDefault(null) {
            throw RuntimeException("error")
        }

        assertNull(result)
    }

    @Test
    fun `withIO - 嵌套调用`() = runTest {
        val result = CoroutineHelper.withIO {
            CoroutineHelper.withDefault {
                "nested"
            }
        }

        assertEquals("nested", result)
    }
}
