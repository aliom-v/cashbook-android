package com.example.localexpense.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.localexpense.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通知管理工具类
 *
 * 功能：
 * 1. 创建和管理通知渠道
 * 2. 构建各类通知
 * 3. 前台服务通知管理
 * 4. 通知权限检查
 *
 * 通知渠道设计：
 * - 前台服务：持续运行提示，低优先级
 * - 交易记录：新交易提醒，默认优先级
 * - 系统消息：错误、警告等，高优先级
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // 通知渠道 ID
    object Channels {
        const val FOREGROUND_SERVICE = "foreground_service"
        const val TRANSACTION = "transaction"
        const val SYSTEM = "system"
    }

    // 通知 ID
    object NotificationIds {
        const val FOREGROUND_SERVICE = 1
        const val TRANSACTION_RECORDED = 100  // 交易通知ID起始范围: 100-199
        const val BUDGET_WARNING = 200
        const val ERROR = 300

        // 交易通知ID范围
        private const val TRANSACTION_ID_MIN = 100
        private const val TRANSACTION_ID_MAX = 199

        // 原子计数器，用于生成唯一的交易通知ID
        private val transactionIdCounter = AtomicInteger(TRANSACTION_ID_MIN)

        /**
         * 获取下一个交易通知ID
         * 使用原子计数器循环生成 100-199 范围内的唯一ID
         */
        fun nextTransactionId(): Int {
            val id = transactionIdCounter.getAndIncrement()
            // 循环使用ID范围
            if (transactionIdCounter.get() > TRANSACTION_ID_MAX) {
                transactionIdCounter.compareAndSet(transactionIdCounter.get(), TRANSACTION_ID_MIN)
            }
            return id
        }
    }

    /**
     * 初始化所有通知渠道
     * 应在 Application.onCreate() 中调用
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            // 前台服务通知渠道
            NotificationChannel(
                Channels.FOREGROUND_SERVICE,
                context.getString(R.string.notification_channel_foreground),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_foreground_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },

            // 交易记录通知渠道
            NotificationChannel(
                Channels.TRANSACTION,
                context.getString(R.string.notification_channel_transaction),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_transaction_desc)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
            },

            // 系统消息通知渠道
            NotificationChannel(
                Channels.SYSTEM,
                context.getString(R.string.notification_channel_system),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_system_desc)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }
        )

        notificationManager.createNotificationChannels(channels)
        Logger.i(TAG, "通知渠道已创建: ${channels.size} 个")
    }

    /**
     * 构建前台服务通知
     * 优化：添加监听状态摘要
     */
    fun buildForegroundNotification(
        context: Context,
        contentIntent: PendingIntent? = null,
        statusText: String? = null  // 可选的状态文本
    ): Notification {
        val text = statusText ?: context.getString(R.string.notification_foreground_text)

        return NotificationCompat.Builder(context, Channels.FOREGROUND_SERVICE)
            .setContentTitle(context.getString(R.string.notification_foreground_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // 优化：添加子文本显示更多信息
            .setSubText("点击查看记账详情")
            .build()
    }

    /**
     * 显示交易记录通知
     */
    fun showTransactionNotification(
        context: Context,
        amount: Double,
        type: String,
        merchant: String,
        contentIntent: PendingIntent? = null
    ) {
        if (!areNotificationsEnabled(context)) {
            Logger.w(TAG, "通知权限未授予")
            return
        }

        val isExpense = type == "expense"
        val amountText = if (isExpense) "-¥${String.format("%.2f", amount)}" else "+¥${String.format("%.2f", amount)}"
        val title = if (isExpense) "支出已记录" else "收入已记录"

        val notification = NotificationCompat.Builder(context, Channels.TRANSACTION)
            .setContentTitle(title)
            .setContentText("$amountText · $merchant")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$amountText\n商户: $merchant"))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.nextTransactionId(), notification)
        } catch (e: SecurityException) {
            Logger.e(TAG, "显示通知失败: 权限被拒绝", e)
        }
    }

    /**
     * 显示预算警告通知
     */
    fun showBudgetWarning(
        context: Context,
        usedPercent: Int,
        remaining: Double,
        contentIntent: PendingIntent? = null
    ) {
        if (!areNotificationsEnabled(context)) return

        val title = when {
            usedPercent >= 100 -> "预算已超支"
            usedPercent >= 90 -> "预算即将用尽"
            usedPercent >= 80 -> "预算提醒"
            else -> return // 不显示通知
        }

        val text = if (usedPercent >= 100) {
            "本月预算已超支 ¥${String.format("%.2f", -remaining)}"
        } else {
            "已使用 $usedPercent%，剩余 ¥${String.format("%.2f", remaining)}"
        }

        val notification = NotificationCompat.Builder(context, Channels.SYSTEM)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.BUDGET_WARNING, notification)
        } catch (e: SecurityException) {
            Logger.e(TAG, "显示预算警告失败", e)
        }
    }

    /**
     * 显示错误通知
     */
    fun showErrorNotification(
        context: Context,
        title: String,
        message: String,
        contentIntent: PendingIntent? = null
    ) {
        if (!areNotificationsEnabled(context)) return

        val notification = NotificationCompat.Builder(context, Channels.SYSTEM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(contentIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.ERROR, notification)
        } catch (e: SecurityException) {
            Logger.e(TAG, "显示错误通知失败", e)
        }
    }

    /**
     * 取消指定通知
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * 取消所有通知
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * 检查通知权限是否已授予
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 检查指定渠道是否启用
     */
    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(channelId) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * 获取通知渠道状态摘要
     */
    fun getChannelsStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            Channels.FOREGROUND_SERVICE to isChannelEnabled(context, Channels.FOREGROUND_SERVICE),
            Channels.TRANSACTION to isChannelEnabled(context, Channels.TRANSACTION),
            Channels.SYSTEM to isChannelEnabled(context, Channels.SYSTEM)
        )
    }

    /**
     * 通知构建器（流式 API）
     */
    class Builder(private val context: Context, private val channelId: String) {
        private var title: String = ""
        private var text: String = ""
        private var bigText: String? = null
        private var smallIcon: Int = R.drawable.ic_notification
        private var autoCancel: Boolean = true
        private var ongoing: Boolean = false
        private var priority: Int = NotificationCompat.PRIORITY_DEFAULT
        private var category: String? = null
        private var contentIntent: PendingIntent? = null
        private var actions: MutableList<NotificationCompat.Action> = mutableListOf()

        fun setTitle(title: String) = apply { this.title = title }
        fun setText(text: String) = apply { this.text = text }
        fun setBigText(bigText: String) = apply { this.bigText = bigText }
        fun setSmallIcon(@DrawableRes icon: Int) = apply { this.smallIcon = icon }
        fun setAutoCancel(autoCancel: Boolean) = apply { this.autoCancel = autoCancel }
        fun setOngoing(ongoing: Boolean) = apply { this.ongoing = ongoing }
        fun setPriority(priority: Int) = apply { this.priority = priority }
        fun setCategory(category: String) = apply { this.category = category }
        fun setContentIntent(intent: PendingIntent?) = apply { this.contentIntent = intent }

        fun addAction(icon: Int, title: String, intent: PendingIntent) = apply {
            actions.add(NotificationCompat.Action.Builder(icon, title, intent).build())
        }

        fun build(): Notification {
            return NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(smallIcon)
                .setAutoCancel(autoCancel)
                .setOngoing(ongoing)
                .setPriority(priority)
                .apply {
                    category?.let { setCategory(it) }
                    contentIntent?.let { setContentIntent(it) }
                    bigText?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
                    actions.forEach { addAction(it) }
                }
                .build()
        }

        fun show(notificationId: Int) {
            if (!areNotificationsEnabled(context)) {
                Logger.w(TAG, "通知权限未授予")
                return
            }
            try {
                NotificationManagerCompat.from(context).notify(notificationId, build())
            } catch (e: SecurityException) {
                Logger.e(TAG, "显示通知失败", e)
            }
        }
    }
}
