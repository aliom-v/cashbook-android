package com.example.localexpense.domain.usecase

import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 分类相关 UseCase 集合
 */
class CategoryUseCases(private val repository: TransactionRepository) {

    /**
     * 获取所有分类
     */
    fun getAllCategories(): Flow<Result<List<CategoryEntity>>> {
        return repository.getAllCategories()
            .map<List<CategoryEntity>, Result<List<CategoryEntity>>> { Result.Success(it) }
            .catch { emit(Result.Error(it, "获取分类失败")) }
    }

    /**
     * 按类型获取分类
     */
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>> {
        return repository.getCategoriesByType(type)
    }

    /**
     * 初始化默认分类
     */
    suspend fun initDefaultCategories(): Result<Unit> {
        return Result.suspendRunCatching {
            repository.initDefaultCategories()
        }
    }

    /**
     * 添加分类
     */
    suspend fun addCategory(category: CategoryEntity): Result<Long> {
        return Result.suspendRunCatching {
            repository.insertCategory(category)
        }
    }

    /**
     * 删除分类
     */
    suspend fun deleteCategory(category: CategoryEntity): Result<Unit> {
        return Result.suspendRunCatching {
            repository.deleteCategory(category)
        }
    }
}
