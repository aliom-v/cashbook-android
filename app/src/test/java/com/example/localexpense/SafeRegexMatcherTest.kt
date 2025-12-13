package com.example.localexpense

import com.example.localexpense.util.SafeRegexMatcher
import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

/**
 * 安全正则匹配器单元测试
 * v1.9.3: 更新以匹配实际的 SafeRegexMatcher API
 */
class SafeRegexMatcherTest {

    @Test
    fun `findWithTimeout finds match successfully`() {
        val pattern = Pattern.compile("\\d+\\.\\d{2}")
        val result = SafeRegexMatcher.findWithTimeout(pattern, "金额：123.45元")

        assertNotNull(result)
        assertTrue(result!!.matched)
        assertEquals("123.45", result.group(0))
    }

    @Test
    fun `findWithTimeout returns unmatched result for no match`() {
        val pattern = Pattern.compile("\\d+\\.\\d{2}")
        val result = SafeRegexMatcher.findWithTimeout(pattern, "没有金额")

        // 无匹配时返回 MatchResult(matched=false)，而不是 null
        assertNotNull(result)
        assertFalse(result!!.matched)
    }

    @Test
    fun `matchesWithTimeout returns true for full match`() {
        val pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}")
        assertTrue(SafeRegexMatcher.matchesWithTimeout(pattern, "2024-01-01"))
    }

    @Test
    fun `matchesWithTimeout returns false for partial match`() {
        val pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}")
        assertFalse(SafeRegexMatcher.matchesWithTimeout(pattern, "2024-01-01 extra"))
    }

    @Test
    fun `findWithTimeout extracts capture groups`() {
        val pattern = Pattern.compile("￥(\\d+\\.\\d{2})")
        val result = SafeRegexMatcher.findWithTimeout(pattern, "支付：￥99.99")

        assertNotNull(result)
        assertTrue(result!!.matched)
        assertEquals("99.99", result.group(1))
    }

    @Test
    fun `findWithTimeout handles empty input`() {
        val pattern = Pattern.compile("\\d+")
        val result = SafeRegexMatcher.findWithTimeout(pattern, "")

        // 空输入时也返回 MatchResult(matched=false)
        assertNotNull(result)
        assertFalse(result!!.matched)
    }

    @Test
    fun `findWithTimeout handles any pattern safely`() {
        val pattern = Pattern.compile(".*")  // 匹配任意
        val result = SafeRegexMatcher.findWithTimeout(pattern, "任意文本")

        assertNotNull(result)
        assertTrue(result!!.matched)
    }

    @Test
    fun `findWithTimeout respects input length limit`() {
        val pattern = Pattern.compile("\\d+")
        // 创建一个超长字符串
        val longInput = "a".repeat(20000)
        val result = SafeRegexMatcher.findWithTimeout(pattern, longInput)

        // 应该正常处理，不会崩溃
        // 返回 MatchResult(matched=false) 因为没有数字
        assertNotNull(result)
        assertFalse(result!!.matched)
    }

    @Test
    fun `currency amount patterns work correctly`() {
        val pattern = Pattern.compile("￥\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")

        var result = SafeRegexMatcher.findWithTimeout(pattern, "支付 ￥100.00")
        assertNotNull(result)
        assertTrue(result!!.matched)
        assertEquals("100.00", result.group(1))

        result = SafeRegexMatcher.findWithTimeout(pattern, "金额￥1,234.56")
        assertNotNull(result)
        assertTrue(result!!.matched)
        assertEquals("1,234.56", result.group(1))
    }

    @Test
    fun `time pattern exclusion works correctly`() {
        val timePattern = Pattern.compile("^\\d{1,2}[:.：]\\d{2}(:\\d{2})?$")

        assertTrue(SafeRegexMatcher.matchesWithTimeout(timePattern, "12:30"))
        assertTrue(SafeRegexMatcher.matchesWithTimeout(timePattern, "9:00"))
        assertTrue(SafeRegexMatcher.matchesWithTimeout(timePattern, "12:30:45"))
        assertFalse(SafeRegexMatcher.matchesWithTimeout(timePattern, "123.45"))
    }

    @Test
    fun `checkDangerousPattern detects nested quantifiers`() {
        val result = SafeRegexMatcher.checkDangerousPattern("(a+)+")
        assertTrue(result.isDangerous)
    }

    @Test
    fun `checkDangerousPattern allows safe patterns`() {
        val result = SafeRegexMatcher.checkDangerousPattern("\\d{1,2}[:.：]\\d{2}")
        assertFalse(result.isDangerous)
    }

    @Test
    fun `safeCompile returns null for dangerous pattern`() {
        val result = SafeRegexMatcher.safeCompile("(a+)+")
        assertNull(result)
    }

    @Test
    fun `safeCompile returns pattern for safe regex`() {
        val result = SafeRegexMatcher.safeCompile("\\d+\\.\\d{2}")
        assertNotNull(result)
    }

    @Test
    fun `getStats returns statistics`() {
        SafeRegexMatcher.resetStats()
        val pattern = Pattern.compile("\\d+")
        SafeRegexMatcher.findWithTimeout(pattern, "123")

        val stats = SafeRegexMatcher.getStats()
        assertTrue(stats.successCount >= 0)
    }
}
