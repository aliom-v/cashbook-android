package com.example.localexpense.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.Locale
import java.util.TimeZone

/**
 * 设备信息工具类
 *
 * 功能：
 * 1. 获取设备基本信息
 * 2. 获取系统状态（电池、网络、内存）
 * 3. 获取屏幕信息
 * 4. 获取应用信息
 * 5. 设备兼容性检查
 *
 * 使用场景：
 * - 崩溃报告收集设备信息
 * - 用户反馈附带设备信息
 * - 功能兼容性判断
 * - 性能优化决策
 */
object DeviceUtils {

    private const val TAG = "DeviceUtils"

    // ==================== 设备信息 ====================

    /**
     * 设备基本信息
     */
    data class DeviceInfo(
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val hardware: String,
        val board: String,
        val sdkVersion: Int,
        val androidVersion: String,
        val securityPatch: String?,
        val cpuAbi: String,
        val supportedAbis: List<String>,
        val isEmulator: Boolean
    )

    /**
     * 获取设备基本信息
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            brand = Build.BRAND,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            sdkVersion = Build.VERSION.SDK_INT,
            androidVersion = Build.VERSION.RELEASE,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else null,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            isEmulator = isEmulator()
        )
    }

    /**
     * 检测是否是模拟器
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    // ==================== 屏幕信息 ====================

    /**
     * 屏幕信息
     */
    data class ScreenInfo(
        val widthPixels: Int,
        val heightPixels: Int,
        val density: Float,
        val densityDpi: Int,
        val scaledDensity: Float,
        val widthDp: Float,
        val heightDp: Float,
        val smallestWidthDp: Float,
        val orientation: String,
        val isTablet: Boolean
    )

    /**
     * 获取屏幕信息
     */
    fun getScreenInfo(context: Context): ScreenInfo {
        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration

        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        val heightDp = displayMetrics.heightPixels / displayMetrics.density

        // v1.9.5: 使用 fontScale * density 替代废弃的 scaledDensity
        val scaledDensity = configuration.fontScale * displayMetrics.density

        return ScreenInfo(
            widthPixels = displayMetrics.widthPixels,
            heightPixels = displayMetrics.heightPixels,
            density = displayMetrics.density,
            densityDpi = displayMetrics.densityDpi,
            scaledDensity = scaledDensity,
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = configuration.smallestScreenWidthDp.toFloat(),
            orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "横屏" else "竖屏",
            isTablet = configuration.smallestScreenWidthDp >= 600
        )
    }

    // ==================== 电池状态 ====================

