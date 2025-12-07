package com.example.localexpense.ocr

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.example.localexpense.BuildConfig
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.parser.RuleEngine
import com.example.localexpense.util.Channel
import com.example.localexpense.util.Constants
import com.example.localexpense.util.Logger
import com.example.localexpense.util.PerformanceMonitor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * OCR 解析器 - 当节点读取失败时的备用方案
 *
 * 使用 Google ML Kit 进行文字识别
 * 优势：
 * 1. 完全离线，无需网络
 * 2. 速度快（约100-300ms）
 * 3. 准确度高（特别是中文）
 * 4. 完全免费
 *
 * 使用场景：
 * - 支付App改版后节点结构变化
 * - 自绘UI导致节点读取失败
 * - 动态内容无法通过节点获取
 *
 * 容错特性：
 * - 图片大小检查
 * - 超时保护
 * - 自动重试（可选）
 * - 性能监控
 */
object OcrParser {

    private const val TAG = "OcrParser"

    // 配置常量
    private const val MAX_IMAGE_WIDTH = 2000
    private const val MAX_IMAGE_HEIGHT = 3000
    private const val MIN_IMAGE_SIZE = 100
    private const val OCR_TIMEOUT_MS = 5000L  // OCR 超时时间
    private const val MAX_RETRY_COUNT = 1     // 最大重试次数

    // 识别器实例（线程安全的懒加载，支持释放后重新创建）
    @Volatile
    private var _recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private val recognizerLock = Any()

    // 标记是否正在释放，防止释放时创建新实例
    @Volatile
    private var isReleasing = false

    // 标记是否已永久释放（服务销毁时）
    @Volatile
    private var isPermanentlyReleased = false

