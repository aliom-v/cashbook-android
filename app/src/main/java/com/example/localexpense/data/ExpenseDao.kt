package com.example.localexpense.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExpenseEntity): Long

    @Update
    suspend fun update(entity: ExpenseEntity)

    @Delete
    suspend fun delete(entity: ExpenseEntity)

    /**
     * 获取所有记录（带限制，防止大数据集 OOM）
     */
    @Query("SELECT * FROM expense ORDER BY timestamp DESC LIMIT 1000")
    fun getAll(): Flow<List<ExpenseEntity>>

    /**
     * Paging 3 分页查询所有记录
     */
    @Query("SELECT * FROM expense ORDER BY timestamp DESC")
    fun getAllPaging(): PagingSource<Int, ExpenseEntity>

    /**
     * 分页获取所有记录
     */
    @Query("SELECT * FROM expense ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAllPaged(limit: Int, offset: Int): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>>

    /**
     * 搜索交易记录（带限制，防止大数据集ANR）
     * 注意：query 参数应在调用前进行 SQL LIKE 特殊字符转义
     */
    @Query("SELECT * FROM expense WHERE (merchant LIKE '%' || :query || '%' ESCAPE '\\' OR note LIKE '%' || :query || '%' ESCAPE '\\' OR category LIKE '%' || :query || '%' ESCAPE '\\') ORDER BY timestamp DESC LIMIT 200")
    fun search(query: String): Flow<List<ExpenseEntity>>

    /**
     * 分页搜索交易记录
     */
    @Query("SELECT * FROM expense WHERE (merchant LIKE '%' || :query || '%' ESCAPE '\\' OR note LIKE '%' || :query || '%' ESCAPE '\\' OR category LIKE '%' || :query || '%' ESCAPE '\\') ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun searchPaged(query: String, limit: Int, offset: Int): Flow<List<ExpenseEntity>>

    /**
     * Paging 3 分页搜索交易记录
     */
    @Query("SELECT * FROM expense WHERE (merchant LIKE '%' || :query || '%' ESCAPE '\\' OR note LIKE '%' || :query || '%' ESCAPE '\\' OR category LIKE '%' || :query || '%' ESCAPE '\\') ORDER BY timestamp DESC")
    fun searchPaging(query: String): PagingSource<Int, ExpenseEntity>

    /**
     * Paging 3 按日期范围分页查询
     */
    @Query("SELECT * FROM expense WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getByDateRangePaging(start: Long, end: Long): PagingSource<Int, ExpenseEntity>

    @Query("SELECT SUM(amount) FROM expense WHERE type = 'expense' AND timestamp BETWEEN :start AND :end")
    fun getTotalExpense(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expense WHERE type = 'income' AND timestamp BETWEEN :start AND :end")
    fun getTotalIncome(start: Long, end: Long): Flow<Double?>

    /**
     * 一次查询获取收支总额（优化性能）
     * 比分开查询 getTotalExpense + getTotalIncome 更高效
     */
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) as totalExpense,
            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) as totalIncome
        FROM expense
        WHERE timestamp BETWEEN :start AND :end
    """)
    fun getTotalExpenseAndIncome(start: Long, end: Long): Flow<ExpenseIncomeStat>

    @Query("SELECT category, SUM(amount) as total FROM expense WHERE type = :type AND timestamp BETWEEN :start AND :end GROUP BY category")
    fun getCategoryStats(type: String, start: Long, end: Long): Flow<List<CategoryStat>>

    /**
     * 获取分类统计（带限制和排序）
     */
    @Query("SELECT category, SUM(amount) as total FROM expense WHERE type = :type AND timestamp BETWEEN :start AND :end GROUP BY category ORDER BY total DESC LIMIT :limit")
    fun getCategoryStatsTop(type: String, start: Long, end: Long, limit: Int): Flow<List<CategoryStat>>

    @Query("SELECT date(timestamp/1000, 'unixepoch', 'localtime') as date, SUM(amount) as total FROM expense WHERE type = :type AND timestamp BETWEEN :start AND :end GROUP BY date ORDER BY date")
    fun getDailyStats(type: String, start: Long, end: Long): Flow<List<DailyStat>>

    @Query("SELECT * FROM expense WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<ExpenseEntity>>

    /**
     * 获取最近 N 条记录（用于首页展示）
     */
    @Query("SELECT * FROM expense ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ExpenseEntity>>

    /**
     * 检查是否存在重复交易（用于去重检查）
     */
    @Query("SELECT EXISTS(SELECT 1 FROM expense WHERE amount = :amount AND merchant = :merchant AND type = :type AND timestamp BETWEEN :startTime AND :endTime LIMIT 1)")
    suspend fun existsDuplicate(amount: Double, merchant: String, type: String, startTime: Long, endTime: Long): Boolean

    // 用于数据备份的一次性查询
    @Query("SELECT * FROM expense ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<ExpenseEntity>

    @Query("DELETE FROM expense")
    suspend fun deleteAll()

    // 根据ID删除单条记录
    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    // 删除指定时间之前的数据
    @Query("DELETE FROM expense WHERE timestamp < :beforeTimestamp")
    suspend fun deleteBeforeDate(beforeTimestamp: Long): Int

    // 统计指定时间之前的数据数量
    @Query("SELECT COUNT(*) FROM expense WHERE timestamp < :beforeTimestamp")
    suspend fun countBeforeDate(beforeTimestamp: Long): Int

    // 获取总记录数
    @Query("SELECT COUNT(*) FROM expense")
    suspend fun getExpenseCount(): Int

    // ==================== v1.9.6 新增优化查询 ====================

    /**
     * 按渠道筛选交易记录
     * 使用 idx_channel 索引优化
     */
    @Query("SELECT * FROM expense WHERE channel = :channel ORDER BY timestamp DESC LIMIT :limit")
    fun getByChannel(channel: String, limit: Int = 200): Flow<List<ExpenseEntity>>

    /**
     * 获取大额交易（支出）
     * 使用 idx_amount_type 索引优化
     */
    @Query("SELECT * FROM expense WHERE type = 'expense' AND amount >= :minAmount ORDER BY amount DESC LIMIT :limit")
    fun getLargeExpenses(minAmount: Double, limit: Int = 100): Flow<List<ExpenseEntity>>

    /**
     * 获取指定日期范围内的分类统计（带排序和限制）
     * 优化：使用 idx_stats 复合索引
     */
    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM expense
        WHERE type = :type AND timestamp BETWEEN :start AND :end
        GROUP BY category
        ORDER BY total DESC
        LIMIT :limit
    """)
    fun getCategoryStatsWithCount(type: String, start: Long, end: Long, limit: Int = 20): Flow<List<CategoryStatWithCount>>

    /**
     * 获取月度趋势统计（按月汇总）
     * 用于年度报表
     */
    @Query("""
        SELECT
            strftime('%Y-%m', timestamp/1000, 'unixepoch', 'localtime') as month,
            SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END) as expense,
            SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END) as income
        FROM expense
        WHERE timestamp BETWEEN :start AND :end
        GROUP BY month
        ORDER BY month
    """)
    fun getMonthlyTrend(start: Long, end: Long): Flow<List<MonthlyTrendStat>>
}

data class CategoryStat(
    val category: String,
    val total: Double
)

data class DailyStat(
    val date: String,
    val total: Double
)

/**
 * 收支统计结果（用于合并查询）
 */
data class ExpenseIncomeStat(
    val totalExpense: Double,
    val totalIncome: Double
) {
    val balance: Double get() = totalIncome - totalExpense
}

/**
 * 分类统计（带数量）- v1.9.6 新增
 */
data class CategoryStatWithCount(
    val category: String,
    val total: Double,
    val count: Int
) {
    /** 平均金额 */
    val average: Double get() = if (count > 0) total / count else 0.0
}

/**
 * 月度趋势统计 - v1.9.6 新增
 */
data class MonthlyTrendStat(
    val month: String,      // 格式: "2025-01"
    val expense: Double,
    val income: Double
) {
    /** 净收入 */
    val net: Double get() = income - expense

    /** 储蓄率（收入为0时返回0） */
    val savingsRate: Double get() = if (income > 0) (net / income * 100) else 0.0
}
