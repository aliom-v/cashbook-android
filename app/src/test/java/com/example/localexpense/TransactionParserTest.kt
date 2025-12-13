package com.example.localexpense

import com.example.localexpense.parser.TransactionParser
import org.junit.Assert.*
import org.junit.Test

/**
 * 交易解析器单元测试
 */
class TransactionParserTest {

    // ==================== 微信支付测试 ====================

    @Test
    fun `parse should extract payment success`() {
        val texts = listOf(
            "支付成功",
            "￥100.00",
            "商户：测试商户",
            "支付时间：2024-01-01 12:00:00"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(100.0, result!!.amount, 0.01)
        assertEquals("expense", result.type)
        assertEquals("测试商户", result.merchant)
    }

    @Test
    fun `parse should extract red packet income`() {
        val texts = listOf(
            "已存入零钱",
            "￥8.88",
            "来自张三的红包"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(8.88, result!!.amount, 0.01)
        assertEquals("income", result.type)
    }

    @Test
    fun `parse should extract transfer income`() {
        val texts = listOf(
            "收款成功",
            "￥500.00",
            "张三向你转账"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.01)
        assertEquals("income", result.type)
    }

    @Test
    fun `parse should recognize send red packet as expense`() {
        val texts = listOf(
            "发出红包",
            "共发出1个红包，共￥10.00"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(10.0, result!!.amount, 0.01)
        assertEquals("expense", result.type)
    }

    // ==================== 支付宝测试 ====================

    @Test
    fun `parse should handle alipay payment`() {
        val texts = listOf(
            "付款成功",
            "¥99.00",
            "商家：某某店铺"
        )
        val result = TransactionParser.parse(texts, "com.eg.android.AlipayGphone")

        assertNotNull(result)
        assertEquals(99.0, result!!.amount, 0.01)
        assertEquals("expense", result.type)
    }

    @Test
    fun `parse should handle refund as income`() {
        val texts = listOf(
            "退款成功",
            "￥50.00",
            "已退回至支付宝账户"
        )
        val result = TransactionParser.parse(texts, "com.eg.android.AlipayGphone")

        assertNotNull(result)
        assertEquals(50.0, result!!.amount, 0.01)
        assertEquals("income", result.type)
        assertEquals("退款", result.category)
    }

    // ==================== 金额解析测试 ====================

    @Test
    fun `parse should handle amount with comma separator`() {
        val texts = listOf(
            "支付成功",
            "￥1,234.56"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(1234.56, result!!.amount, 0.01)
    }

    @Test
    fun `parse should handle amount without decimal`() {
        val texts = listOf(
            "支付成功",
            "￥100"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(100.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse should ignore time format as amount`() {
        val texts = listOf(
            "支付成功",
            "12:30",  // 这是时间，不是金额
            "￥88.00"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(88.0, result!!.amount, 0.01)
    }

    // ==================== 排除关键词测试 ====================

    @Test
    fun `parse should skip payment confirmation page`() {
        val texts = listOf(
            "确认付款",
            "￥100.00",
            "请输入密码"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNull(result)  // 确认页面不应该被记录
    }

    @Test
    fun `parse should skip pending payment`() {
        val texts = listOf(
            "待支付",
            "￥200.00",
            "去支付"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNull(result)
    }

    // ==================== 商户提取测试 ====================

    @Test
    fun `parse should extract merchant from label format`() {
        val texts = listOf(
            "支付成功",
            "￥50.00",
            "商户：沃尔玛超市"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals("沃尔玛超市", result!!.merchant)
    }

    @Test
    fun `parse should extract merchant from transfer format`() {
        val texts = listOf(
            "收款成功",
            "￥100.00",
            "李四向你转账"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")

        assertNotNull(result)
        assertEquals("李四", result!!.merchant)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun `parse should return null for empty input`() {
        val result = TransactionParser.parse(emptyList(), "com.tencent.mm")
        assertNull(result)
    }

    @Test
    fun `parse should return null for no transaction keywords`() {
        val texts = listOf(
            "普通消息",
            "没有交易关键词"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")
        assertNull(result)
    }

    @Test
    fun `parse should return null for no valid amount`() {
        val texts = listOf(
            "支付成功",
            "金额未知"
        )
        val result = TransactionParser.parse(texts, "com.tencent.mm")
        assertNull(result)
    }

    // ==================== 通知解析测试 ====================

    @Test
    fun `parseNotification should extract payment notification`() {
        val text = "微信支付：你已支付￥50.00给商户A"
        val result = TransactionParser.parseNotification(text, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(50.0, result!!.amount, 0.01)
        assertEquals("expense", result.type)
    }

    @Test
    fun `parseNotification should extract income notification`() {
        // 使用包含实际收入关键词的文本
        val text = "收款成功：收到转账￥200.00"
        val result = TransactionParser.parseNotification(text, "com.tencent.mm")

        assertNotNull(result)
        assertEquals(200.0, result!!.amount, 0.01)
        assertEquals("income", result.type)
    }

    @Test
    fun `parseNotification should return null for blank text`() {
        val result = TransactionParser.parseNotification("", "com.tencent.mm")
        assertNull(result)
    }
}
