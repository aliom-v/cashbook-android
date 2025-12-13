package com.example.localexpense.di

import com.example.localexpense.data.TransactionRepository
import com.example.localexpense.domain.repository.ITransactionRepository
import com.example.localexpense.domain.service.ICryptoService
import com.example.localexpense.domain.service.IDuplicateDetector
import com.example.localexpense.util.CryptoServiceImpl
import com.example.localexpense.util.DuplicateChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 接口绑定模块
 * 将接口绑定到具体实现
 *
 * 注意：DuplicateChecker、TransactionRepository、CryptoServiceImpl
 * 都通过 @Inject constructor 自动提供，无需手动 @Provides
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        repository: TransactionRepository
    ): ITransactionRepository

    @Binds
    @Singleton
    abstract fun bindCryptoService(
        cryptoService: CryptoServiceImpl
    ): ICryptoService

    @Binds
    @Singleton
    abstract fun bindDuplicateDetector(
        duplicateChecker: DuplicateChecker
    ): IDuplicateDetector
}
