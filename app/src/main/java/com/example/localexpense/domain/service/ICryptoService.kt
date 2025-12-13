package com.example.localexpense.domain.service

/**
 * 加密服务接口
 * 提供字符串加密和解密功能
 */
interface ICryptoService {

    /**
     * 加密字符串
     * @param plainText 明文
     * @return 加密后的字符串
     */
    fun encrypt(plainText: String): String

    /**
     * 解密字符串
     * @param encryptedText 加密的字符串
     * @return 解密后的明文
     */
    fun decrypt(encryptedText: String): String

    /**
     * 检查字符串是否已加密
     * @param text 待检查的字符串
     * @return 如果已加密返回 true
     */
    fun isEncrypted(text: String): Boolean
}
