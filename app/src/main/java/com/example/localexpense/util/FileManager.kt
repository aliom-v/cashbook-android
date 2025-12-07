package com.example.localexpense.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 文件管理工具
 *
 * 功能：
 * 1. 应用文件目录管理
 * 2. 缓存清理
 * 3. 存储空间监控
 * 4. 文件压缩/解压
 * 5. 文件校验（MD5/SHA256）
 * 6. 安全的文件操作
 */
object FileManager {

    private const val TAG = "FileManager"

    // 目录名称
    private const val DIR_BACKUP = "backups"
    private const val DIR_EXPORT = "exports"
    private const val DIR_CACHE = "cache"
    private const val DIR_LOGS = "logs"
    private const val DIR_TEMP = "temp"

    // 文件大小限制
    private const val MAX_BACKUP_SIZE_MB = 100L
    private const val MAX_CACHE_SIZE_MB = 50L

    /**
     * 存储信息
     */
    data class StorageInfo(
        val totalBytes: Long,
        val availableBytes: Long,
        val appDataBytes: Long,
        val cacheBytes: Long,
        val backupBytes: Long
    ) {
        val totalMB: Float get() = totalBytes / (1024f * 1024f)
        val availableMB: Float get() = availableBytes / (1024f * 1024f)
        val appDataMB: Float get() = appDataBytes / (1024f * 1024f)
        val cacheMB: Float get() = cacheBytes / (1024f * 1024f)
        val usedPercent: Float get() = if (totalBytes > 0) ((totalBytes - availableBytes) * 100f / totalBytes) else 0f
    }

    /**
     * 清理结果
     */
    data class CleanupResult(
        val success: Boolean,
        val freedBytes: Long,
        val deletedFiles: Int,
        val message: String
    ) {
        val freedMB: Float get() = freedBytes / (1024f * 1024f)
    }

    // ==================== 目录管理 ====================

    /**
     * 获取备份目录
     */
    fun getBackupDir(context: Context): File {
        return getOrCreateDir(context.filesDir, DIR_BACKUP)
    }

    /**
     * 获取导出目录
     */
    fun getExportDir(context: Context): File {
        return getOrCreateDir(context.filesDir, DIR_EXPORT)
    }

    /**
     * 获取日志目录
     */
    fun getLogsDir(context: Context): File {
        return getOrCreateDir(context.filesDir, DIR_LOGS)
    }

    /**
     * 获取临时目录
     */
    fun getTempDir(context: Context): File {
        return getOrCreateDir(context.cacheDir, DIR_TEMP)
    }

