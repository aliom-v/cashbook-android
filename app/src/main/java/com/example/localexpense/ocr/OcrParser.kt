package com.example.localexpense.ocr

import android.graphics.Bitmap
import android.util.Log
import com.example.localexpense.BuildConfig
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.parser.RuleEngine
import com.example.localexpense.util.Channel
import com.example.localexpense.util.Constants
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

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
 */
object OcrParser {

    private const val TAG = "OcrParser"

    // 识别器实例（线程安全的懒加载，支持释放后重新创建）
    @Volatile
    private var _recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private val recognizerLock = Any()

    // 标记是否正在释放，防止释放时创建新实例
    @Volatile
    private var isReleasing = false

    private val recognizer: com.google.mlkit.vision.text.TextRecognizer?
        get() {
            // 如果正在释放，不创建新实例
            if (isReleasing) return null

            return _recognizer ?: synchronized(recognizerLock) {
                // 双重检查
                if (isReleasing) return null
                _recognizer ?: TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build()
                ).also { _recognizer = it }
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "开始 OCR 识别，包名: $packageName")
        }

        // 获取识别器，如果正在释放则直接返回
        val textRecognizer = recognizer
        if (textRecognizer == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "OCR 识别器不可用（可能正在释放）")
            }
            callback(null)
            return
        }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    handleOcrSuccess(visionText, packageName, callback)
                }
                .addOnFailureListener { e ->
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "OCR 识别失败: ${e.message}")
                    }
                    callback(null)
                }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "OCR 处理异常: ${e.message}")
            }
            callback(null)
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
                if (lineText.isNotEmpty()) {
                    texts.add(lineText)
                }
            }
        }

        if (texts.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OCR 未识别到文字")
            }
            callback(null)
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "OCR 识别到 ${texts.size} 行文字")
            texts.take(10).forEachIndexed { i, text ->
                Log.d(TAG, "  [$i] $text")
            }
        }

        // 2. 使用规则引擎匹配
        val ruleMatch = RuleEngine.match(texts, packageName)

        if (ruleMatch != null) {
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

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "✓ OCR 解析成功: ${transaction.type} ¥*** [商户已隐藏]")
            }
            callback(transaction)
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OCR 识别的文字未匹配到规则")
            }
            callback(null)
        }
    }

    /**
     * 释放资源
     * 释放后可以再次使用，会自动重新创建识别器
     */
    fun release() {
        synchronized(recognizerLock) {
            isReleasing = true
            try {
                _recognizer?.close()
                _recognizer = null
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "OCR 识别器已释放")
                }
            } catch (e: Exception) {
                // 忽略释放异常
                _recognizer = null
            } finally {
                isReleasing = false
            }
        }
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
    }
}
