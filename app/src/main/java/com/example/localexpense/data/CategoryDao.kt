package com.example.localexpense.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM category ORDER BY type, id")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE type = :type ORDER BY id")
    fun getByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int
}
