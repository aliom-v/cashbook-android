package com.example.localexpense.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.example.localexpense.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 截图管理器
 *
 * 支持 Android 11+ 的 AccessibilityService.takeScreenshot() API
 * 用于 OCR 备用方案
 *
 * 使用限制：
 * 1. 仅支持 Android 11 (API 30) 及以上
 * 2. 需要无障碍服务权限
 * 3. 截图频率建议控制在 1次/秒以内
 *
 * 注意：
 * Android 10 及以下需要使用 MediaProjection API，需要用户授权，
 * 不适合自动记账场景，因此本实现仅支持 Android 11+
 */
class ScreenCaptureManager(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val SCREENSHOT_TIMEOUT_MS = 5000L  // 截图超时时间
        private const val MIN_INTERVAL_MS = 1000L        // 最小截图间隔（防止频繁截图）
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 防止频繁截图
    @Volatile
    private var lastCaptureTime = 0L

    // 标记是否正在截图
    private val isCapturing = AtomicBoolean(false)

    // 超时任务引用，用于在成功/失败时取消
    @Volatile
    private var timeoutRunnable: Runnable? = null

    /**
     * 截取当前屏幕
     *
     * @param callback 截图结果回调（在主线程调用）
     *                 成功返回 Bitmap，失败返回 null
     */
    fun captureScreen(callback: (Bitmap?) -> Unit) {
        // 1. 版本检查
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "截图功能仅支持 Android 11+，当前系统版本: ${Build.VERSION.SDK_INT}")
            }
            mainHandler.post { callback(null) }
            return
        }

        // 2. 频率限制
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < MIN_INTERVAL_MS) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "截图频率过高，已忽略（需间隔 ${MIN_INTERVAL_MS}ms）")
            }
            mainHandler.post { callback(null) }
            return
        }

        // 3. 并发控制
        if (!isCapturing.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "已有截图任务在进行中")
            }
            mainHandler.post { callback(null) }
            return
        }

        lastCaptureTime = now

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "开始截图...")
        }

        // 4. 执行截图
        captureScreenInternal(callback)
    }

    /**
     * 内部截图实现（Android 11+）
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenInternal(callback: (Bitmap?) -> Unit) {
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        handleScreenshotSuccess(screenshot, callback)
                    }

                    override fun onFailure(errorCode: Int) {
                        handleScreenshotFailure(errorCode, callback)
                    }
                }
            )

            // 超时保护 - 保存引用以便取消
            val timeout = Runnable {
                if (isCapturing.compareAndSet(true, false)) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "截图超时")
                    }
                    callback(null)
                }
                timeoutRunnable = null
            }
            timeoutRunnable = timeout
            mainHandler.postDelayed(timeout, SCREENSHOT_TIMEOUT_MS)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "截图异常: ${e.message}", e)
            }
            isCapturing.set(false)
            mainHandler.post { callback(null) }
        }
    }

    /**
     * 取消超时任务
     */
    private fun cancelTimeout() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    /**
     * 处理截图成功
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleScreenshotSuccess(
        screenshot: AccessibilityService.ScreenshotResult,
        callback: (Bitmap?) -> Unit
    ) {
        // 取消超时任务
        cancelTimeout()

        try {
            // 从 HardwareBuffer 创建 Bitmap
            val hardwareBuffer = screenshot.hardwareBuffer
            val colorSpace = screenshot.colorSpace

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            } else {
                // Android 11 必定 >= API 29，这里只是防御性编程
                Bitmap.wrapHardwareBuffer(hardwareBuffer, ColorSpace.get(ColorSpace.Named.SRGB))
            }

            if (bitmap != null) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "截图成功: ${bitmap.width}x${bitmap.height}")
                }
                mainHandler.post { callback(bitmap) }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Bitmap 创建失败")
                }
                mainHandler.post { callback(null) }
            }

            // 释放 HardwareBuffer
            hardwareBuffer.close()

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "处理截图结果异常: ${e.message}", e)
            }
            mainHandler.post { callback(null) }
        } finally {
            isCapturing.set(false)
        }
    }

    /**
     * 处理截图失败
     */
    private fun handleScreenshotFailure(errorCode: Int, callback: (Bitmap?) -> Unit) {
        // 取消超时任务
        cancelTimeout()

        val errorMsg = when (errorCode) {
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR ->
                "内部错误"
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
                "无效的显示设备"
            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                "无障碍权限不足"
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                "截图频率过高"
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ->
                "无效的窗口"
            else -> "未知错误($errorCode)"
        }

        if (BuildConfig.DEBUG) {
            Log.e(TAG, "截图失败: $errorMsg")
        }

        isCapturing.set(false)
        mainHandler.post { callback(null) }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            // 取消所有 pending 的超时任务
            cancelTimeout()
            // 移除所有 Handler 回调，防止泄漏
            mainHandler.removeCallbacksAndMessages(null)
            // 重置截图状态
            isCapturing.set(false)
            // 立即关闭线程池，中断所有任务
            executor.shutdownNow()
            // 等待最多1秒让任务结束
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "执行器未能在1秒内关闭")
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "截图管理器已释放")
            }
        } catch (e: Exception) {
            // 忽略释放异常
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "释放截图管理器异常: ${e.message}")
            }
        }
    }

    /**
     * 检查是否支持截图功能
     */
    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * 获取截图功能状态信息（用于调试）
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("截图功能状态:")
            appendLine("  系统版本: Android ${Build.VERSION.SDK_INT} (需要 >= 30)")
            appendLine("  支持截图: ${isSupported()}")
            appendLine("  正在截图: ${isCapturing.get()}")
            appendLine("  上次截图: ${System.currentTimeMillis() - lastCaptureTime}ms 前")
        }
    }
}
