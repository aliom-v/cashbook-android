package com.example.localexpense

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.util.CryptoUtils
import com.example.localexpense.util.Logger
import com.example.localexpense.util.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用主入口
 *
 * 启动优化策略：
 * 1. 关键初始化同步执行（加密模块）
 * 2. 非关键初始化延迟到后台线程
 * 3. 使用 PerformanceMonitor 追踪启动时间
 */
class LocalExpenseApp : Application() {

    companion object {
        private const val TAG = "LocalExpenseApp"

        // 全局应用实例（用于需要 Context 的地方）
        @Volatile
        private var instance: LocalExpenseApp? = null

        fun getInstance(): LocalExpenseApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    // 应用级协程作用域（用于后台初始化任务）
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 标记是否完成初始化
    @Volatile
    private var isFullyInitialized = false

    override fun onCreate() {
        // 记录启动开始时间
        val startTime = PerformanceMonitor.startTimer("应用启动")

        super.onCreate()
        instance = this

        // ===== 同步初始化（关键组件，必须在主线程完成）=====
        initCriticalComponents()

        // ===== 异步初始化（非关键组件，后台执行）=====
        initNonCriticalComponentsAsync()

        // 设置全局异常处理器
        setupGlobalExceptionHandler()

        // 仅在 Debug 模式下启用 StrictMode
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        // 记录启动完成时间
        PerformanceMonitor.endTimer("应用启动", startTime)

        if (BuildConfig.DEBUG) {
            Logger.i(TAG, "应用启动完成")
        }
    }

    /**
     * 初始化关键组件（同步，主线程）
     * 只包含必须在应用启动时立即可用的组件
     */
    private fun initCriticalComponents() {
        val startTime = PerformanceMonitor.startTimer("关键组件初始化")

        try {
            // 初始化加密模块（用于 rawText 加密存储）
            // 必须同步执行，因为后续数据读写都依赖它
            CryptoUtils.init(this)
        } catch (e: Exception) {
            Logger.e(TAG, "加密模块初始化失败", e)
            // 不抛出异常，允许应用继续启动（使用降级模式）
        }

        PerformanceMonitor.endTimer("关键组件初始化", startTime)
    }

    /**
     * 初始化非关键组件（异步，后台线程）
     * 延迟加载以加快启动速度
     */
    private fun initNonCriticalComponentsAsync() {
        applicationScope.launch {
            val startTime = PerformanceMonitor.startTimer("后台初始化")

            try {
                // 预热 Repository（触发数据库初始化）
                // 这会在后台创建数据库连接和默认分类
                TransactionRepository.getInstance(applicationContext).waitForInitialization()

                // 预热 SecurePreferences（触发 MasterKey 创建）
                // 首次访问可能需要生成密钥，耗时较长
                com.example.localexpense.util.SecurePreferences.getInstance(applicationContext)

                isFullyInitialized = true

                if (BuildConfig.DEBUG) {
                    Logger.i(TAG, "后台初始化完成")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "后台初始化失败", e)
            }

            PerformanceMonitor.endTimer("后台初始化", startTime)
        }
    }

    /**
     * 检查是否完全初始化
     */
    fun isFullyInitialized(): Boolean = isFullyInitialized

    override fun onTerminate() {
        super.onTerminate()
        // 清理 Repository 的协程作用域，防止资源泄漏
        try {
            TransactionRepository.getInstance(this).shutdown()
        } catch (e: Exception) {
            Logger.e(TAG, "关闭 Repository 失败", e)
        }
    }

    /**
     * 低内存回调 - 清理缓存
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Logger.w(TAG, "收到低内存警告，清理缓存")

        // 清理去重检测器缓存
        com.example.localexpense.util.DuplicateChecker.getInstance().clear()

        // 触发 GC
        System.gc()
    }

    /**
     * 内存压力回调
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                Logger.w(TAG, "内存严重不足 (level=$level)")
                com.example.localexpense.util.DuplicateChecker.getInstance().clear()
            }
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_RUNNING_LOW -> {
                Logger.w(TAG, "内存不足 (level=$level)")
            }
            TRIM_MEMORY_UI_HIDDEN -> {
                // 应用进入后台，可以释放一些 UI 相关缓存
                if (BuildConfig.DEBUG) {
                    Logger.d(TAG) { "应用进入后台" }
                }
            }
        }
    }

    /**
     * 设置全局异常处理器，防止未捕获异常导致闪退
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.e(TAG, "未捕获异常 [${thread.name}]: ${throwable.message}", throwable)

            // 记录崩溃信息（可以扩展为保存到文件或上报）
            saveCrashInfo(throwable)

            // 调用默认处理器（通常会导致应用退出）
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 保存崩溃信息（可扩展）
     */
    private fun saveCrashInfo(throwable: Throwable) {
        try {
            // 记录关键信息用于诊断
            val crashInfo = buildString {
                appendLine("===== Crash Report =====")
                appendLine("Time: ${System.currentTimeMillis()}")
                appendLine("Exception: ${throwable::class.java.simpleName}")
                appendLine("Message: ${throwable.message}")
                appendLine("Stack trace:")
                appendLine(throwable.stackTraceToString().take(2000))
            }

            // 保存到 SharedPreferences（下次启动时可读取）
            getSharedPreferences("crash_report", MODE_PRIVATE)
                .edit()
                .putString("last_crash", crashInfo)
                .putLong("crash_time", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // 保存崩溃信息失败，忽略
        }
    }

    /**
     * 启用 StrictMode 检测潜在问题
     * - 主线程 IO 操作
     * - 主线程网络请求
     * - 资源泄漏
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        detectNonSdkApiUsage()
                    }
                }
                .penaltyLog()
                .build()
        )
    }
}
