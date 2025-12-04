package com.example.localexpense.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.localexpense.R
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.parser.TransactionParser
import com.example.localexpense.util.Constants

class ExpenseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ExpenseService"
        private const val CHANNEL_ID = "expense_channel"
        private var notifyId = 1

        // 监听的App包名
        private val MONITORED_PACKAGES = setOf(
            "com.tencent.mm",              // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.unionpay"                 // 云闪付
        )
    }

    private lateinit var repository: TransactionRepository
    private lateinit var notificationManager: NotificationManager

    // 防重复
    private var lastHash: Int = 0
    private var lastTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = TransactionRepository.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.i(TAG, "无障碍服务已启动")
        showNotification("服务已启动", "正在监听微信、支付宝交易")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "记账通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "自动记账成功通知"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        // 使用常量取模防止溢出
        notifyId = (notifyId % Constants.NOTIFICATION_ID_MOD) + 1
        notificationManager.notify(notifyId, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // 处理通知事件（优先级最高，快速返回）
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (pkg in MONITORED_PACKAGES) {
                handleNotification(event, pkg)
            }
            return
        }

        // 只处理目标App的窗口变化事件
        if (pkg !in MONITORED_PACKAGES) return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        var root: AccessibilityNodeInfo? = null
        try {
            root = rootInActiveWindow ?: return

            // 快速检查：只在可能包含交易信息的页面才收集所有文本
            if (!quickCheckForTransaction(root)) {
                return
            }

            val texts = collectAllText(root)
            if (texts.isEmpty()) return

            val joined = texts.joinToString(" | ")

            Log.d(TAG, ">>> 检测到可能的交易页面，收集到 ${texts.size} 条文本")

            // 解析交易
            val transaction = TransactionParser.parse(texts, pkg) ?: return

            // 防重复：使用常量定义的时间间隔
            val hash = transaction.rawText.hashCode()
            val now = System.currentTimeMillis()
            if (hash == lastHash && now - lastTime < Constants.DUPLICATE_CHECK_INTERVAL_MS) {
                Log.d(TAG, "重复交易，跳过")
                return
            }
            lastHash = hash
            lastTime = now

            // 保存
            Log.i(TAG, "✓✓✓ 记录成功: ${transaction.type} ¥${transaction.amount} ${transaction.merchant}")
            repository.insertTransaction(transaction)

            // 发送通知
            val typeText = if (transaction.type == "income") "收入" else "支出"
            showNotification("记账成功", "$typeText ¥${transaction.amount} - ${transaction.merchant}")

        } catch (e: Exception) {
            Log.e(TAG, "处理事件出错: ${e.message}", e)
        } finally {
            try {
                root?.recycle()
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    /**
     * 快速检查是否可能是交易页面，避免对所有页面都进行完整文本收集
     */
    private fun quickCheckForTransaction(root: AccessibilityNodeInfo): Boolean {
        // 使用 findAccessibilityNodeInfosByText 快速搜索关键词
        val keywords = listOf("￥", "¥", "元", "红包", "转账", "支付")
        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNotEmpty()) {
                nodes.forEach { it.recycle() }
                return true
            }
        }
        return false
    }
    
    private fun handleNotification(event: AccessibilityEvent, pkg: String) {
        if (pkg !in MONITORED_PACKAGES) return
        
        val text = event.text?.joinToString(" ") ?: return
        Log.d(TAG, "通知: pkg=$pkg, text=$text")
        
        // 解析通知中的交易信息
        val transaction = TransactionParser.parseNotification(text, pkg)
        if (transaction != null) {
            val hash = transaction.rawText.hashCode()
            val now = System.currentTimeMillis()
            if (hash != lastHash || now - lastTime >= Constants.DUPLICATE_CHECK_INTERVAL_MS) {
                lastHash = hash
                lastTime = now
                repository.insertTransaction(transaction)
                val typeText = if (transaction.type == "income") "收入" else "支出"
                showNotification("记账成功(通知)", "$typeText ¥${transaction.amount}")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        collectTextRecursive(node, result)
        return result
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        
        // 获取文本
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { 
            list.add(it) 
        }
        
        // 获取内容描述
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { 
            list.add(it) 
        }
        
        // 递归子节点，并正确释放资源
        for (i in 0 until node.childCount) {
            var child: AccessibilityNodeInfo? = null
            try {
                child = node.getChild(i)
                collectTextRecursive(child, list)
            } catch (e: Exception) {
                // 忽略
            } finally {
                try {
                    child?.recycle()
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }
    }
}
