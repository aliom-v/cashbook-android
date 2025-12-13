package com.example.localexpense

import com.example.localexpense.util.DuplicateChecker
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 去重检测器单元测试
 * v1.9.3: 更新以适配 Hilt 注入后的 API 变更
 */
class DuplicateCheckerTest {

    private lateinit var checker: DuplicateChecker

    @Before
    fun setUp() {
        // 使用默认构造函数（Hilt 注入方式）
        checker = DuplicateChecker()
    }

    @After
    fun tearDown() {
        checker.clear()
    }

    @Test
    fun `shouldProcess returns true for new transaction`() {
        val result = checker.shouldProcess(
            amount = 100.0,
            merchant = "测试商户",
            type = "expense"
        )
        assertTrue(result)
    }

    @Test
    fun `shouldProcess returns false for duplicate transaction`() {
        // 第一次应该返回 true
        assertTrue(checker.shouldProcess(100.0, "测试商户", "expense"))

        // 立即重复应该返回 false
        assertFalse(checker.shouldProcess(100.0, "测试商户", "expense"))
    }

    @Test
    fun `shouldProcess differentiates by amount`() {
        assertTrue(checker.shouldProcess(100.0, "测试商户", "expense"))
        assertTrue(checker.shouldProcess(200.0, "测试商户", "expense"))
    }

    @Test
    fun `shouldProcess differentiates by merchant`() {
        assertTrue(checker.shouldProcess(100.0, "商户A", "expense"))
        assertTrue(checker.shouldProcess(100.0, "商户B", "expense"))
    }

    @Test
    fun `shouldProcess differentiates by type`() {
        assertTrue(checker.shouldProcess(100.0, "测试商户", "expense"))
        assertTrue(checker.shouldProcess(100.0, "测试商户", "income"))
    }

    @Test
    fun `shouldProcess differentiates by channel`() {
        // 使用实际的包名（interface 方法将 packageName 映射到 channel）
        assertTrue(checker.shouldProcess(100.0, "测试商户", "expense", "com.tencent.mm"))      // 微信
        assertTrue(checker.shouldProcess(100.0, "测试商户", "expense", "com.eg.android.AlipayGphone")) // 支付宝
    }

    @Test
    fun `generateKey produces consistent output`() {
        val key1 = checker.generateKey(100.0, "测试商户", "expense")
        val key2 = checker.generateKey(100.0, "测试商户", "expense")
        assertEquals(key1, key2)
    }

    @Test
    fun `generateKey normalizes amount precision`() {
        val key1 = checker.generateKey(100.0, "测试商户", "expense")
        val key2 = checker.generateKey(100.00, "测试商户", "expense")
        assertEquals(key1, key2)
    }

    @Test
    fun `generateKey normalizes merchant name`() {
        // 平台前缀应该被移除
        val key1 = checker.generateKey(100.0, "微信-测试商户", "expense")
        val key2 = checker.generateKey(100.0, "测试商户", "expense")
        assertEquals(key1, key2)
    }

    @Test
    fun `isDuplicate returns correct result`() {
        // 标记为已处理
        checker.markProcessed(100.0, "测试商户", "expense")

        // 检查是否重复
        assertTrue(checker.isDuplicate(100.0, "测试商户", "expense"))
        assertFalse(checker.isDuplicate(200.0, "测试商户", "expense"))
    }

    @Test
    fun `tryAcquireForProcessing prevents concurrent processing`() {
        // 第一次获取处理权应该成功
        assertTrue(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))

        // 同时尝试获取应该失败
        assertFalse(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))
    }

    @Test
    fun `releaseProcessing allows re-processing without cooldown`() {
        // 获取处理权
        assertTrue(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))

        // 释放处理权（不添加冷却）
        checker.releaseProcessing(100.0, "测试商户", "expense", addCooldown = false)

        // 应该可以再次获取
        assertTrue(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))
    }

    @Test
    fun `releaseProcessing with cooldown blocks immediate re-processing`() {
        // 获取处理权
        assertTrue(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))

        // 释放处理权（添加冷却）
        checker.releaseProcessing(100.0, "测试商户", "expense", addCooldown = true)

        // 立即尝试应该被阻止（冷却中）
        assertFalse(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))
    }

    @Test
    fun `markProcessed prevents further processing`() {
        // 获取处理权
        assertTrue(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))

        // 标记为已处理
        checker.markProcessed(100.0, "测试商户", "expense")

        // 不应该能再次获取处理权
        assertFalse(checker.tryAcquireForProcessing(100.0, "测试商户", "expense"))
    }

    @Test
    fun `clear removes all cached entries`() {
        checker.shouldProcess(100.0, "测试商户", "expense")
        checker.clear()

        // 清除后应该能再次处理
        assertTrue(checker.shouldProcess(100.0, "测试商户", "expense"))
    }

    @Test
    fun `getStats returns correct statistics`() {
        checker.shouldProcess(100.0, "商户1", "expense")
        checker.shouldProcess(200.0, "商户2", "expense")
        checker.shouldProcess(100.0, "商户1", "expense") // 重复

        val stats = checker.getStats()
        assertEquals(3, stats.totalChecks)
        assertEquals(1, stats.duplicatesBlocked)
        assertTrue(stats.hitRate > 0)
    }
}
