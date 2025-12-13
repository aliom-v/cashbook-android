package com.example.localexpense

import com.example.localexpense.util.InputValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * 输入验证器单元测试
 * v1.9.3: 更新以匹配实际的 InputValidator API
 */
class InputValidatorTest {

    @Test
    fun `removeDangerousChars removes control characters`() {
        val input = "测试\u0000文本\u001F"
        val result = InputValidator.removeDangerousChars(input)
        assertEquals("测试文本", result)
    }

    @Test
    fun `removeDangerousChars trims whitespace`() {
        assertEquals("测试", InputValidator.removeDangerousChars("  测试  "))
    }

    @Test
    fun `removeDangerousChars handles empty string`() {
        assertEquals("", InputValidator.removeDangerousChars(""))
    }

    @Test
    fun `removeDangerousChars removes SQL injection characters`() {
        val input = "测试'文本\"注入;攻击`"
        val result = InputValidator.removeDangerousChars(input)
        assertEquals("测试文本注入攻击", result)
    }

    @Test
    fun `escapeSqlLikePattern escapes special characters`() {
        assertEquals("100\\%", InputValidator.escapeSqlLikePattern("100%"))
        assertEquals("test\\_value", InputValidator.escapeSqlLikePattern("test_value"))
        assertEquals("path\\\\to", InputValidator.escapeSqlLikePattern("path\\to"))
    }

    @Test
    fun `escapeSqlLikePattern handles normal text`() {
        assertEquals("普通文本", InputValidator.escapeSqlLikePattern("普通文本"))
    }

    @Test
    fun `prepareSearchQuery combines sanitize and escape`() {
        val input = "  测试%_文本  "
        val result = InputValidator.prepareSearchQuery(input)
        assertEquals("测试\\%\\_文本", result)
    }

    @Test
    fun `prepareSearchQuery returns empty for blank input`() {
        assertEquals("", InputValidator.prepareSearchQuery(""))
        assertEquals("", InputValidator.prepareSearchQuery("   "))
    }

    @Test
    fun `prepareSearchQuery returns empty for null input`() {
        assertEquals("", InputValidator.prepareSearchQuery(null))
    }

    @Test
    fun `validateMerchant returns Valid for normal name`() {
        val result = InputValidator.validateMerchant("商户名称")
        assertTrue(result is InputValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateMerchant returns Invalid for empty name`() {
        val result = InputValidator.validateMerchant("")
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateMerchant returns Invalid for null name`() {
        val result = InputValidator.validateMerchant(null)
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateMerchant returns Invalid for dangerous characters`() {
        val result = InputValidator.validateMerchant("商户<script>")
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateAmount returns Valid for positive amount`() {
        val result = InputValidator.validateAmount("100.00")
        assertTrue(result is InputValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateAmount returns Invalid for zero amount`() {
        val result = InputValidator.validateAmount("0")
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateAmount returns Invalid for negative amount`() {
        val result = InputValidator.validateAmount("-100")
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateAmount returns Invalid for empty input`() {
        val result = InputValidator.validateAmount("")
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateAmount returns Invalid for null input`() {
        val result = InputValidator.validateAmount(null)
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validateAmount handles currency symbols`() {
        val result = InputValidator.validateAmount("¥100.00")
        assertTrue(result is InputValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateAmount returns Invalid for more than 2 decimal places`() {
        val result = InputValidator.validateAmount("100.123")
        assertTrue(result is InputValidator.ValidationResult.Invalid)
    }

    @Test
    fun `sanitizeMerchant returns default for empty input`() {
        assertEquals("未知商户", InputValidator.sanitizeMerchant(""))
        assertEquals("未知商户", InputValidator.sanitizeMerchant(null))
    }

    @Test
    fun `sanitizeMerchant cleans merchant name`() {
        val result = InputValidator.sanitizeMerchant("  商户名称  ")
        assertEquals("商户名称", result)
    }

    @Test
    fun `validateNote returns Valid for empty note`() {
        val result = InputValidator.validateNote(null)
        assertTrue(result is InputValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateNote returns Valid for normal note`() {
        val result = InputValidator.validateNote("这是备注")
        assertTrue(result is InputValidator.ValidationResult.Valid)
    }

    @Test
    fun `containsDangerousChars detects SQL injection`() {
        assertTrue(InputValidator.containsDangerousChars("'; DROP TABLE--"))
        assertTrue(InputValidator.containsDangerousChars("<script>alert(1)</script>"))
        assertFalse(InputValidator.containsDangerousChars("正常文本"))
    }

    @Test
    fun `escapeHtml escapes HTML characters`() {
        assertEquals("&lt;div&gt;", InputValidator.escapeHtml("<div>"))
        assertEquals("&amp;nbsp;", InputValidator.escapeHtml("&nbsp;"))
        assertEquals("&quot;text&quot;", InputValidator.escapeHtml("\"text\""))
    }
}
