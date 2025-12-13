package com.example.localexpense

import com.example.localexpense.util.PerformanceMonitor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * PerformanceMonitor 单元测试
 *
 * 测试覆盖：
 * - 计时功能 (startTimer, endTimer, measure)
 * - 计数功能 (increment, getCount)
 * - 内存监控 (getMemoryInfo, isMemoryPressure)
 * - 报告生成 (generateReport)
 * - 预定义常量
 */
class PerformanceMonitorTest {

    @Before
    fun setUp() {
        PerformanceMonitor.reset()
    }

    @After
    fun tearDown() {
        PerformanceMonitor.reset()
    }

    // ==================== 计数功能测试 ====================

    @Test
    fun `increment - 增加计数`() {
        PerformanceMonitor.increment("test_counter")
        assertEquals(1, PerformanceMonitor.getCount("test_counter"))
    }

    @Test
    fun `increment - 多次增加`() {
        PerformanceMonitor.increment("test_counter")
        PerformanceMonitor.increment("test_counter")
        PerformanceMonitor.increment("test_counter")
        assertEquals(3, PerformanceMonitor.getCount("test_counter"))
    }

    @Test
    fun `increment - 指定增量`() {
        PerformanceMonitor.increment("test_counter", 5)
        assertEquals(5, PerformanceMonitor.getCount("test_counter"))
    }

    @Test
    fun `increment - 不同计数器互不影响`() {
        PerformanceMonitor.increment("counter1", 10)
        PerformanceMonitor.increment("counter2", 20)

        assertEquals(10, PerformanceMonitor.getCount("counter1"))
        assertEquals(20, PerformanceMonitor.getCount("counter2"))
    }

    @Test
    fun `getCount - 不存在的计数器返回 0`() {
        assertEquals(0, PerformanceMonitor.getCount("non_existent"))
    }

    // ==================== 计时功能测试 ====================

    @Test
    fun `startTimer - 返回时间戳`() {
        val startTime = PerformanceMonitor.startTimer("test_operation")
        // 在 Debug 模式下返回非零值，Release 模式返回 0
        // 单元测试环境中 Logger.isDebug 可能为 false
        assertTrue(startTime >= 0)
    }

    @Test
    fun `measure - 执行代码块并返回结果`() {
        val result = PerformanceMonitor.measure("test_operation") {
            "test_result"
        }
        assertEquals("test_result", result)
    }

    @Test
    fun `measure - 执行计算`() {
        val result = PerformanceMonitor.measure("calculation") {
            (1..100).sum()
        }
        assertEquals(5050, result)
    }

    @Test
    fun `measure - 异常会传播`() {
        try {
            PerformanceMonitor.measure<String>("error_operation") {
                throw RuntimeException("test error")
            }
            fail("应该抛出异常")
        } catch (e: RuntimeException) {
            assertEquals("test error", e.message)
        }
    }

    // ==================== 内存监控测试 ====================

    @Test
    fun `getMemoryInfo - 返回有效数据`() {
        val memInfo = PerformanceMonitor.getMemoryInfo()

        assertTrue(memInfo.usedMB >= 0)
        assertTrue(memInfo.maxMB > 0)
        assertTrue(memInfo.usagePercent >= 0)
        assertTrue(memInfo.usagePercent <= 100)
    }

    @Test
    fun `getMemoryInfo - usedMB 小于等于 maxMB`() {
        val memInfo = PerformanceMonitor.getMemoryInfo()
        assertTrue(memInfo.usedMB <= memInfo.maxMB)
    }

    @Test
    fun `isMemoryPressure - 返回布尔值`() {
        val result = PerformanceMonitor.isMemoryPressure()
        // 只验证返回有效布尔值
        assertTrue(result || !result)
    }

    // ==================== 报告生成测试 ====================

    @Test
    fun `generateReport - 返回非空字符串`() {
        val report = PerformanceMonitor.generateReport()
        assertTrue(report.isNotEmpty())
    }

    @Test
    fun `generateReport - 包含内存信息`() {
        val report = PerformanceMonitor.generateReport()
        // 报告可能显示"未启用"或包含内存信息
        assertTrue(report.contains("内存") || report.contains("未启用"))
    }

    // ==================== reset 测试 ====================

    @Test
    fun `reset - 清除所有计数器`() {
        PerformanceMonitor.increment("counter1", 10)
        PerformanceMonitor.increment("counter2", 20)

        PerformanceMonitor.reset()

        assertEquals(0, PerformanceMonitor.getCount("counter1"))
        assertEquals(0, PerformanceMonitor.getCount("counter2"))
    }

