package com.example.localexpense.util

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 崩溃报告收集器
 *
 * 功能：
 * 1. 捕获未处理异常
 * 2. 收集设备和应用信息
 * 3. 保存崩溃日志到本地
 * 4. 支持用户反馈时附带崩溃信息
 *
 * 使用方式：
 * 在 Application.onCreate() 中调用 CrashReporter.init(this)
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 10
    private const val CRASH_FILE_PREFIX = "crash_"
    private const val CRASH_FILE_SUFFIX = ".txt"

    private var contextRef: WeakReference<Context>? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private val isInitialized = AtomicBoolean(false)

    // 崩溃回调（可选，用于自定义处理）
    private var onCrashCallback: ((CrashInfo) -> Unit)? = null

    /**
     * 初始化崩溃收集器
     *
     * @param context 应用上下文
     * @param onCrash 崩溃回调（可选）
     */
    fun init(context: Context, onCrash: ((CrashInfo) -> Unit)? = null) {
        if (!isInitialized.compareAndSet(false, true)) {
            Logger.w(TAG, "CrashReporter 已初始化，忽略重复调用")
            return
        }

        contextRef = WeakReference(context.applicationContext)
        onCrashCallback = onCrash

        // 保存默认处理器
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 设置自定义处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }

        Logger.i(TAG, "CrashReporter 初始化成功")

        // 清理旧的崩溃文件
        cleanOldCrashFiles()
    }

    /**
     * 处理未捕获异常
     */
    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val context = contextRef?.get()
            if (context != null) {
                val crashInfo = collectCrashInfo(context, thread, throwable)

                // 保存崩溃日志
                saveCrashToFile(context, crashInfo)

                // 触发回调
                onCrashCallback?.invoke(crashInfo)

                Logger.e(TAG, "捕获未处理异常", throwable)
            }
        } catch (e: Exception) {
            // 崩溃处理本身出错，忽略
        } finally {
            // 调用默认处理器（通常会终止应用）
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 收集崩溃信息
     */
    private fun collectCrashInfo(context: Context, thread: Thread, throwable: Throwable): CrashInfo {
        val stackTrace = getStackTraceString(throwable)
        val timestamp = System.currentTimeMillis()

        return CrashInfo(
            timestamp = timestamp,
            threadName = thread.name,
            exceptionClass = throwable.javaClass.name,
            exceptionMessage = throwable.message ?: "无消息",
            stackTrace = stackTrace,
            deviceInfo = collectDeviceInfo(context),
            appInfo = collectAppInfo(context),
            memoryInfo = collectMemoryInfo()
        )
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    /**
     * 收集设备信息
     */
    private fun collectDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            sdkVersion = Build.VERSION.SDK_INT,
            androidVersion = Build.VERSION.RELEASE,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            screenDensity = context.resources.displayMetrics.densityDpi,
            screenResolution = "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}"
        )
    }

    /**
     * 收集应用信息
     */
    private fun collectAppInfo(context: Context): AppInfo {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            AppInfo(
                packageName = context.packageName,
                versionName = pInfo.versionName ?: "unknown",
                versionCode = versionCode,
                firstInstallTime = pInfo.firstInstallTime,
                lastUpdateTime = pInfo.lastUpdateTime
            )
        } catch (e: Exception) {
            AppInfo(
                packageName = context.packageName,
                versionName = "unknown",
                versionCode = 0,
                firstInstallTime = 0,
                lastUpdateTime = 0
            )
        }
    }

    /**
     * 收集内存信息
     */
    private fun collectMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        return MemoryInfo(
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            maxMemory = runtime.maxMemory(),
            usedMemory = runtime.totalMemory() - runtime.freeMemory()
        )
    }

    /**
     * 保存崩溃信息到文件
     */
    private fun saveCrashToFile(context: Context, crashInfo: CrashInfo) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "$CRASH_FILE_PREFIX${dateFormat.format(Date(crashInfo.timestamp))}$CRASH_FILE_SUFFIX"
            val crashFile = File(crashDir, fileName)

            crashFile.writeText(crashInfo.toReportString())
            Logger.i(TAG, "崩溃日志已保存: ${crashFile.name}")
        } catch (e: Exception) {
            Logger.e(TAG, "保存崩溃日志失败", e)
        }
    }

    /**
     * 清理旧的崩溃文件
     */
    private fun cleanOldCrashFiles() {
        try {
            val context = contextRef?.get() ?: return
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) return

            val crashFiles = crashDir.listFiles { file ->
                file.name.startsWith(CRASH_FILE_PREFIX) && file.name.endsWith(CRASH_FILE_SUFFIX)
            }?.sortedByDescending { it.lastModified() } ?: return

            // 删除超出数量限制的旧文件
            if (crashFiles.size > MAX_CRASH_FILES) {
                crashFiles.drop(MAX_CRASH_FILES).forEach { file ->
                    file.delete()
                    Logger.d(TAG) { "删除旧崩溃日志: ${file.name}" }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "清理崩溃日志失败", e)
        }
    }

    /**
     * 获取所有崩溃报告
     */
    suspend fun getAllCrashReports(context: Context): List<CrashReport> = withContext(Dispatchers.IO) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) return@withContext emptyList()

            crashDir.listFiles { file ->
                file.name.startsWith(CRASH_FILE_PREFIX) && file.name.endsWith(CRASH_FILE_SUFFIX)
            }?.sortedByDescending { it.lastModified() }?.map { file ->
                CrashReport(
                    fileName = file.name,
                    timestamp = file.lastModified(),
                    size = file.length(),
                    content = file.readText()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "读取崩溃报告失败", e)
            emptyList()
        }
    }

    /**
     * 获取最近的崩溃报告
     */
    suspend fun getLatestCrashReport(context: Context): CrashReport? = withContext(Dispatchers.IO) {
        getAllCrashReports(context).firstOrNull()
    }

    /**
     * 删除所有崩溃报告
     */
    suspend fun clearAllCrashReports(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) return@withContext 0

            var count = 0
            crashDir.listFiles()?.forEach { file ->
                if (file.delete()) count++
            }
            Logger.i(TAG, "已清除 $count 个崩溃报告")
            count
        } catch (e: Exception) {
            Logger.e(TAG, "清除崩溃报告失败", e)
            0
        }
    }

    /**
     * 手动记录异常（非崩溃）
     */
    fun logException(throwable: Throwable, tag: String = TAG) {
        Logger.e(tag, "记录异常", throwable)

        val context = contextRef?.get() ?: return
        try {
            val crashInfo = CrashInfo(
                timestamp = System.currentTimeMillis(),
                threadName = Thread.currentThread().name,
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message ?: "无消息",
                stackTrace = getStackTraceString(throwable),
                deviceInfo = collectDeviceInfo(context),
                appInfo = collectAppInfo(context),
                memoryInfo = collectMemoryInfo(),
                isNonFatal = true
            )

            // 保存为非致命错误日志
            saveNonFatalError(context, crashInfo)
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 保存非致命错误
     */
    private fun saveNonFatalError(context: Context, crashInfo: CrashInfo) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "error_${dateFormat.format(Date(crashInfo.timestamp))}$CRASH_FILE_SUFFIX"
            val errorFile = File(crashDir, fileName)

            errorFile.writeText(crashInfo.toReportString())
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ==================== 数据类 ====================

    /**
     * 崩溃信息
     */
    data class CrashInfo(
        val timestamp: Long,
        val threadName: String,
        val exceptionClass: String,
        val exceptionMessage: String,
        val stackTrace: String,
        val deviceInfo: DeviceInfo,
        val appInfo: AppInfo,
        val memoryInfo: MemoryInfo,
        val isNonFatal: Boolean = false
    ) {
        fun toReportString(): String = buildString {
            appendLine("==================== ${if (isNonFatal) "错误报告" else "崩溃报告"} ====================")
            appendLine()
            appendLine("【时间】")
            appendLine("  ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))}")
            appendLine()
            appendLine("【异常信息】")
            appendLine("  线程: $threadName")
            appendLine("  类型: $exceptionClass")
            appendLine("  消息: $exceptionMessage")
            appendLine()
            appendLine("【堆栈跟踪】")
            appendLine(stackTrace)
            appendLine()
            appendLine("【设备信息】")
            appendLine("  品牌: ${deviceInfo.brand}")
            appendLine("  型号: ${deviceInfo.model}")
            appendLine("  设备: ${deviceInfo.device}")
            appendLine("  SDK: ${deviceInfo.sdkVersion}")
            appendLine("  Android: ${deviceInfo.androidVersion}")
            appendLine("  CPU: ${deviceInfo.cpuAbi}")
            appendLine("  屏幕: ${deviceInfo.screenResolution} (${deviceInfo.screenDensity}dpi)")
            appendLine()
            appendLine("【应用信息】")
            appendLine("  包名: ${appInfo.packageName}")
            appendLine("  版本: ${appInfo.versionName} (${appInfo.versionCode})")
            appendLine()
            appendLine("【内存信息】")
            appendLine("  已用: ${memoryInfo.usedMemory / 1024 / 1024}MB")
            appendLine("  可用: ${memoryInfo.freeMemory / 1024 / 1024}MB")
            appendLine("  总计: ${memoryInfo.totalMemory / 1024 / 1024}MB")
            appendLine("  最大: ${memoryInfo.maxMemory / 1024 / 1024}MB")
            appendLine()
            appendLine("============================================================")
        }
    }

    data class DeviceInfo(
        val brand: String,
        val model: String,
        val device: String,
        val sdkVersion: Int,
        val androidVersion: String,
        val cpuAbi: String,
        val screenDensity: Int,
        val screenResolution: String
    )

    data class AppInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val firstInstallTime: Long,
        val lastUpdateTime: Long
    )

    data class MemoryInfo(
        val totalMemory: Long,
        val freeMemory: Long,
        val maxMemory: Long,
        val usedMemory: Long
    )

    data class CrashReport(
        val fileName: String,
        val timestamp: Long,
        val size: Long,
        val content: String
    )
}
