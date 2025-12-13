package com.example.localexpense

import com.example.localexpense.util.Constants
import com.example.localexpense.util.TransactionType
import com.example.localexpense.util.Channel
import com.example.localexpense.util.PackageNames
import com.example.localexpense.util.CategoryNames
import com.example.localexpense.util.AmountUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Constants 单元测试
 *
 * 测试覆盖：
 * - 常量值的合理性验证
 * - 常量之间的关系验证
 * - 边界值检查
 * - AmountUtils 工具方法
 */
class ConstantsTest {

    // ==================== 搜索相关常量测试 ====================

    @Test
    fun `SEARCH_DEBOUNCE_MS - 值在合理范围内`() {
        // 搜索防抖应该在 100ms - 1000ms 之间
        assertTrue(Constants.SEARCH_DEBOUNCE_MS >= 100)
        assertTrue(Constants.SEARCH_DEBOUNCE_MS <= 1000)
    }

    @Test
    fun `SEARCH_MAX_RESULTS - 值为正数`() {
        assertTrue(Constants.SEARCH_MAX_RESULTS > 0)
    }

    // ==================== 数据库查询相关常量测试 ====================

    @Test
    fun `QUERY_ALL_MAX_COUNT - 值为正数`() {
        assertTrue(Constants.QUERY_ALL_MAX_COUNT > 0)
    }

    @Test
    fun `RECENT_TRANSACTIONS_COUNT - 值为正数且合理`() {
        assertTrue(Constants.RECENT_TRANSACTIONS_COUNT > 0)
        assertTrue(Constants.RECENT_TRANSACTIONS_COUNT <= 100)
    }

    // ==================== 分页相关常量测试 ====================

    @Test
    fun `PAGE_SIZE - 值为正数`() {
        assertTrue(Constants.PAGE_SIZE > 0)
    }

    @Test
    fun `PREFETCH_DISTANCE - 值为正数`() {
        assertTrue(Constants.PREFETCH_DISTANCE > 0)
    }

    @Test
    fun `PREFETCH_DISTANCE - 小于等于 PAGE_SIZE`() {
        // 预取距离通常应该小于等于页面大小
        assertTrue(Constants.PREFETCH_DISTANCE <= Constants.PAGE_SIZE)
    }

    // ==================== 去重相关常量测试 ====================

    @Test
    fun `DUPLICATE_CHECK_INTERVAL_MS - 值为正数`() {
        assertTrue(Constants.DUPLICATE_CHECK_INTERVAL_MS > 0)
    }

    @Test
    fun `DUPLICATE_CHECK_INTERVAL_MS - 值在合理范围内`() {
        // 去重时间窗口应该在 1秒 - 5分钟 之间
        assertTrue(Constants.DUPLICATE_CHECK_INTERVAL_MS >= 1000)
        assertTrue(Constants.DUPLICATE_CHECK_INTERVAL_MS <= 5 * 60 * 1000)
    }

    @Test
    fun `ALIPAY_DUPLICATE_CHECK_INTERVAL_MS - 大于普通间隔`() {
        assertTrue(Constants.ALIPAY_DUPLICATE_CHECK_INTERVAL_MS >= Constants.DUPLICATE_CHECK_INTERVAL_MS)
    }

    // ==================== OCR 相关常量测试 ====================

    @Test
    fun `OCR_COOLDOWN_MS - 值为正数`() {
        assertTrue(Constants.OCR_COOLDOWN_MS > 0)
    }

    @Test
    fun `OCR_COOLDOWN_MS - 值在合理范围内`() {
        // OCR 冷却时间应该在 500ms - 10秒 之间
        assertTrue(Constants.OCR_COOLDOWN_MS >= 500)
        assertTrue(Constants.OCR_COOLDOWN_MS <= 10000)
    }

    // ==================== 金额相关常量测试 ====================

    @Test
    fun `MAX_AMOUNT - 值为正数`() {
        assertTrue(Constants.MAX_AMOUNT > 0)
    }

    @Test
    fun `MAX_AMOUNT - 值在合理范围内`() {
        // 最大金额应该是一个合理的大数（50000）
        assertTrue(Constants.MAX_AMOUNT >= 10000)
    }

    // ==================== 长度限制常量测试 ====================

    @Test
    fun `RAW_TEXT_MAX_LENGTH - 值为正数`() {
        assertTrue(Constants.RAW_TEXT_MAX_LENGTH > 0)
    }