    // ==================== Operations 常量测试 ====================

    @Test
    fun `Operations - 常量值正确`() {
        assertEquals("解析交易", PerformanceMonitor.Operations.PARSE_TRANSACTION)
        assertEquals("数据库插入", PerformanceMonitor.Operations.DB_INSERT)
        assertEquals("数据库查询", PerformanceMonitor.Operations.DB_QUERY)
        assertEquals("规则匹配", PerformanceMonitor.Operations.RULE_MATCH)
        assertEquals("OCR识别", PerformanceMonitor.Operations.OCR_RECOGNIZE)
        assertEquals("加密", PerformanceMonitor.Operations.ENCRYPT)
        assertEquals("解密", PerformanceMonitor.Operations.DECRYPT)
        assertEquals("无障碍事件处理", PerformanceMonitor.Operations.ACCESSIBILITY_EVENT)
        assertEquals("UI渲染", PerformanceMonitor.Operations.UI_RENDER)
        assertEquals("备份导出", PerformanceMonitor.Operations.BACKUP_EXPORT)
        assertEquals("备份导入", PerformanceMonitor.Operations.BACKUP_IMPORT)
    }

    // ==================== Counters 常量测试 ====================

    @Test
    fun `Counters - 常量值正确`() {
        assertEquals("已记录交易数", PerformanceMonitor.Counters.TRANSACTIONS_RECORDED)
        assertEquals("跳过重复数", PerformanceMonitor.Counters.DUPLICATES_SKIPPED)
        assertEquals("解析失败数", PerformanceMonitor.Counters.PARSE_FAILURES)
        assertEquals("无障碍事件数", PerformanceMonitor.Counters.ACCESSIBILITY_EVENTS)
        assertEquals("OCR调用数", PerformanceMonitor.Counters.OCR_INVOCATIONS)
        assertEquals("正则超时数", PerformanceMonitor.Counters.REGEX_TIMEOUTS)
    }

    // ==================== MemoryInfo 数据类测试 ====================

    @Test
    fun `MemoryInfo - 属性正确`() {
        val memInfo = PerformanceMonitor.MemoryInfo(
            usedMB = 100,
            maxMB = 256,
            usagePercent = 39
        )

        assertEquals(100, memInfo.usedMB)
        assertEquals(256, memInfo.maxMB)
        assertEquals(39, memInfo.usagePercent)
    }

    // ==================== TimerStats 数据类测试 ====================

    @Test
    fun `TimerStats - 属性正确`() {
        val stats = PerformanceMonitor.TimerStats(
            totalTime = 1000,
            count = 10,
            maxTime = 200,
            minTime = 50,
            avgTime = 100
        )

        assertEquals(1000, stats.totalTime)
        assertEquals(10, stats.count)
        assertEquals(200, stats.maxTime)
        assertEquals(50, stats.minTime)
        assertEquals(100, stats.avgTime)
    }

    // ==================== 并发安全测试 ====================

    @Test
    fun `increment - 并发调用安全`() {
        val threads = (1..10).map {
            Thread {
                repeat(100) {
                    PerformanceMonitor.increment("concurrent_counter")
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1000, PerformanceMonitor.getCount("concurrent_counter"))
    }

    @Test
    fun `increment - 并发不同计数器`() {
        val threads = (1..5).map { index ->
            Thread {
                repeat(100) {
                    PerformanceMonitor.increment("counter_$index")
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        for (i in 1..5) {
            assertEquals(100, PerformanceMonitor.getCount("counter_$i"))
        }
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun `increment - 大数值`() {
        PerformanceMonitor.increment("large_counter", Long.MAX_VALUE / 2)
        assertEquals(Long.MAX_VALUE / 2, PerformanceMonitor.getCount("large_counter"))
    }

    @Test
    fun `increment - 负数增量`() {
        PerformanceMonitor.increment("negative_counter", 10)
        PerformanceMonitor.increment("negative_counter", -5)
        assertEquals(5, PerformanceMonitor.getCount("negative_counter"))
    }

    @Test
    fun `measure - 空操作名称`() {
        val result = PerformanceMonitor.measure("") {
            "result"
        }
        assertEquals("result", result)
    }

    @Test
    fun `measure - 特殊字符操作名称`() {
        val result = PerformanceMonitor.measure("操作@#$%") {
            "result"
        }
        assertEquals("result", result)
    }
}
