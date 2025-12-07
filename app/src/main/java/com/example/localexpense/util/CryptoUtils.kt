package com.example.localexpense.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密工具类
 *
 * 使用 AES-GCM 加密算法和 Android Keystore 进行安全加密
 * 主要用于加密敏感数据如原始交易文本(rawText)
 *
 * 安全特性：
 * 1. 密钥存储在 Android Keystore 中，无法导出
 * 2. 使用 AES-256-GCM 提供认证加密
 * 3. 每次加密使用随机 IV，IV 与密文一起存储
 */
object CryptoUtils {

    private const val TAG = "CryptoUtils"
    private const val KEYSTORE_ALIAS = "LocalExpenseRawTextKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    // 加密前缀，用于识别已加密的数据
    private const val ENCRYPTED_PREFIX = "ENC:"

    // 是否启用加密（可通过配置控制）
    @Volatile
    private var encryptionEnabled = true

    /**
     * 初始化加密密钥
     * 应在 Application.onCreate() 中调用
     */
    fun init(context: Context) {
        try {
            getOrCreateSecretKey()
            Log.i(TAG, "加密模块初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "加密模块初始化失败，将使用明文存储: ${e.message}")
            encryptionEnabled = false
        }
    }

    /**
     * 设置是否启用加密
     */
    fun setEncryptionEnabled(enabled: Boolean) {
        encryptionEnabled = enabled
    }

    /**
     * 检查加密是否启用
     */
    fun isEncryptionEnabled(): Boolean = encryptionEnabled

    /**
     * 加密字符串
     *
     * @param plainText 明文
     * @return 加密后的 Base64 字符串（带 ENC: 前缀），加密失败返回原文
     */
    fun encrypt(plainText: String?): String {
        if (plainText.isNullOrEmpty()) return ""
        if (!encryptionEnabled) return plainText

        // 如果已经加密过，直接返回
        if (plainText.startsWith(ENCRYPTED_PREFIX)) return plainText

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // 将 IV 和密文合并存储
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "加密失败: ${e.message}")
            plainText // 加密失败返回原文
        }
    }

    /**
     * 解密字符串
     *
     * @param encryptedText 加密的字符串（带 ENC: 前缀）
     * @return 解密后的明文，解密失败返回原始输入
     */
    fun decrypt(encryptedText: String?): String {
        if (encryptedText.isNullOrEmpty()) return ""

        // 如果不是加密数据，直接返回
        if (!encryptedText.startsWith(ENCRYPTED_PREFIX)) return encryptedText

        if (!encryptionEnabled) {
            // 加密被禁用但数据是加密的，尝试解密
            Log.w(TAG, "加密已禁用，但尝试解密历史数据")
        }

        return try {
            val secretKey = getOrCreateSecretKey()
            val combined = Base64.decode(
                encryptedText.removePrefix(ENCRYPTED_PREFIX),
                Base64.NO_WRAP
            )

            // 分离 IV 和密文
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "解密失败: ${e.message}")
            // 解密失败可能是数据损坏或密钥丢失，返回占位符
            "[解密失败]"
        }
    }

    /**
     * 检查字符串是否已加密
     */
    fun isEncrypted(text: String?): Boolean {
        return text?.startsWith(ENCRYPTED_PREFIX) == true
    }

    /**
     * 获取或创建加密密钥
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // 检查密钥是否已存在
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        // 创建新密钥
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // 不要求用户认证，允许后台加解密
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * 删除加密密钥（用于测试或重置）
     * 警告：删除密钥后，所有加密数据将无法解密！
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
                Log.w(TAG, "加密密钥已删除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除密钥失败: ${e.message}")
        }
    }
}
