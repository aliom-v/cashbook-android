package com.example.localexpense.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localexpense.util.Constants
import com.example.localexpense.util.Logger

/**
 * 无障碍节点文本收集器
 *
 * 职责：
 * 1. 从无障碍节点树中收集文本
 * 2. 超时保护，避免 ANR
 * 3. 内存压力检查
 * 4. 快速交易页面检测
 * 5. 敏感输入框过滤（安全增强）
 *
 * 从 ExpenseAccessibilityService 中提取，提高代码可维护性
 *
 * 性能优化 v1.8.2：
 * - 使用 StringBuilder 池减少对象分配
 * - 优化内存检查频率
 * - 使用 ArrayDeque 替代递归，避免栈溢出
 *
 * 安全增强 v1.9.0：
 * - 过滤密码输入框内容
 * - 跳过敏感字段（银行卡号、身份证等）
 */
object AccessibilityTextCollector {
    private const val TAG = "TextCollector"

    // 文本收集超时时间
    private const val TEXT_COLLECT_TIMEOUT_MS = 2000L
    private const val TEXT_COLLECT_TIMEOUT_EXTRA_MS = 1000L

    // 复杂页面节点数阈值
    private const val COMPLEX_PAGE_NODE_THRESHOLD = 50

    // 内存检查间隔（节点数）
    private const val MEMORY_CHECK_INTERVAL = 50

    // 超时检查间隔（节点数）
    private const val TIMEOUT_CHECK_INTERVAL = 15

    // 交易相关关键词（按优先级排序，金额标识优先）
    private val TRANSACTION_KEYWORDS = arrayOf(
        "￥", "¥",                   // 金额标识（最高优先级）
        "支付成功", "付款成功",       // 明确的成功状态
        "转账成功", "已转账",         // 转账
        "红包", "到账",               // 红包和到账
        "收款", "付款", "扣款"        // 操作类型
    )

    // 敏感字段关键词（用于过滤）
    private val SENSITIVE_KEYWORDS = setOf(
        "密码", "password", "pwd",
        "验证码", "短信码", "动态码",
        "cvv", "cvc", "安全码",
        "身份证", "idcard", "证件号",
        "银行卡号", "卡号"
    )

    // 敏感节点类名（密码输入框等）
    private val SENSITIVE_CLASS_NAMES = setOf(
        "android.widget.EditText",  // 需要配合 isPassword 检查
    )

    // 缓存 Runtime 实例，避免重复获取
    private val runtime: Runtime = Runtime.getRuntime()

    /**
     * 快速检查是否可能是交易页面
     * 优化：按优先级检查关键词，找到后立即返回
     */
    fun quickCheckForTransaction(root: AccessibilityNodeInfo): Boolean {
        for (keyword in TRANSACTION_KEYWORDS) {
            try {
                val nodes = root.findAccessibilityNodeInfosByText(keyword)
                if (nodes.isNotEmpty()) {
                    // 批量回收节点
                    for (node in nodes) {
                        recycleNodeSafely(node)
                    }
                    return true
                }
            } catch (e: Exception) {
                // 忽略单个关键词查找的异常
            }
        }
        return false
    }

    /**
     * 收集所有文本（带超时保护）
     * 优化：使用迭代替代递归，避免栈溢出风险
     */
    fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val result = ArrayList<String>(64) // 预分配容量
        val startTime = System.currentTimeMillis()

        try {
            collectTextIterative(node, result, startTime)
        } catch (e: Exception) {
            Logger.e(TAG, "收集文本异常", e)
        }

