package com.example.localexpense.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期时间工具类
 * 注意：SimpleDateFormat 不是线程安全的，使用 ThreadLocal 确保线程安全
 */
object DateUtils {

    // 使用 ThreadLocal 确保线程安全
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    private val dateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    private val displayDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE)
    }
    private val exportDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    }

    /**
     * 统计周期
     */
    enum class StatsPeriod {
        DAY, WEEK, MONTH
    }

    /**
     * 获取日期范围
     * @return Pair<开始时间戳, 结束时间戳>
     */
    fun getDateRange(calendar: Calendar, period: StatsPeriod): Pair<Long, Long> {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.firstDayOfWeek = Calendar.MONDAY

        return when (period) {
            StatsPeriod.DAY -> {
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                Pair(start, cal.timeInMillis)
            }
            StatsPeriod.WEEK -> {
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 7)
                Pair(start, cal.timeInMillis)
            }
            StatsPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                Pair(start, cal.timeInMillis)
            }
        }
    }

    /**
     * 获取当月时间范围
     */
    fun getCurrentMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthStart = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val monthEnd = calendar.timeInMillis

        return Pair(monthStart, monthEnd)
    }

    /**
     * 获取当前月份标识 (YYYYMM)
     */
    fun getCurrentMonthId(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1
    }

    /**
     * 格式化时间戳为日期字符串 (yyyy-MM-dd)
     */
    fun formatDate(timestamp: Long): String {
        return dateFormat.get()!!.format(Date(timestamp))
    }

    /**
     * 格式化时间戳为时间字符串 (HH:mm)
     */
    fun formatTime(timestamp: Long): String {
        return timeFormat.get()!!.format(Date(timestamp))
    }

    /**
     * 格式化时间戳为完整日期时间字符串
     */
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.get()!!.format(Date(timestamp))
    }

    /**
     * 格式化为显示用的日期 (MM月dd日 星期X)
     */
    fun formatDisplayDate(dateString: String): String {
        return try {
            val date = dateFormat.get()!!.parse(dateString)
            displayDateFormat.get()!!.format(date!!)
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * 获取导出文件名用的日期
     */
    fun getExportDateString(): String {
        return exportDateFormat.get()!!.format(Date())
    }

    /**
     * 获取今天的日期字符串
     */
    fun getTodayString(): String {
        return dateFormat.get()!!.format(Date())
    }
}
