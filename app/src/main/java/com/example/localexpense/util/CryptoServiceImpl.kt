package com.example.localexpense.util

import com.example.localexpense.domain.service.ICryptoService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密服务实现
 * 委托给现有的 CryptoUtils 单例
 */
@Singleton
class CryptoServiceImpl @Inject constructor() : ICryptoService {

    override fun encrypt(plainText: String): String {
        return CryptoUtils.encrypt(plainText)
    }

    override fun decrypt(encryptedText: String): String {
        return CryptoUtils.decrypt(encryptedText)
    }

    override fun isEncrypted(text: String): Boolean {
        return CryptoUtils.isEncrypted(text)
    }
}
