package com.example.localexpense

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.example.localexpense.data.TransactionRepository

class LocalExpenseApp : Application() {

    companion object {
        private const val TAG = "LocalExpenseApp"
    }

    override fun onCreate() {
        super.onCreate()

        // 设置全局异常处理器
        setupGlobalExceptionHandler()

        // 仅在 Debug 模式下启用 StrictMode
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 清理 Repository 的协程作用域，防止资源泄漏
        try {
            TransactionRepository.getInstance(this).shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "关闭 Repository 失败: ${e.message}")
        }
    }

    /**
     * 设置全局异常处理器，防止未捕获异常导致闪退
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "未捕获异常: ${throwable.message}", throwable)
            // 调用默认处理器（通常会导致应用退出）
            defaultHandler?.uncaughtException(thread, throwable)
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
