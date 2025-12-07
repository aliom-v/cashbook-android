package com.example.localexpense.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * 协程辅助工具类
 *
 * 功能：
 * 1. 全局异常处理器
 * 2. 安全的协程作用域
 * 3. Flow 异常处理扩展
 * 4. 上下文切换工具
 */
object CoroutineHelper {

    @PublishedApi
    internal const val TAG = "CoroutineHelper"

    /**
     * 全局协程异常处理器
     * 处理未捕获的协程异常
     */
    val globalExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        // 忽略取消异常
        if (throwable is CancellationException) return@CoroutineExceptionHandler

        Logger.e(TAG, "协程异常 [${context[kotlinx.coroutines.CoroutineName]?.name ?: "unnamed"}]", throwable)

        // 记录到性能监控
        PerformanceMonitor.increment("协程异常数")
    }

    /**
     * 创建带异常处理的协程作用域
     *
     * @param dispatcher 调度器，默认为 IO
     * @param onError 自定义错误处理回调
     */
    fun createSafeScope(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        onError: ((Throwable) -> Unit)? = null
    ): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException) return@CoroutineExceptionHandler

            Logger.e(TAG, "协程作用域异常", throwable)
            onError?.invoke(throwable)
        }

        return CoroutineScope(SupervisorJob() + dispatcher + handler)
    }

    /**
     * 安全执行挂起函数
     * 自动捕获异常并返回 Result
     */
    suspend inline fun <T> runSafely(
        crossinline block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e // 不捕获取消异常
        } catch (e: Exception) {
            Logger.e(TAG, "安全执行异常", e)
            Result.failure(e)
        }
    }

    /**
     * 安全执行挂起函数，带默认值
     */
    suspend inline fun <T> runSafelyWithDefault(
        default: T,
        crossinline block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "安全执行异常，使用默认值", e)
            default
        }
    }

    /**
     * 在 IO 线程安全执行
     */
    suspend inline fun <T> withIO(crossinline block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.IO) {
            block()
        }
    }

    /**
     * 在主线程安全执行
     */
    suspend inline fun <T> withMain(crossinline block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.Main) {
            block()
        }
    }

    /**
     * 在 Default 线程安全执行
     */
    suspend inline fun <T> withDefault(crossinline block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.Default) {
            block()
        }
    }
}

/**
 * Flow 扩展：安全收集
 * 自动捕获异常并处理
 */
fun <T> Flow<T>.catchAndLog(
    tag: String = "Flow",
    onError: ((Throwable) -> Unit)? = null
): Flow<T> {
    return this.catch { e ->
        if (e is CancellationException) throw e
        Logger.e(tag, "Flow 异常", e)
        onError?.invoke(e)
    }
}

/**
 * Flow 扩展：在 IO 线程执行
 */
fun <T> Flow<T>.onIO(): Flow<T> = flowOn(Dispatchers.IO)

/**
 * Flow 扩展：在 Default 线程执行
 */
fun <T> Flow<T>.onDefault(): Flow<T> = flowOn(Dispatchers.Default)

/**
 * 安全的协程上下文
 * 包含异常处理器和 SupervisorJob
 */
fun safeCoroutineContext(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    name: String? = null
): CoroutineContext {
    val handler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        Logger.e("SafeContext", "协程异常 [$name]", throwable)
    }

    return SupervisorJob() + dispatcher + handler + (name?.let {
        kotlinx.coroutines.CoroutineName(it)
    } ?: kotlinx.coroutines.CoroutineName("unnamed"))
}
