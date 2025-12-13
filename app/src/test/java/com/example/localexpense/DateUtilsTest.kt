package com.example.localexpense

import com.example.localexpense.util.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * 日期工具类单元测试
 * v1.9.3: 更新以匹配实际的 DateUtils API
 */
class DateUtilsTest {

    @Test
    fun `formatDate should return correct format`() {
        val calendar = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 15, 10, 30, 0)
        }
        val result = DateUtils.formatDate(calendar.timeInMillis)

        assertTrue(result.contains("2024"))
        assertTrue(result.contains("01"))
        assertTrue(result.contains("15"))
    }

    @Test
    fun `formatTime should return correct format`() {
        val calendar = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 15, 14, 30, 45)
        }
        val result = DateUtils.formatTime(calendar.timeInMillis)

        assertTrue(result.contains("14:30"))
    }

    @Test
    fun `getCurrentMonthRange should return valid range`() {
        val (start, end) = DateUtils.getCurrentMonthRange()

        assertTrue(start < end)
        assertTrue(start > 0)
        assertTrue(end > 0)

        // start 应该是月初
        val startCalendar = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(1, startCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCalendar.get(Calendar.MINUTE))
    }

    @Test
    fun `getCurrentMonthId should return YYYYMM format`() {
        val monthId = DateUtils.getCurrentMonthId()

        // getCurrentMonthId 返回 Int 格式 YYYYMM (如 202412)
        assertTrue(monthId >= 202400)  // 2024年及以后
        assertTrue(monthId <= 210012)  // 合理范围内

        // 验证月份部分 (01-12)
        val month = monthId % 100
        assertTrue(month in 1..12)
    }

    @Test
    fun `getDateRange should return correct range for DAY`() {
        val calendar = Calendar.getInstance()
        val (start, end) = DateUtils.getDateRange(calendar, DateUtils.StatsPeriod.DAY)

        assertTrue(start < end)
        // 一天的毫秒数
        assertEquals(24 * 60 * 60 * 1000L, end - start)
    }

    @Test
    fun `getDateRange should return correct range for WEEK`() {
        val calendar = Calendar.getInstance()
        val (start, end) = DateUtils.getDateRange(calendar, DateUtils.StatsPeriod.WEEK)

        assertTrue(start < end)
        // 一周的毫秒数
        assertEquals(7 * 24 * 60 * 60 * 1000L, end - start)
    }

    @Test
    fun `getDateRange should return correct range for MONTH`() {
        val calendar = Calendar.getInstance()
        val (start, end) = DateUtils.getDateRange(calendar, DateUtils.StatsPeriod.MONTH)

        assertTrue(start < end)
        assertTrue(end - start >= 27 * 24 * 60 * 60 * 1000L) // 至少 27 天
    }

    @Test
    fun `formatDateTime should return correct format`() {
        val calendar = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 20, 15, 45, 30)
        }
        val result = DateUtils.formatDateTime(calendar.timeInMillis)

        assertTrue(result.contains("2024"))
        assertTrue(result.contains("03"))
        assertTrue(result.contains("20"))
        assertTrue(result.contains("15:45"))
    }

    @Test
    fun `formatShortDate should return MM月dd日 format`() {
        val calendar = Calendar.getInstance().apply {
            set(2024, Calendar.DECEMBER, 25, 0, 0, 0)
        }
        val result = DateUtils.formatShortDate(calendar.timeInMillis)

        assertTrue(result.contains("12月"))
        assertTrue(result.contains("25日"))
    }

    @Test
    fun `getTodayString should return today's date`() {
        val result = DateUtils.getTodayString()
        val today = Calendar.getInstance()

        val year = today.get(Calendar.YEAR).toString()
        assertTrue(result.contains(year))
    }

    @Test
    fun `formatMonthYear should return yyyy年MM月 format`() {
        val calendar = Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 15)
        }
        val result = DateUtils.formatMonthYear(calendar)

        assertTrue(result.contains("2024年"))
        assertTrue(result.contains("06月") || result.contains("6月"))
    }

    // ==================== v1.9.7 新增测试 ====================

    @Test
    fun `getCurrentYearRange should return valid range`() {
        val (start, end) = DateUtils.getCurrentYearRange()

        assertTrue(start < end)
        assertTrue(start > 0)
        assertTrue(end > 0)

        // start 应该是年初 1月1日
        val startCalendar = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(Calendar.JANUARY, startCalendar.get(Calendar.MONTH))
        assertEquals(1, startCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCalendar.get(Calendar.MINUTE))
        assertEquals(0, startCalendar.get(Calendar.SECOND))
    }

    @Test
    fun `getCurrentYearRange should span approximately one year`() {
        val (start, end) = DateUtils.getCurrentYearRange()

        // 一年大约 365 天（闰年 366 天）
        val daysDiff = (end - start) / (24 * 60 * 60 * 1000L)
        assertTrue(daysDiff in 365..366)
    }

    @Test
    fun `getCurrentYearRange end should be next year January 1st`() {
        val (_, end) = DateUtils.getCurrentYearRange()

        val endCalendar = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(Calendar.JANUARY, endCalendar.get(Calendar.MONTH))
        assertEquals(1, endCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, endCalendar.get(Calendar.HOUR_OF_DAY))
    }
}
