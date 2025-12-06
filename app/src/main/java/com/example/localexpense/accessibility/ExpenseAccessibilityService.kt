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
import com.example.localexpense.BuildConfig
import com.example.localexpense.R
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.parser.TransactionParser
import com.example.localexpense.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ExpenseService"
        private const val CHANNEL_ID = "expense_channel"
        // 通知ID范围：1 到 NOTIFICATION_ID_MOD
        private const val NOTIFICATION_ID_MIN = 1
        private const val NOTIFICATION_ID_MAX = Constants.NOTIFICATION_ID_MOD

        // 监听的App包名
        private val MONITORED_PACKAGES = setOf(
            "com.tencent.mm",              // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.unionpay"                 // 云闪付
        )
    }

    private var repository: TransactionRepository? = null
    private var notificationManager: NotificationManager? = null
    @Volatile
    private var isInitialized = false
    
    // 通知ID，使用实例变量避免静态变量问题
    private var notifyId = NOTIFICATION_ID_MIN

    // 防重复 - 使用同步锁保护
    private val duplicateLock = Any()
    @Volatile
    private var lastHash: Int = 0
    @Volatile
    private var lastTime: Long = 0

    // 文本收集最大深度，防止栈溢出
    private val maxCollectDepth = 20

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            createNotificationChannel()

            // 使用 try-catch 包裹 Repository 初始化，防止崩溃
            try {
                repository = TransactionRepository.getInstance(applicationContext)
                // 启动后台协程等待初始化完成
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repository?.waitForInitialization()
                        isInitialized = true
                        Log.i(TAG, "无障碍服务已启动并完成初始化")
                        showNotification("服务已启动", "正在监听微信、支付宝交易")
                    } catch (e: Exception) {
                        Log.e(TAG, "Repository 初始化等待失败: ${e.message}", e)
                        isInitialized = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Repository 初始化失败: ${e.message}", e)
                // 即使 Repository 初始化失败，服务仍然运行，但不记录数据
                isInitialized = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "服务初始化失败: ${e.message}", e)
            isInitialized = false
        }
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
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(title: String, content: String) {
        try {
            val nm = notificationManager ?: return
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            // 修复通知ID循环：先使用当前ID，再递增
            val currentId = notifyId
            notifyId = if (notifyId >= NOTIFICATION_ID_MAX) NOTIFICATION_ID_MIN else notifyId + 1
            nm.notify(currentId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 全局异常保护，防止服务崩溃
        try {
            handleAccessibilityEventSafely(event)
        } catch (e: Exception) {
            Log.e(TAG, "onAccessibilityEvent 异常: ${e.message}", e)
        }
    }

    private fun handleAccessibilityEventSafely(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isInitialized || repository == null) return  // 服务未初始化完成，跳过

        val pkg = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // 处理通知事件（优先级最高，快速返回）
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (pkg in MONITORED_PACKAGES) {
                try {
                    handleNotification(event, pkg)
                } catch (e: Exception) {
                    Log.e(TAG, "处理通知失败: ${e.message}")
                }
            }
            return
        }

        // 只处理目标App的窗口变化事件
        if (pkg !in MONITORED_PACKAGES) return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        try {
            val root = rootInActiveWindow ?: return

            // 快速检查：只在可能包含交易信息的页面才收集所有文本
            if (!quickCheckForTransaction(root)) {
                return
            }

            val texts = collectAllText(root)
            if (texts.isEmpty()) return

            // 解析交易
            val transaction = TransactionParser.parse(texts, pkg) ?: return

            // 防重复：使用同步块保护，确保原子操作
            val shouldProcess = synchronized(duplicateLock) {
                val hash = transaction.rawText.hashCode()
                val now = System.currentTimeMillis()
                if (hash == lastHash && now - lastTime < Constants.DUPLICATE_CHECK_INTERVAL_MS) {
                    false
                } else {
                    lastHash = hash
                    lastTime = now
                    true
                }
            }

            if (!shouldProcess) return

            // 保存（日志中隐藏敏感信息）
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "✓ 记录: ${transaction.type} ¥*** [商户已隐藏]")
            }
            repository?.insertTransaction(transaction)

            // 发送通知
            val typeText = if (transaction.type == "income") "收入" else "支出"
            showNotification("记账成功", "$typeText ¥${transaction.amount} - ${transaction.merchant}")

        } catch (e: Exception) {
            Log.e(TAG, "处理窗口事件出错: ${e.message}")
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
                // API 26-32 需要手动 recycle AccessibilityNodeInfo
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    nodes.forEach { node ->
                        try {
                            node.recycle()
                        } catch (e: Exception) {
                            // 忽略 recycle 异常
                        }
                    }
                }
                return true
            }
        }
        return false
    }
    
    private fun handleNotification(event: AccessibilityEvent, pkg: String) {
        if (pkg !in MONITORED_PACKAGES) return

        val text = event.text?.joinToString(" ") ?: return

        // 解析通知中的交易信息
        val transaction = TransactionParser.parseNotification(text, pkg) ?: return

        // 防重复：使用同步块保护
        val shouldProcess = synchronized(duplicateLock) {
            val hash = transaction.rawText.hashCode()
            val now = System.currentTimeMillis()
            if (hash == lastHash && now - lastTime < Constants.DUPLICATE_CHECK_INTERVAL_MS) {
                false
            } else {
                lastHash = hash
                lastTime = now
                true
            }
        }

        if (!shouldProcess) return

        repository?.insertTransaction(transaction)
        val typeText = if (transaction.type == "income") "收入" else "支出"
        showNotification("记账成功(通知)", "$typeText ¥${transaction.amount}")
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源，防止内存泄漏
        repository = null
        notificationManager = null
        isInitialized = false
        Log.i(TAG, "无障碍服务已销毁")
    }

    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        collectTextRecursive(node, result, 0)
        return result
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, list: MutableList<String>, depth: Int) {
        // 深度限制，防止栈溢出
        if (node == null || depth > maxCollectDepth) return
        // 文本数量限制，避免收集过多
        if (list.size > 200) return

        // 获取文本
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 500 }?.let {
            list.add(it)
        }

        // 获取内容描述
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 500 }?.let {
            list.add(it)
        }

        // 递归子节点
        val childCount = node.childCount.coerceAtMost(50) // 限制子节点数量
        for (i in 0 until childCount) {
            try {
                val child = node.getChild(i)
                if (child != null) {
                    collectTextRecursive(child, list, depth + 1)
                }
            } catch (e: Exception) {
                // 忽略获取子节点时的异常
            }
        }
    }
}