    @Test
    fun `MAX_MERCHANT_NAME_LENGTH - 值为正数`() {
        assertTrue(Constants.MAX_MERCHANT_NAME_LENGTH > 0)
    }

    @Test
    fun `MAX_NOTE_LENGTH - 值为正数`() {
        assertTrue(Constants.MAX_NOTE_LENGTH > 0)
    }

    @Test
    fun `MAX_CATEGORY_NAME_LENGTH - 值为正数`() {
        assertTrue(Constants.MAX_CATEGORY_NAME_LENGTH > 0)
    }

    @Test
    fun `MAX_SEARCH_QUERY_LENGTH - 值为正数`() {
        assertTrue(Constants.MAX_SEARCH_QUERY_LENGTH > 0)
    }

    // ==================== 无障碍服务常量测试 ====================

    @Test
    fun `MAX_NODE_COLLECT_DEPTH - 值为正数`() {
        assertTrue(Constants.MAX_NODE_COLLECT_DEPTH > 0)
    }

    @Test
    fun `MAX_CHILD_NODE_COUNT - 值为正数`() {
        assertTrue(Constants.MAX_CHILD_NODE_COUNT > 0)
    }

    @Test
    fun `MAX_COLLECTED_TEXT_COUNT - 值为正数`() {
        assertTrue(Constants.MAX_COLLECTED_TEXT_COUNT > 0)
    }

    // ==================== 黑名单常量测试 ====================

    @Test
    fun `BLACKLIST_AMOUNTS - 包含运营商号码`() {
        assertTrue(Constants.BLACKLIST_AMOUNTS.contains(10086.0))
        assertTrue(Constants.BLACKLIST_AMOUNTS.contains(10010.0))
        assertTrue(Constants.BLACKLIST_AMOUNTS.contains(10000.0))
    }

    @Test
    fun `BLACKLIST_AMOUNTS - 包含银行号码`() {
        assertTrue(Constants.BLACKLIST_AMOUNTS.contains(95588.0))
        assertTrue(Constants.BLACKLIST_AMOUNTS.contains(95533.0))
    }

    @Test
    fun `BLACKLIST_INTEGER_PREFIXES - 包含运营商号码`() {
        assertTrue(Constants.BLACKLIST_INTEGER_PREFIXES.contains(10086))
        assertTrue(Constants.BLACKLIST_INTEGER_PREFIXES.contains(10010))
    }

    // ==================== TransactionType 测试 ====================

    @Test
    fun `TransactionType - 常量值正确`() {
        assertEquals("expense", TransactionType.EXPENSE)
        assertEquals("income", TransactionType.INCOME)
    }

    // ==================== Channel 测试 ====================

    @Test
    fun `Channel - 常量值正确`() {
        assertEquals("微信", Channel.WECHAT)
        assertEquals("支付宝", Channel.ALIPAY)
        assertEquals("云闪付", Channel.UNIONPAY)
        assertEquals("手动", Channel.MANUAL)
        assertEquals("其他", Channel.OTHER)
    }

    @Test
    fun `Channel - PACKAGE_MAP 包含所有监控包名`() {
        assertTrue(Channel.PACKAGE_MAP.containsKey(PackageNames.WECHAT))
        assertTrue(Channel.PACKAGE_MAP.containsKey(PackageNames.ALIPAY))
        assertTrue(Channel.PACKAGE_MAP.containsKey(PackageNames.UNIONPAY))
    }

    // ==================== PackageNames 测试 ====================

    @Test
    fun `PackageNames - 常量值正确`() {
        assertEquals("com.tencent.mm", PackageNames.WECHAT)
        assertEquals("com.eg.android.AlipayGphone", PackageNames.ALIPAY)
        assertEquals("com.unionpay", PackageNames.UNIONPAY)
    }

    @Test
    fun `PackageNames - MONITORED_PACKAGES 包含所有包名`() {
        assertTrue(PackageNames.MONITORED_PACKAGES.contains(PackageNames.WECHAT))
        assertTrue(PackageNames.MONITORED_PACKAGES.contains(PackageNames.ALIPAY))
        assertTrue(PackageNames.MONITORED_PACKAGES.contains(PackageNames.UNIONPAY))
        assertEquals(3, PackageNames.MONITORED_PACKAGES.size)
    }

    // ==================== CategoryNames 测试 ====================