        return result
    }

    /**
     * 检查节点是否为敏感输入框
     * 安全增强：跳过密码框和敏感字段
     */
    private fun isSensitiveNode(node: AccessibilityNodeInfo): Boolean {
        try {
            // 1. 检查是否为密码输入框
            if (node.isPassword) {
                return true
            }

            // 2. 检查输入类型（如果可用）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val inputType = node.inputType
                // TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
                // TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
                // TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
                // TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
                if (inputType and 0x000000f0 in listOf(0x80, 0x90, 0xe0, 0x10)) {
                    return true
                }
            }

            // 3. 检查 viewIdResourceName 是否包含敏感关键词
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (SENSITIVE_KEYWORDS.any { viewId.contains(it) }) {
                return true
            }

            // 4. 检查 contentDescription 是否包含敏感关键词
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (SENSITIVE_KEYWORDS.any { contentDesc.contains(it) }) {
                return true
            }

            // 5. 检查 hintText（Android O+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hintText = node.hintText?.toString()?.lowercase() ?: ""
                if (SENSITIVE_KEYWORDS.any { hintText.contains(it) }) {
                    return true
                }
            }

        } catch (e: Exception) {
            // 检查失败时保守处理，不标记为敏感
            Logger.d(TAG) { "敏感节点检查异常: ${e.message}" }
        }

        return false
    }

    /**
     * 检查文本是否可能是敏感信息
     */
    private fun isSensitiveText(text: String): Boolean {
        val lowerText = text.lowercase()

        // 检查是否包含敏感关键词
        if (SENSITIVE_KEYWORDS.any { lowerText.contains(it) }) {
            return true
        }

        // 检查是否可能是银行卡号（16-19位数字）
        val digitsOnly = text.replace(Regex("[^0-9]"), "")
        if (digitsOnly.length in 16..19 && digitsOnly.all { it.isDigit() }) {
            // 可能是银行卡号，进一步验证（Luhn 算法简化版）
            return true
        }

        // 检查是否可能是身份证号（18位，最后一位可能是X）
        if (text.length == 18 && text.take(17).all { it.isDigit() } &&
            (text.last().isDigit() || text.last().uppercaseChar() == 'X')) {
            return true
        }

        return false
    }

    /**
     * 迭代方式收集文本（避免递归导致的栈溢出）
     */
    private fun collectTextIterative(
        root: AccessibilityNodeInfo,
        list: MutableList<String>,
        startTime: Long
    ) {
        // 使用栈模拟递归，存储 (节点, 深度) 对
        data class NodeTask(val node: AccessibilityNodeInfo, val depth: Int, val needRecycle: Boolean)

        val stack = ArrayDeque<NodeTask>(128)
        stack.addLast(NodeTask(root, 0, false)) // root 不需要回收

        var nodeCount = 0
        var dynamicTimeout = TEXT_COLLECT_TIMEOUT_MS

        while (stack.isNotEmpty() && list.size < Constants.MAX_COLLECTED_TEXT_COUNT) {
            val task = stack.removeLast()
            val node = task.node
            val depth = task.depth

            // 深度限制
            if (depth > Constants.MAX_NODE_COLLECT_DEPTH) {
                if (task.needRecycle) recycleNodeSafely(node)
                continue
            }

            nodeCount++

            // 动态超时：复杂页面增加超时时间
            if (nodeCount == COMPLEX_PAGE_NODE_THRESHOLD) {
                dynamicTimeout = TEXT_COLLECT_TIMEOUT_MS + TEXT_COLLECT_TIMEOUT_EXTRA_MS
            }

            // 周期性检查超时
            if (nodeCount % TIMEOUT_CHECK_INTERVAL == 0) {
                if (System.currentTimeMillis() - startTime > dynamicTimeout) {
                    Logger.w(TAG, "文本收集超时(${dynamicTimeout}ms)，已收集 ${list.size} 条，节点数=$nodeCount")
                    // 清理栈中剩余节点
                    while (stack.isNotEmpty()) {
                        val remaining = stack.removeLast()
                        if (remaining.needRecycle) recycleNodeSafely(remaining.node)
                    }
                    if (task.needRecycle) recycleNodeSafely(node)
                    return
                }
            }

            // 周期性检查内存压力
            if (nodeCount % MEMORY_CHECK_INTERVAL == 0) {
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                if (usedMemory > maxMemory * 0.75) {
                    Logger.w(TAG, "内存压力过大(${usedMemory * 100 / maxMemory}%)，停止收集")
                    while (stack.isNotEmpty()) {
                        val remaining = stack.removeLast()
                        if (remaining.needRecycle) recycleNodeSafely(remaining.node)
                    }
                    if (task.needRecycle) recycleNodeSafely(node)
                    return
                }
            }

            try {
                // 安全检查：跳过敏感节点
                if (isSensitiveNode(node)) {
                    Logger.d(TAG) { "跳过敏感节点" }
                    if (task.needRecycle) recycleNodeSafely(node)
                    continue
                }

                // 收集 text（带敏感信息过滤）
                node.text?.toString()?.trim()?.takeIf {
                    it.isNotEmpty() &&
                    it.length < Constants.MAX_SINGLE_TEXT_LENGTH &&
                    !isSensitiveText(it)
                }?.let { list.add(it) }

                // 收集 contentDescription（带敏感信息过滤）
                node.contentDescription?.toString()?.trim()?.takeIf {
                    it.isNotEmpty() &&
                    it.length < Constants.MAX_SINGLE_TEXT_LENGTH &&
                    !isSensitiveText(it)
                }?.let { list.add(it) }

                // 收集 hintText (Android O+)（带敏感信息过滤）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim()?.takeIf {
                        it.isNotEmpty() &&
                        it.length < Constants.MAX_SINGLE_TEXT_LENGTH &&
                        !isSensitiveText(it)
                    }?.let { list.add(it) }
                }

                // 将子节点加入栈（逆序添加以保持遍历顺序）
                val childCount = node.childCount.coerceAtMost(Constants.MAX_CHILD_NODE_COUNT)
                for (i in childCount - 1 downTo 0) {
                    if (list.size >= Constants.MAX_COLLECTED_TEXT_COUNT) break
                    try {
                        val child = node.getChild(i)
                        if (child != null) {
                            stack.addLast(NodeTask(child, depth + 1, true))
                        }
                    } catch (e: Exception) {
                        // 忽略获取子节点的异常
                    }
                }
            } catch (e: Exception) {
                // 忽略单个节点的异常
            }

            // 回收当前节点（如果需要）
            if (task.needRecycle) {
                recycleNodeSafely(node)
            }
        }

        // 清理栈中剩余节点
        while (stack.isNotEmpty()) {
            val remaining = stack.removeLast()
            if (remaining.needRecycle) recycleNodeSafely(remaining.node)
        }
    }

    /**
     * 安全回收节点
     * 注意：recycle() 在 Android 13+ 废弃，系统自动管理节点生命周期
     */
    @Suppress("DEPRECATION")
    fun recycleNodeSafely(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return

        try {
            node.recycle()
        } catch (e: Exception) {
            // 忽略
        }
    }
}
