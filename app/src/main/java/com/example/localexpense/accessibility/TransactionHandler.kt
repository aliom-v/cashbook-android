package com.example.localexpense.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.localexpense.data.ExpenseEntity
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.ui.FloatingConfirmWindow
import com.example.localexpense.util.DuplicateChecker
import com.example.localexpense.util.Logger
import com.example.localexpense.util.NotificationHelper
import com.example.localexpense.util.PerformanceMonitor
import com.example.localexpense.util.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 交易处理器
 *
 * 职责：
 * 1. 处理识别到的交易
 * 2. 显示悬浮窗确认
 * 3. 保存交易到数据库
 * 4. 显示通知
 *
 * 从 ExpenseAccessibilityService 中提取，提高代码可维护性
 */
class TransactionHandler(
    private val context: Context,
    private val repository: TransactionRepository,
    private val floatingWindow: FloatingConfirmWindow?,
    private val duplicateChecker: DuplicateChecker,
    private val onSuccess: () -> Unit,
    private val onError: () -> Unit
) {
    companion object {
        private const val TAG = "TransactionHandler"
    }

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 通知 ID 计数器
    private val notifyIdCounter = AtomicInteger(100)

    // 是否已销毁
    @Volatile
    private var isDestroyed = false

    /**
     * 处理找到的交易
     */
    fun handleTransaction(transaction: ExpenseEntity) {
        if (isDestroyed) return

        // 使用原子操作检查重复并标记为处理中
        if (!duplicateChecker.tryAcquireForProcessing(
                transaction.amount,
                transaction.merchant,
                transaction.type,
                transaction.channel,
                transaction.rawText
            )) {
            Logger.d(TAG) { "跳过交易(重复或处理中): ¥${Logger.maskAmount(transaction.amount)}" }
            PerformanceMonitor.increment(PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
            return
        }

        // 限流检查
        if (!RateLimiter.allowFloatingWindowShow()) {
            // 限流时直接保存
            saveTransactionWithMark(transaction, "记账成功")
            return
        }

        PerformanceMonitor.increment(PerformanceMonitor.Counters.TRANSACTIONS_RECORDED)
        Logger.d(TAG) { "识别到交易: ${transaction.type} ¥${Logger.maskAmount(transaction.amount)}" }

        // 在主线程显示悬浮窗
        mainHandler.post {
            showFloatingWindow(transaction)
        }
    }

    /**
     * 处理通知交易（不需要用户确认）
     */
    fun handleNotificationTransaction(transaction: ExpenseEntity) {
        if (isDestroyed) return

        // 检查重复
        if (duplicateChecker.isDuplicate(
                transaction.amount,
                transaction.merchant,
                transaction.type,
                transaction.channel,
                transaction.rawText
            )) {
            Logger.d(TAG) { "跳过重复通知交易" }
            return
        }

        // 直接保存并标记
        saveTransactionWithMark(transaction, "记账成功(通知)")
    }

    /**
     * 显示悬浮窗
     */
    private fun showFloatingWindow(transaction: ExpenseEntity) {
        if (isDestroyed) {
            releaseProcessing(transaction)
            return
        }

        if (floatingWindow != null && FloatingConfirmWindow.hasPermission(context)) {
            floatingWindow.show(
                transaction = transaction,
                onConfirm = { confirmed ->
                    scope.launch {
                        saveTransactionWithMark(confirmed, "记账成功")
                    }
                },
                onDismiss = {
                    // 用户取消时添加冷却时间，防止立即重新触发
                    releaseProcessing(transaction, addCooldown = true)
                    Logger.d(TAG) { "用户取消记账，已释放处理权并添加冷却" }
                }
            )
        } else {
            // 无悬浮窗权限，直接保存
            scope.launch {
                saveTransactionWithMark(transaction, "记账成功", showMerchant = true)
            }
        }
    }

    /**
     * 保存交易并标记为已处理
     */
    private fun saveTransactionWithMark(
        transaction: ExpenseEntity,
        title: String,
        showMerchant: Boolean = false
    ) {
        scope.launch {
            try {
                val success = repository.insertTransactionSync(transaction)
                if (success) {
                    // 保存成功后标记为已处理
                    duplicateChecker.markProcessed(
                        transaction.amount,
                        transaction.merchant,
                        transaction.type,
                        transaction.channel,
                        transaction.rawText
                    )

                    onSuccess()

                    val typeText = if (transaction.type == "income") "收入" else "支出"
                    val content = if (showMerchant) {
                        "$typeText ¥${transaction.amount} - ${transaction.merchant}"
                    } else {
                        "$typeText ¥${transaction.amount}"
                    }

                    withContext(Dispatchers.Main) {
                        showNotification(title, content)
                    }

                    Logger.i(TAG, "交易保存成功: ¥${Logger.maskAmount(transaction.amount)}")
                } else {
                    releaseProcessing(transaction)
                    Logger.w(TAG, "交易保存失败，已释放处理权")

                    withContext(Dispatchers.Main) {
                        showNotification("记账失败", "保存失败，请稍后重试")
                    }
                }
            } catch (e: Exception) {
                releaseProcessing(transaction)
                Logger.e(TAG, "保存交易异常", e)

                withContext(Dispatchers.Main) {
                    showNotification("记账失败", "发生异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 释放处理权
     * @param addCooldown 是否添加冷却时间（用户取消时为 true）
     */
    private fun releaseProcessing(transaction: ExpenseEntity, addCooldown: Boolean = false) {
        duplicateChecker.releaseProcessing(
            transaction.amount,
            transaction.merchant,
            transaction.type,
            transaction.channel,
            addCooldown = addCooldown
        )
    }

    /**
     * 显示通知
     */
    private fun showNotification(title: String, content: String) {
        try {
            NotificationHelper.Builder(context, NotificationHelper.Channels.TRANSACTION)
                .setTitle(title)
                .setText(content)
                .show(notifyIdCounter.incrementAndGet())
        } catch (e: Exception) {
            Logger.e(TAG, "显示通知异常", e)
        }
    }

    /**
     * 销毁
     */
    fun destroy() {
        isDestroyed = true
        try {
            scope.cancel()
        } catch (e: Exception) {
            // 忽略
        }
    }
}
