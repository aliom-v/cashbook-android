package com.example.localexpense

import com.example.localexpense.util.AmountUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * 金额解析工具单元测试
 * v1.9.3: 更新以匹配实际的 AmountUtils API
 */
class AmountUtilsTest {

    @Test
    fun `parseAmount should parse simple decimal`() {
        assertEquals(123.45, AmountUtils.parseAmount("123.45")!!, 0.001)
    }

    @Test
    fun `parseAmount should parse integer as decimal`() {
        assertEquals(100.0, AmountUtils.parseAmount("100")!!, 0.001)
    }

    @Test
    fun `parseAmount should parse amount with comma separator`() {
        assertEquals(1234.56, AmountUtils.parseAmount("1,234.56")!!, 0.001)
    }

    @Test
    fun `parseAmount should parse large amount with commas`() {
        assertEquals(12345678.90, AmountUtils.parseAmount("12,345,678.90")!!, 0.001)
    }

    @Test
    fun `parseAmount should return null for invalid input`() {
        assertNull(AmountUtils.parseAmount("abc"))
        assertNull(AmountUtils.parseAmount(""))
        assertNull(AmountUtils.parseAmount(null))
    }

    @Test
    fun `parseAmount should handle whitespace`() {
        assertEquals(123.45, AmountUtils.parseAmount("  123.45  ")!!, 0.001)
    }

    @Test
    fun `isValidAmount should return true for valid amounts`() {
        assertTrue(AmountUtils.isValidAmount(0.01))
        assertTrue(AmountUtils.isValidAmount(100.0))
        assertTrue(AmountUtils.isValidAmount(9999.99))
    }

    @Test
    fun `isValidAmount should return false for invalid amounts`() {
        assertFalse(AmountUtils.isValidAmount(0.0))
        assertFalse(AmountUtils.isValidAmount(-1.0))
        assertFalse(AmountUtils.isValidAmount(100000.0))  // 超过最大值 (50000)
    }

    @Test
    fun `format should format with two decimal places`() {
        assertEquals("123.45", AmountUtils.format(123.45))
        assertEquals("100.00", AmountUtils.format(100.0))
        assertEquals("0.01", AmountUtils.format(0.01))
    }

    @Test
    fun `add should add two amounts correctly`() {
        assertEquals(10.02, AmountUtils.add(5.01, 5.01), 0.001)
        assertEquals(100.0, AmountUtils.add(99.99, 0.01), 0.001)
    }

    @Test
    fun `subtract should subtract two amounts correctly`() {
        assertEquals(4.99, AmountUtils.subtract(10.0, 5.01), 0.001)
        assertEquals(0.0, AmountUtils.subtract(5.01, 5.01), 0.001)
    }

    @Test
    fun `divide should divide correctly`() {
        assertEquals(2.5, AmountUtils.divide(5.0, 2.0), 0.001)
        assertEquals(0.0, AmountUtils.divide(5.0, 0.0), 0.001) // 除零返回0
    }

    @Test
    fun `percentage should calculate percentage correctly`() {
        assertEquals(50.0, AmountUtils.percentage(50.0, 100.0), 0.001)
        assertEquals(33.33, AmountUtils.percentage(1.0, 3.0), 0.01)
        assertEquals(0.0, AmountUtils.percentage(50.0, 0.0), 0.001) // 分母为0返回0
    }

    @Test
    fun `isValidAmount should reject blacklisted amounts`() {
        // 运营商客服号码
        assertFalse(AmountUtils.isValidAmount(10086.0))
        assertFalse(AmountUtils.isValidAmount(10010.0))
        // 银行客服号码
        assertFalse(AmountUtils.isValidAmount(95588.0))
        assertFalse(AmountUtils.isValidAmount(95533.0))
    }
}
