package com.example.localexpense.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全的 SharedPreferences 包装类
 *
 * 使用 EncryptedSharedPreferences 存储敏感数据：
 * - 使用 AES256-GCM 加密值
 * - 使用 AES256-SIV 加密键
 * - 密钥存储在 Android Keystore 中
 *
 * 适用场景：
 * - 用户设置中的敏感配置
 * - Token、密码等敏感信息
 * - 规则版本号等需要保护的元数据
 */
object SecurePreferences {

    private const val TAG = "SecurePreferences"
    private const val PREFS_NAME = "local_expense_secure_prefs"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    @Volatile
    private var fallbackPrefs: SharedPreferences? = null

    // 是否使用加密存储（初始化失败时降级到普通存储）
    @Volatile
    private var useEncryption = true

    /**
     * 获取 SharedPreferences 实例
     * 优先使用加密存储，失败时降级到普通存储
     */
    fun getInstance(context: Context): SharedPreferences {
        // 双重检查锁定
        encryptedPrefs?.let { return it }
        fallbackPrefs?.let { if (!useEncryption) return it }

        synchronized(this) {
            encryptedPrefs?.let { return it }

            return try {
                createEncryptedPrefs(context).also {
                    encryptedPrefs = it
                    useEncryption = true
                }
            } catch (e: Exception) {
                Logger.e(TAG, "加密存储初始化失败，降级到普通存储", e)
                useEncryption = false
                createFallbackPrefs(context).also {
                    fallbackPrefs = it
                }
            }
        }
    }

    /**
     * 创建加密的 SharedPreferences
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 创建普通的 SharedPreferences（降级方案）
     */
    private fun createFallbackPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            "${PREFS_NAME}_fallback",
            Context.MODE_PRIVATE
        )
    }

    /**
     * 是否正在使用加密存储
     */
    fun isUsingEncryption(): Boolean = useEncryption

    // ========== 便捷方法 ==========

    fun getString(context: Context, key: String, default: String? = null): String? {
        return getInstance(context).getString(key, default)
    }

    fun putString(context: Context, key: String, value: String?) {
        getInstance(context).edit().putString(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int {
        return getInstance(context).getInt(key, default)
    }

    fun putInt(context: Context, key: String, value: Int) {
        getInstance(context).edit().putInt(key, value).apply()
    }

    fun getLong(context: Context, key: String, default: Long = 0L): Long {
        return getInstance(context).getLong(key, default)
    }

    fun putLong(context: Context, key: String, value: Long) {
        getInstance(context).edit().putLong(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean {
        return getInstance(context).getBoolean(key, default)
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        getInstance(context).edit().putBoolean(key, value).apply()
    }

    fun remove(context: Context, key: String) {
        getInstance(context).edit().remove(key).apply()
    }

    fun clear(context: Context) {
        getInstance(context).edit().clear().apply()
    }

    fun contains(context: Context, key: String): Boolean {
        return getInstance(context).contains(key)
    }

    // ========== 预定义的键名 ==========

    object Keys {
        // 规则相关
        const val RULES_VERSION = "rules_version"
        const val RULES_LAST_UPDATE = "rules_last_update"

        // 用户设置
        const val AUTO_RECORD_ENABLED = "auto_record_enabled"
        const val CONFIRM_DIALOG_ENABLED = "confirm_dialog_enabled"
        const val NOTIFICATION_ENABLED = "notification_enabled"
        const val OCR_ENABLED = "ocr_enabled"

        // 统计相关
        const val LAST_BACKUP_TIME = "last_backup_time"
        const val TOTAL_RECORDS_COUNT = "total_records_count"

        // 首次启动
        const val FIRST_LAUNCH = "first_launch"
        const val ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
