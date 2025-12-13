package com.example.localexpense.domain

import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.util.CoroutineHelper
import com.example.localexpense.util.Logger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分类相关 UseCase
 *
 * 封装分类管理的业务逻辑，支持 Hilt 依赖注入
 *
 * v1.9.3 重构：
 * - 拆分为独立文件
 * - 添加 Hilt @Inject 注解
 * - 使用 ITransactionRepository 接口
 * - 增加更新分类功能
 */
@Singleton
class CategoryUseCases @Inject constructor(
    private val repository: ITransactionRepository
) {

    companion object {
        private const val TAG = "CategoryUseCases"
    }

    /**
     * 获取所有分类
     */
    fun getAllCategories(): Flow<List<CategoryEntity>> = repository.getAllCategories()

    /**
     * 根据类型获取分类（支出/收入）
     */
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>> {
        return repository.getCategoriesByType(type)
    }

    /**
     * 根据 ID 获取分类
     */
    suspend fun getCategoryById(id: Long): CategoryEntity? {
        return repository.getCategoryById(id)
    }

    /**
     * 添加分类
     */
    suspend fun addCategory(category: CategoryEntity): kotlin.Result<Long> {
        return CoroutineHelper.runSafely {
            repository.insertCategory(category)
        }.also { result ->
            result.fold(
                onSuccess = { id -> Logger.d(TAG) { "分类添加成功: id=$id" } },
                onFailure = { e -> Logger.e(TAG, "分类添加失败", e) }
            )
        }
    }

    /**
     * 更新分类
     */
    suspend fun updateCategory(category: CategoryEntity): kotlin.Result<Unit> {
        return CoroutineHelper.runSafely {
            repository.updateCategory(category)
        }.also { result ->
            result.fold(
                onSuccess = { Logger.d(TAG) { "分类更新成功: id=${category.id}" } },
                onFailure = { e -> Logger.e(TAG, "分类更新失败", e) }
            )
        }
    }

    /**
     * 删除分类
     */
    suspend fun deleteCategory(category: CategoryEntity): kotlin.Result<Unit> {
        return CoroutineHelper.runSafely {
            repository.deleteCategory(category)
        }.also { result ->
            result.fold(
                onSuccess = { Logger.d(TAG) { "分类删除成功: id=${category.id}" } },
                onFailure = { e -> Logger.e(TAG, "分类删除失败", e) }
            )
        }
    }

    /**
     * 添加或更新分类
     */
    suspend fun saveCategory(category: CategoryEntity): kotlin.Result<Long> {
        return if (category.id == 0L) {
            addCategory(category)
        } else {
            updateCategory(category).map { category.id }
        }
    }
}
