package com.example.localexpense.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budget WHERE month = :month")
    fun getByMonth(month: Int): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE month = :month AND categoryId IS NULL LIMIT 1")
    fun getTotalBudget(month: Int): Flow<BudgetEntity?>

    @Query("SELECT * FROM budget WHERE month = :month AND categoryId = :categoryId LIMIT 1")
    fun getCategoryBudget(month: Int, categoryId: Long): Flow<BudgetEntity?>
}