    /**
     * 创建或获取目录
     */
    private fun getOrCreateDir(parent: File, name: String): File {
        val dir = File(parent, name)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // ==================== 存储监控 ====================

    /**
     * 获取存储信息
     */
    suspend fun getStorageInfo(context: Context): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val dataDir = context.filesDir
            val stat = StatFs(dataDir.path)

            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

            // 计算应用数据大小
            val appDataBytes = calculateDirSize(context.filesDir) +
                    calculateDirSize(context.cacheDir) +
                    DatabaseOptimizer.getDatabaseSize(context)

            // 计算缓存大小
            val cacheBytes = calculateDirSize(context.cacheDir)

            // 计算备份大小
            val backupBytes = calculateDirSize(getBackupDir(context))

            StorageInfo(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                appDataBytes = appDataBytes,
                cacheBytes = cacheBytes,
                backupBytes = backupBytes
            )
        } catch (e: Exception) {
            Logger.e(TAG, "获取存储信息失败", e)
            StorageInfo(0, 0, 0, 0, 0)
        }
    }

    /**
     * 计算目录大小
     */
    fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()

        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    /**
     * 检查存储空间是否充足
     */
    suspend fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        val info = getStorageInfo(context)
        return info.availableBytes >= requiredBytes
    }

    // ==================== 清理操作 ====================

    /**
     * 清理缓存
     */
    suspend fun clearCache(context: Context): CleanupResult = withContext(Dispatchers.IO) {
        val startSize = calculateDirSize(context.cacheDir)
        var deletedFiles = 0

        try {
            deletedFiles = deleteRecursively(context.cacheDir, keepRoot = true)
            val freedBytes = startSize - calculateDirSize(context.cacheDir)

            Logger.i(TAG, "缓存清理完成: 删除 $deletedFiles 个文件, 释放 ${freedBytes / 1024}KB")

            CleanupResult(
                success = true,
                freedBytes = freedBytes,
                deletedFiles = deletedFiles,
                message = "缓存清理完成"
            )
        } catch (e: Exception) {
            Logger.e(TAG, "清理缓存失败", e)
            CleanupResult(false, 0, deletedFiles, "清理失败: ${e.message}")
        }
    }

    /**
     * 清理临时文件
     */
    suspend fun clearTempFiles(context: Context): CleanupResult = withContext(Dispatchers.IO) {
        val tempDir = getTempDir(context)
        val startSize = calculateDirSize(tempDir)
        var deletedFiles = 0

        try {
            deletedFiles = deleteRecursively(tempDir, keepRoot = true)
            val freedBytes = startSize - calculateDirSize(tempDir)

            CleanupResult(
                success = true,
                freedBytes = freedBytes,
                deletedFiles = deletedFiles,
                message = "临时文件清理完成"
            )
        } catch (e: Exception) {
            Logger.e(TAG, "清理临时文件失败", e)
            CleanupResult(false, 0, deletedFiles, "清理失败: ${e.message}")
        }
    }

    /**
     * 清理旧备份（保留最近 N 个）
     */
    suspend fun cleanupOldBackups(context: Context, keepCount: Int = 5): CleanupResult = withContext(Dispatchers.IO) {
        val backupDir = getBackupDir(context)
        val startSize = calculateDirSize(backupDir)
        var deletedFiles = 0

        try {
            val backups = backupDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (backups.size > keepCount) {
                backups.drop(keepCount).forEach { file ->
                    if (file.delete()) {
                        deletedFiles++
                        Logger.d(TAG) { "删除旧备份: ${file.name}" }
                    }
                }
            }

            val freedBytes = startSize - calculateDirSize(backupDir)

            CleanupResult(
                success = true,
                freedBytes = freedBytes,
                deletedFiles = deletedFiles,
                message = "旧备份清理完成"
            )
        } catch (e: Exception) {
            Logger.e(TAG, "清理旧备份失败", e)
            CleanupResult(false, 0, deletedFiles, "清理失败: ${e.message}")
        }
    }

    /**
     * 递归删除目录内容
     */
    private fun deleteRecursively(file: File, keepRoot: Boolean = false): Int {
        var count = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                count += deleteRecursively(child, keepRoot = false)
            }
        }
        if (!keepRoot && file.delete()) {
            count++
        }
        return count
    }

    // ==================== 文件压缩 ====================

    /**
     * 压缩文件到 ZIP
     */
    suspend fun compressToZip(
        sourceFiles: List<File>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                sourceFiles.forEach { file ->
                    if (file.exists()) {
                        addToZip(zos, file, file.name)
                    }
                }
            }
            Logger.d(TAG) { "压缩完成: ${outputFile.name}" }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "压缩文件失败", e)
            false
        }
    }

    /**
     * 添加文件到 ZIP
     */
    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                addToZip(zos, child, "$entryName/${child.name}")
            }
        } else {
            FileInputStream(file).use { fis ->
                zos.putNextEntry(ZipEntry(entryName))
                fis.copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    /**
     * 解压 ZIP 文件
     */
    suspend fun extractZip(
        zipFile: File,
        outputDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(outputDir, entry.name)

                    // 安全检查：防止 Zip Slip 攻击
                    if (!outFile.canonicalPath.startsWith(outputDir.canonicalPath)) {
                        throw SecurityException("Zip entry outside target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            Logger.d(TAG) { "解压完成: ${zipFile.name}" }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "解压文件失败", e)
            false
        }
    }

    // ==================== 文件校验 ====================

    /**
     * 计算文件 MD5
     */
    suspend fun calculateMD5(file: File): String? = withContext(Dispatchers.IO) {
        calculateHash(file, "MD5")
    }

    /**
     * 计算文件 SHA-256
     */
    suspend fun calculateSHA256(file: File): String? = withContext(Dispatchers.IO) {
        calculateHash(file, "SHA-256")
    }

    /**
     * 计算文件哈希
     */
    private fun calculateHash(file: File, algorithm: String): String? {
        if (!file.exists()) return null

        return try {
            val digest = MessageDigest.getInstance(algorithm)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(TAG, "计算哈希失败", e)
            null
        }
    }

    /**
     * 验证文件哈希
     */
    suspend fun verifyHash(file: File, expectedHash: String, algorithm: String = "SHA-256"): Boolean {
        val actualHash = calculateHash(file, algorithm)
        return actualHash?.equals(expectedHash, ignoreCase = true) == true
    }

    // ==================== 文件操作 ====================

    /**
     * 安全复制文件
     */
    suspend fun copyFile(source: File, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "复制文件失败", e)
            false
        }
    }

    /**
     * 安全移动文件
     */
    suspend fun moveFile(source: File, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            if (source.renameTo(dest)) {
                true
            } else {
                // renameTo 跨文件系统可能失败，使用复制+删除
                if (copyFile(source, dest)) {
                    source.delete()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "移动文件失败", e)
            false
        }
    }

    /**
     * 生成唯一文件名
     */
    fun generateUniqueFileName(prefix: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val random = (1000..9999).random()
        return "${prefix}_${timestamp}_$random.$extension"
    }

    /**
     * 获取文件扩展名
     */
    fun getFileExtension(file: File): String {
        return file.name.substringAfterLast('.', "")
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.2f", bytes / (1024f * 1024f))} MB"
            else -> "${String.format("%.2f", bytes / (1024f * 1024f * 1024f))} GB"
        }
    }

    /**
     * 生成存储报告
     */
    suspend fun generateReport(context: Context): String = withContext(Dispatchers.IO) {
        val info = getStorageInfo(context)

        buildString {
            appendLine("===== 存储状态报告 =====")
            appendLine()
            appendLine("【设备存储】")
            appendLine("  总空间: ${formatFileSize(info.totalBytes)}")
            appendLine("  可用空间: ${formatFileSize(info.availableBytes)}")
            appendLine("  使用率: ${String.format("%.1f", info.usedPercent)}%")
            appendLine()
            appendLine("【应用存储】")
            appendLine("  应用数据: ${formatFileSize(info.appDataBytes)}")
            appendLine("  缓存: ${formatFileSize(info.cacheBytes)}")
            appendLine("  备份: ${formatFileSize(info.backupBytes)}")
            appendLine()
            appendLine("【目录信息】")
            appendLine("  备份目录: ${getBackupDir(context).absolutePath}")
            appendLine("  导出目录: ${getExportDir(context).absolutePath}")
            appendLine("  日志目录: ${getLogsDir(context).absolutePath}")
            appendLine("==========================")
        }
    }
}