    // 统计信息
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)

    // 主线程 Handler（用于超时处理）
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognizer: com.google.mlkit.vision.text.TextRecognizer?
        get() {
            // 如果正在释放或已永久释放，不创建新实例
            if (isReleasing || isPermanentlyReleased) return null

            return _recognizer ?: synchronized(recognizerLock) {
                // 双重检查
                if (isReleasing || isPermanentlyReleased) return null
                _recognizer ?: try {
                    TextRecognition.getClient(
                        ChineseTextRecognizerOptions.Builder().build()
                    ).also { _recognizer = it }
                } catch (e: Exception) {
                    Logger.e(TAG, "创建 OCR 识别器失败", e)
                    null
                }
            }
        }

    /**
     * 从截图中识别交易信息
     *
     * @param bitmap 截图位图
     * @param packageName 应用包名
     * @param callback 识别结果回调（异步）
     */
    fun parseFromBitmap(
        bitmap: Bitmap,
        packageName: String,
        callback: (ExpenseEntity?) -> Unit
    ) {
        parseFromBitmapInternal(bitmap, packageName, callback, retryCount = 0)
    }

    /**
     * 内部解析方法（支持重试）
     */
    private fun parseFromBitmapInternal(
        bitmap: Bitmap,
        packageName: String,
        callback: (ExpenseEntity?) -> Unit,
        retryCount: Int
    ) {
        val startTime = PerformanceMonitor.startTimer(PerformanceMonitor.Operations.OCR_RECOGNIZE)

        // 1. 验证输入
        val validationError = validateBitmap(bitmap)
        if (validationError != null) {
            Logger.w(TAG, "图片验证失败: $validationError")
            PerformanceMonitor.increment(PerformanceMonitor.Counters.PARSE_FAILURES)
            failureCount.incrementAndGet()
            callback(null)
            return
        }

        // 2. 获取识别器
        val textRecognizer = recognizer
        if (textRecognizer == null) {
            Logger.w(TAG, "OCR 识别器不可用")
            callback(null)
            return
        }

        // 3. 预处理图片（如果需要缩放）
        val processedBitmap = preprocessBitmap(bitmap)

        // 4. 超时处理
        val isCompleted = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (isCompleted.compareAndSet(false, true)) {
                Logger.w(TAG, "OCR 识别超时 (${OCR_TIMEOUT_MS}ms)")
                timeoutCount.incrementAndGet()
                PerformanceMonitor.endTimer(PerformanceMonitor.Operations.OCR_RECOGNIZE, startTime)
                callback(null)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, OCR_TIMEOUT_MS)

        // 5. 执行 OCR
        try {
            val inputImage = InputImage.fromBitmap(processedBitmap, 0)
            PerformanceMonitor.increment(PerformanceMonitor.Counters.OCR_INVOCATIONS)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (isCompleted.compareAndSet(false, true)) {
                        PerformanceMonitor.endTimer(PerformanceMonitor.Operations.OCR_RECOGNIZE, startTime)
                        handleOcrSuccess(visionText, packageName, callback)
                    }
                }
                .addOnFailureListener { e ->
                    mainHandler.removeCallbacks(timeoutRunnable)
                    if (isCompleted.compareAndSet(false, true)) {
                        PerformanceMonitor.endTimer(PerformanceMonitor.Operations.OCR_RECOGNIZE, startTime)
                        handleOcrFailure(e, bitmap, packageName, callback, retryCount)
                    }
                }
        } catch (e: Exception) {
            mainHandler.removeCallbacks(timeoutRunnable)
            if (isCompleted.compareAndSet(false, true)) {
                PerformanceMonitor.endTimer(PerformanceMonitor.Operations.OCR_RECOGNIZE, startTime)
                Logger.e(TAG, "OCR 处理异常", e)
                failureCount.incrementAndGet()
                callback(null)
            }
        } finally {
            // 如果处理后的图片是新创建的，需要回收
            if (processedBitmap !== bitmap && !processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
        }
    }

    /**
     * 验证图片
     */
    private fun validateBitmap(bitmap: Bitmap): String? {
        if (bitmap.isRecycled) {
            return "图片已被回收"
        }
        if (bitmap.width < MIN_IMAGE_SIZE || bitmap.height < MIN_IMAGE_SIZE) {
            return "图片太小 (${bitmap.width}x${bitmap.height})"
        }
        if (bitmap.width > MAX_IMAGE_WIDTH * 2 || bitmap.height > MAX_IMAGE_HEIGHT * 2) {
            return "图片太大 (${bitmap.width}x${bitmap.height})"
        }
        return null
    }

    /**
     * 预处理图片（缩放到合适大小）
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 如果图片大小合适，直接返回
        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            return bitmap
        }

        // 计算缩放比例
        val scaleX = MAX_IMAGE_WIDTH.toFloat() / width
        val scaleY = MAX_IMAGE_HEIGHT.toFloat() / height
        val scale = minOf(scaleX, scaleY)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        Logger.d(TAG) { "缩放图片: ${width}x${height} -> ${newWidth}x${newHeight}" }

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Logger.e(TAG, "图片缩放失败", e)
            bitmap // 返回原图
        }
    }

    /**
     * 处理 OCR 识别成功的结果
     */
    private fun handleOcrSuccess(
        visionText: Text,
        packageName: String,
        callback: (ExpenseEntity?) -> Unit
    ) {
        // 1. 提取所有文本块
        val texts = mutableListOf<String>()

        // 按行提取文本（保持顺序）
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                if (lineText.isNotEmpty() && lineText.length <= Constants.MAX_SINGLE_TEXT_LENGTH) {
                    texts.add(lineText)
                }
            }
        }

        if (texts.isEmpty()) {
            Logger.d(TAG) { "OCR 未识别到有效文字" }
            failureCount.incrementAndGet()
            callback(null)
            return
        }

        Logger.d(TAG) { "OCR 识别到 ${texts.size} 行文字" }

        // 2. 使用规则引擎匹配
        val ruleMatch = RuleEngine.match(texts, packageName)

        if (ruleMatch != null) {
            successCount.incrementAndGet()

            val transaction = ExpenseEntity(
                id = 0,
                amount = ruleMatch.amount,
                merchant = ruleMatch.merchant,
                type = ruleMatch.rule.type,
                timestamp = System.currentTimeMillis(),
                channel = Channel.PACKAGE_MAP[packageName] ?: "其他",
                category = ruleMatch.rule.category,
                categoryId = 0,
                note = "OCR识别",
                rawText = texts.joinToString(" | ").take(Constants.RAW_TEXT_MAX_LENGTH)
            )

            Logger.i(TAG, "OCR 解析成功: ${transaction.type} ¥${Logger.maskAmount(transaction.amount)}")
            callback(transaction)
        } else {
            Logger.d(TAG) { "OCR 识别的文字未匹配到规则" }
            failureCount.incrementAndGet()
            callback(null)
        }
    }

    /**
     * 处理 OCR 识别失败
     */
    private fun handleOcrFailure(
        error: Exception,
        bitmap: Bitmap,
        packageName: String,
        callback: (ExpenseEntity?) -> Unit,
        retryCount: Int
    ) {
        Logger.e(TAG, "OCR 识别失败: ${error.message}")

        // 判断是否需要重试
        if (retryCount < MAX_RETRY_COUNT && shouldRetry(error)) {
            Logger.i(TAG, "OCR 重试 (${retryCount + 1}/$MAX_RETRY_COUNT)")
            // 延迟重试
            mainHandler.postDelayed({
                parseFromBitmapInternal(bitmap, packageName, callback, retryCount + 1)
            }, 500)
        } else {
            failureCount.incrementAndGet()
            PerformanceMonitor.increment(PerformanceMonitor.Counters.PARSE_FAILURES)
            callback(null)
        }
    }

    /**
     * 判断是否应该重试
     */
    private fun shouldRetry(error: Exception): Boolean {
        val message = error.message ?: return false
        // 临时性错误可以重试
        return message.contains("busy", ignoreCase = true) ||
                message.contains("temporary", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true)
    }

    /**
     * 获取 OCR 统计信息
     */
    fun getStats(): OcrStats {
        return OcrStats(
            successCount = successCount.get(),
            failureCount = failureCount.get(),
            timeoutCount = timeoutCount.get()
        )
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        successCount.set(0)
        failureCount.set(0)
        timeoutCount.set(0)
    }

    /**
     * OCR 统计信息
     */
    data class OcrStats(
        val successCount: Int,
        val failureCount: Int,
        val timeoutCount: Int
    ) {
        val totalCount: Int get() = successCount + failureCount + timeoutCount
        val successRate: Double
            get() = if (totalCount > 0) successCount * 100.0 / totalCount else 0.0
    }

    /**
     * 释放资源
     * @param permanent 如果为 true，释放后不会自动重新创建识别器（用于服务销毁场景）
     */
    fun release(permanent: Boolean = false) {
        synchronized(recognizerLock) {
            isReleasing = true
            if (permanent) {
                isPermanentlyReleased = true
            }
            try {
                _recognizer?.close()
                _recognizer = null
                Logger.d(TAG) { "OCR 识别器已释放 (permanent=$permanent)" }
            } catch (e: Exception) {
                // 忽略释放异常
                _recognizer = null
            } finally {
                // 只有非永久释放时才重置 isReleasing
                // 永久释放时保持 isReleasing=true，阻止新实例创建
                if (!permanent) {
                    isReleasing = false
                }
            }
        }
    }

    /**
     * 检查识别器是否可用
     */
    fun isAvailable(): Boolean {
        return !isReleasing && !isPermanentlyReleased && recognizer != null
    }

    /**
     * 调试方法：仅识别文字，不解析交易
     * 用于测试 OCR 识别效果
     */
    fun debugRecognize(bitmap: Bitmap, callback: (List<String>) -> Unit) {
        val textRecognizer = recognizer
        if (textRecognizer == null) {
            callback(emptyList())
            return
        }

        // 验证图片
        if (validateBitmap(bitmap) != null) {
            callback(emptyList())
            return
        }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val texts = visionText.textBlocks.flatMap { block ->
                        block.lines.map { it.text }
                    }
                    callback(texts)
                }
                .addOnFailureListener {
                    callback(emptyList())
                }
        } catch (e: Exception) {
            Logger.e(TAG, "调试识别异常", e)
            callback(emptyList())
        }
    }
}