    @Test
    fun `CategoryNames - 支出分类存在`() {
        assertEquals("餐饮", CategoryNames.FOOD)
        assertEquals("购物", CategoryNames.SHOPPING)
        assertEquals("交通", CategoryNames.TRANSPORT)
        assertEquals("娱乐", CategoryNames.ENTERTAINMENT)
        assertEquals("生活", CategoryNames.LIVING)
        assertEquals("医疗", CategoryNames.MEDICAL)
        assertEquals("教育", CategoryNames.EDUCATION)
    }

    @Test
    fun `CategoryNames - 收入分类存在`() {
        assertEquals("工资", CategoryNames.SALARY)
        assertEquals("奖金", CategoryNames.BONUS)
        assertEquals("红包", CategoryNames.RED_PACKET)
        assertEquals("转账", CategoryNames.TRANSFER)
        assertEquals("投资", CategoryNames.INVESTMENT)
        assertEquals("兼职", CategoryNames.PART_TIME)
    }

    // ==================== AmountUtils 测试 ====================

    @Test
    fun `AmountUtils parseAmount - 正常金额`() {
        assertEquals(123.45, AmountUtils.parseAmount("123.45")!!, 0.001)
    }

    @Test
    fun `AmountUtils parseAmount - 整数金额`() {
        assertEquals(100.0, AmountUtils.parseAmount("100")!!, 0.001)
    }

    @Test
    fun `AmountUtils parseAmount - 带逗号金额`() {
        assertEquals(1234.56, AmountUtils.parseAmount("1,234.56")!!, 0.001)
    }

    @Test
    fun `AmountUtils parseAmount - 空字符串返回 null`() {
        assertNull(AmountUtils.parseAmount(""))
        assertNull(AmountUtils.parseAmount(null))
        assertNull(AmountUtils.parseAmount("   "))
    }

    @Test
    fun `AmountUtils parseAmount - 无效字符串返回 null`() {
        assertNull(AmountUtils.parseAmount("abc"))
        assertNull(AmountUtils.parseAmount("12.34.56"))
    }

    @Test
    fun `AmountUtils format - 格式化金额`() {
        assertEquals("123.45", AmountUtils.format(123.45))
        assertEquals("100.00", AmountUtils.format(100.0))
        assertEquals("0.10", AmountUtils.format(0.1))
    }

    @Test
    fun `AmountUtils add - 加法运算`() {
        assertEquals(3.33, AmountUtils.add(1.11, 2.22), 0.001)
    }

    @Test
    fun `AmountUtils subtract - 减法运算`() {
        assertEquals(1.11, AmountUtils.subtract(3.33, 2.22), 0.001)
    }

    @Test
    fun `AmountUtils divide - 除法运算`() {
        assertEquals(2.0, AmountUtils.divide(10.0, 5.0), 0.001)
    }

    @Test
    fun `AmountUtils divide - 除零返回0`() {
        assertEquals(0.0, AmountUtils.divide(10.0, 0.0), 0.001)
    }

    @Test
    fun `AmountUtils percentage - 百分比计算`() {
        assertEquals(50.0, AmountUtils.percentage(50.0, 100.0), 0.001)
        assertEquals(25.0, AmountUtils.percentage(25.0, 100.0), 0.001)
    }

    @Test
    fun `AmountUtils percentage - 总数为零返回0`() {
        assertEquals(0.0, AmountUtils.percentage(50.0, 0.0), 0.001)
    }

    // ==================== 常量关系测试 ====================

    @Test
    fun `分页常量关系正确`() {
        // PAGE_SIZE 应该大于 PREFETCH_DISTANCE
        assertTrue(Constants.PAGE_SIZE >= Constants.PREFETCH_DISTANCE)
    }

    @Test
    fun `查询限制合理`() {
        // QUERY_ALL_MAX_COUNT 应该大于 RECENT_TRANSACTIONS_COUNT
        assertTrue(Constants.QUERY_ALL_MAX_COUNT >= Constants.RECENT_TRANSACTIONS_COUNT)
    }

    // ==================== 常量类型测试 ====================

    @Test
    fun `时间相关常量为 Long 类型`() {
        // 验证时间常量可以安全地用于时间计算
        val now = System.currentTimeMillis()
        val future = now + Constants.DUPLICATE_CHECK_INTERVAL_MS
        assertTrue(future > now)
    }

    @Test
    fun `金额相关常量为 Double 类型`() {
        // 验证金额常量可以用于金额计算
        val testAmount = 100.0
        assertTrue(testAmount <= Constants.MAX_AMOUNT)
    }
}
