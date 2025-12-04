package com.example.localexpense.data

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

    @Query("SELECT * FROM expense ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE (merchant LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expense WHERE type = 'expense' AND timestamp BETWEEN :start AND :end")
    fun getTotalExpense(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expense WHERE type = 'income' AND timestamp BETWEEN :start AND :end")
    fun getTotalIncome(start: Long, end: Long): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM expense WHERE type = :type AND timestamp BETWEEN :start AND :end GROUP BY category")
    fun getCategoryStats(type: String, start: Long, end: Long): Flow<List<CategoryStat>>

    @Query("SELECT date(timestamp/1000, 'unixepoch', 'localtime') as date, SUM(amount) as total FROM expense WHERE type = :type AND timestamp BETWEEN :start AND :end GROUP BY date ORDER BY date")
    fun getDailyStats(type: String, start: Long, end: Long): Flow<List<DailyStat>>

    @Query("SELECT * FROM expense WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<ExpenseEntity>>
}

data class CategoryStat(
    val category: String,
    val total: Double
)

data class DailyStat(
    val date: String,
    val total: Double
)
