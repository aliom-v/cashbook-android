package com.example.localexpense.di

import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.util.DuplicateChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint 接口
 *
 * 用于非 Hilt 管理的组件获取依赖，例如：
 * - AccessibilityService（不支持 @AndroidEntryPoint）
 * - Application 类中的某些场景
 * - 静态工具类
 *
 * 使用方式：
 * ```kotlin
 * val entryPoint = EntryPointAccessors.fromApplication(context, RepositoryEntryPoint::class.java)
 * val repository = entryPoint.transactionRepository()
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {

    /**
     * 获取 TransactionRepository 实例
     */
    fun transactionRepository(): TransactionRepository

    /**
     * 获取 ITransactionRepository 接口实例
     */
    fun iTransactionRepository(): ITransactionRepository

    /**
     * 获取 DuplicateChecker 实例
     */
    fun duplicateChecker(): DuplicateChecker
}