    /**
     * 电池信息
     */
    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val chargingSource: String,
        val temperature: Float,
        val voltage: Int,
        val health: String,
        val isPowerSaveMode: Boolean
    )

    /**
     * 获取电池信息
     */
    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingSource = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线"
            else -> "未充电"
        }

        val temperature = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
            BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
            else -> "未知"
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager?.isPowerSaveMode ?: false
        } else false

        return BatteryInfo(
            level = batteryPct,
            isCharging = isCharging,
            chargingSource = chargingSource,
            temperature = temperature,
            voltage = voltage,
            health = health,
            isPowerSaveMode = isPowerSaveMode
        )
    }

    // ==================== 网络状态 ====================

    /**
     * 网络信息
     */
    data class NetworkInfo(
        val isConnected: Boolean,
        val type: String,
        val isWifi: Boolean,
        val isCellular: Boolean,
        val isMetered: Boolean
    )

    /**
     * 获取网络信息
     */
    fun getNetworkInfo(context: Context): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return NetworkInfo(false, "未知", false, false, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isConnected = capabilities != null
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isMetered = connectivityManager.isActiveNetworkMetered

            val type = when {
                isWifi -> "WiFi"
                isCellular -> "移动数据"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "以太网"
                else -> "未知"
            }

            return NetworkInfo(isConnected, type, isWifi, isCellular, isMetered)
        } else {
            @Suppress("DEPRECATION")
            val activeNetwork = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            val isConnected = activeNetwork?.isConnected == true
            @Suppress("DEPRECATION")
            val type = when (activeNetwork?.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "移动数据"
                ConnectivityManager.TYPE_ETHERNET -> "以太网"
                else -> "未知"
            }

            return NetworkInfo(
                isConnected = isConnected,
                type = type,
                isWifi = type == "WiFi",
                isCellular = type == "移动数据",
                isMetered = type != "WiFi"
            )
        }
    }

    // ==================== 内存状态 ====================

    /**
     * 内存信息
     */
    data class MemoryInfo(
        val totalMemoryMB: Long,
        val availableMemoryMB: Long,
        val usedMemoryMB: Long,
        val usedPercent: Float,
        val isLowMemory: Boolean,
        val threshold: Long,
        val appUsedMB: Long,
        val appMaxMB: Long
    )

    /**
     * 获取内存信息
     */
    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalMB = memInfo.totalMem / (1024 * 1024)
        val availableMB = memInfo.availMem / (1024 * 1024)
        val usedMB = totalMB - availableMB
        val usedPercent = if (totalMB > 0) (usedMB * 100f / totalMB) else 0f

        val runtime = Runtime.getRuntime()
        val appUsedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val appMaxMB = runtime.maxMemory() / (1024 * 1024)

        return MemoryInfo(
            totalMemoryMB = totalMB,
            availableMemoryMB = availableMB,
            usedMemoryMB = usedMB,
            usedPercent = usedPercent,
            isLowMemory = memInfo.lowMemory,
            threshold = memInfo.threshold / (1024 * 1024),
            appUsedMB = appUsedMB,
            appMaxMB = appMaxMB
        )
    }

    // ==================== 应用信息 ====================

    /**
     * 应用信息
     */
    data class AppInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val targetSdk: Int,
        val minSdk: Int,
        val firstInstallTime: Long,
        val lastUpdateTime: Long,
        val isDebug: Boolean
    )

    /**
     * 获取应用信息
     */
    fun getAppInfo(context: Context): AppInfo {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appInfo = context.applicationInfo

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }

            AppInfo(
                packageName = context.packageName,
                versionName = pInfo.versionName ?: "unknown",
                versionCode = versionCode,
                targetSdk = appInfo.targetSdkVersion,
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else 1,
                firstInstallTime = pInfo.firstInstallTime,
                lastUpdateTime = pInfo.lastUpdateTime,
                isDebug = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            )
        } catch (e: Exception) {
            Logger.e(TAG, "获取应用信息失败", e)
            AppInfo(context.packageName, "unknown", 0, 0, 0, 0, 0, false)
        }
    }

    // ==================== 系统设置 ====================

    /**
     * 获取 Android ID（设备唯一标识，重置后会变化）
     */
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    /**
     * 获取系统语言
     */
    fun getSystemLanguage(): String {
        return Locale.getDefault().language
    }

    /**
     * 获取系统地区
     */
    fun getSystemRegion(): String {
        return Locale.getDefault().country
    }

    /**
     * 获取时区
     */
    fun getTimeZone(): String {
        return TimeZone.getDefault().id
    }

    // ==================== 兼容性检查 ====================

    /**
     * 检查是否支持某个功能
     */
    fun hasFeature(context: Context, feature: String): Boolean {
        return context.packageManager.hasSystemFeature(feature)
    }

    /**
     * 是否支持指纹
     */
    fun hasFingerprintHardware(context: Context): Boolean {
        return hasFeature(context, PackageManager.FEATURE_FINGERPRINT)
    }

    /**
     * 是否支持相机
     */
    fun hasCamera(context: Context): Boolean {
        return hasFeature(context, PackageManager.FEATURE_CAMERA_ANY)
    }

    /**
     * 是否是 64 位设备
     */
    fun is64Bit(): Boolean {
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    // ==================== 报告生成 ====================

    /**
     * 生成完整设备报告
     */
    fun generateReport(context: Context): String {
        val device = getDeviceInfo()
        val screen = getScreenInfo(context)
        val battery = getBatteryInfo(context)
        val network = getNetworkInfo(context)
        val memory = getMemoryInfo(context)
        val app = getAppInfo(context)

        return buildString {
            appendLine("===== 设备信息报告 =====")
            appendLine()
            appendLine("【设备信息】")
            appendLine("  品牌: ${device.brand}")
            appendLine("  型号: ${device.model}")
            appendLine("  制造商: ${device.manufacturer}")
            appendLine("  Android: ${device.androidVersion} (SDK ${device.sdkVersion})")
            appendLine("  CPU: ${device.cpuAbi}")
            appendLine("  模拟器: ${if (device.isEmulator) "是" else "否"}")
            appendLine()
            appendLine("【屏幕信息】")
            appendLine("  分辨率: ${screen.widthPixels}x${screen.heightPixels}")
            appendLine("  密度: ${screen.densityDpi}dpi (${screen.density}x)")
            appendLine("  方向: ${screen.orientation}")
            appendLine("  平板: ${if (screen.isTablet) "是" else "否"}")
            appendLine()
            appendLine("【电池状态】")
            appendLine("  电量: ${battery.level}%")
            appendLine("  充电: ${if (battery.isCharging) battery.chargingSource else "否"}")
            appendLine("  温度: ${battery.temperature}°C")
            appendLine("  省电模式: ${if (battery.isPowerSaveMode) "是" else "否"}")
            appendLine()
            appendLine("【网络状态】")
            appendLine("  连接: ${if (network.isConnected) "是" else "否"}")
            appendLine("  类型: ${network.type}")
            appendLine("  计费: ${if (network.isMetered) "是" else "否"}")
            appendLine()
            appendLine("【内存状态】")
            appendLine("  系统: ${memory.usedMemoryMB}MB / ${memory.totalMemoryMB}MB (${String.format("%.1f", memory.usedPercent)}%)")
            appendLine("  应用: ${memory.appUsedMB}MB / ${memory.appMaxMB}MB")
            appendLine("  低内存: ${if (memory.isLowMemory) "是" else "否"}")
            appendLine()
            appendLine("【应用信息】")
            appendLine("  版本: ${app.versionName} (${app.versionCode})")
            appendLine("  调试: ${if (app.isDebug) "是" else "否"}")
            appendLine()
            appendLine("【其他】")
            appendLine("  语言: ${getSystemLanguage()}")
            appendLine("  地区: ${getSystemRegion()}")
            appendLine("  时区: ${getTimeZone()}")
            appendLine("==========================")
        }
    }
}
