# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==================== 调试信息 ====================
# 保留行号信息，方便调试崩溃日志
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==================== Kotlin ====================
# 保留 Kotlin Metadata（Room 和反射需要）
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin 反射（如果使用）
-keep class kotlin.reflect.jvm.internal.** { *; }

# ==================== Room 数据库 ====================
# Room 实体类和 DAO
-keep class com.example.localexpense.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract <methods>;
}

# Room 生成的代码
-keep class * implements androidx.room.RoomDatabase$Callback { *; }

# ==================== Compose ====================
# Compose 运行时
-keep class androidx.compose.runtime.** { *; }

# ==================== 应用特定 ====================
# 无障碍服务（系统通过反射调用）
-keep class com.example.localexpense.accessibility.ExpenseAccessibilityService { *; }

# 解析器（可能用到反射）
-keep class com.example.localexpense.parser.** { *; }

# ==================== Android 通用 ====================
# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== 优化配置 ====================
# 移除所有日志（Release 版本安全性考虑）
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# 移除自定义 Logger 类的日志输出
-assumenosideeffects class com.example.localexpense.util.Logger {
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}

# ==================== 第三方库 ====================
# Hilt / Dagger
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}

# Google ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# org.json（备份恢复使用）
-keep class org.json.** { *; }
-dontwarn org.json.**

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ==================== 备份数据类 ====================
-keep class com.example.localexpense.data.backup.BackupData { *; }
-keep class com.example.localexpense.data.backup.ImportResult { *; }

# ==================== 加密相关 ====================
# 保留加密算法类
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-dontwarn javax.crypto.**
-dontwarn java.security.**

# ==================== 工具类 ====================
# 保留工具类中需要反射或回调的部分
-keep class com.example.localexpense.util.Constants { *; }
-keep class com.example.localexpense.util.PackageNames { *; }
-keep class com.example.localexpense.util.TransactionType { *; }
-keep class com.example.localexpense.util.Channel { *; }
-keep class com.example.localexpense.util.CategoryNames { *; }

# 输入验证结果类（sealed class）
-keep class com.example.localexpense.util.InputValidator$ValidationResult { *; }
-keep class com.example.localexpense.util.InputValidator$ValidationResult$* { *; }

# 错误处理结果类
-keep class com.example.localexpense.util.ErrorHandler$ErrorType { *; }
-keep class com.example.localexpense.util.ErrorHandler$ErrorInfo { *; }

# 健康检查结果类
-keep class com.example.localexpense.util.AppHealthChecker$HealthReport { *; }
-keep class com.example.localexpense.util.AppHealthChecker$HealthReport$* { *; }
-keep class com.example.localexpense.util.AppHealthChecker$CheckResult { *; }
-keep class com.example.localexpense.util.AppHealthChecker$CheckStatus { *; }

# 崩溃报告类
-keep class com.example.localexpense.util.CrashReporter$CrashInfo { *; }
-keep class com.example.localexpense.util.CrashReporter$DeviceInfo { *; }
-keep class com.example.localexpense.util.CrashReporter$AppInfo { *; }
-keep class com.example.localexpense.util.CrashReporter$MemoryInfo { *; }
-keep class com.example.localexpense.util.CrashReporter$CrashReport { *; }

# 服务管理器状态类
-keep class com.example.localexpense.util.ServiceManager$ServiceState { *; }

# 配置管理器类
-keep class com.example.localexpense.util.ConfigManager$AppConfig { *; }
-keep class com.example.localexpense.util.ConfigManager$AppConfig$* { *; }

# 数据库优化器类
-keep class com.example.localexpense.util.DatabaseOptimizer$OptimizationResult { *; }
-keep class com.example.localexpense.util.DatabaseOptimizer$DatabaseStatus { *; }
-keep class com.example.localexpense.util.DatabaseOptimizer$IndexInfo { *; }

# 文件管理器类
-keep class com.example.localexpense.util.FileManager$StorageInfo { *; }
-keep class com.example.localexpense.util.FileManager$CleanupResult { *; }

# 限流器类
-keep class com.example.localexpense.util.RateLimiter$LimiterStats { *; }

# 数据导出类
-keep class com.example.localexpense.util.DataExporter$ExportFormat { *; }
-keep class com.example.localexpense.util.DataExporter$ExportResult { *; }
-keep class com.example.localexpense.util.DataExporter$ExportResult$* { *; }
-keep class com.example.localexpense.util.DataExporter$ExportOptions { *; }

# 筛选管理器类
-keep class com.example.localexpense.util.FilterManager$FilterCriteria { *; }
-keep class com.example.localexpense.util.FilterManager$FilterResult { *; }
-keep class com.example.localexpense.util.FilterManager$TransactionTypeFilter { *; }
-keep class com.example.localexpense.util.FilterManager$SortBy { *; }
-keep class com.example.localexpense.util.FilterManager$CategoryStats { *; }
-keep class com.example.localexpense.util.FilterManager$ChannelStats { *; }
-keep class com.example.localexpense.util.FilterManager$DayStats { *; }

# 快捷筛选类型
-keep class com.example.localexpense.ui.QuickFilterType { *; }

# 设备工具类
-keep class com.example.localexpense.util.DeviceUtils$DeviceInfo { *; }
-keep class com.example.localexpense.util.DeviceUtils$ScreenInfo { *; }
-keep class com.example.localexpense.util.DeviceUtils$BatteryInfo { *; }
-keep class com.example.localexpense.util.DeviceUtils$NetworkInfo { *; }
-keep class com.example.localexpense.util.DeviceUtils$MemoryInfo { *; }
-keep class com.example.localexpense.util.DeviceUtils$AppInfo { *; }

# 规则校验结果类
-keep class com.example.localexpense.parser.RuleValidator$ValidationResult { *; }
-keep class com.example.localexpense.parser.RuleValidator$ValidationResult$* { *; }

# 规则引擎统计类
-keep class com.example.localexpense.parser.RuleEngine$RuleStats { *; }
-keep class com.example.localexpense.parser.RuleEngine$TransactionRule { *; }
-keep class com.example.localexpense.parser.RuleEngine$MatchResult { *; }

# ==================== UI 状态类 ====================
# UI 状态（sealed class 和 data class）
-keep class com.example.localexpense.ui.MainUiState { *; }
-keep class com.example.localexpense.ui.LoadingState { *; }
-keep class com.example.localexpense.ui.LoadingState$* { *; }
-keep class com.example.localexpense.ui.OperationState { *; }
-keep class com.example.localexpense.ui.OperationState$* { *; }
-keep class com.example.localexpense.ui.UiEvent { *; }
-keep class com.example.localexpense.ui.UiEvent$* { *; }
-keep class com.example.localexpense.ui.UserIntent { *; }
-keep class com.example.localexpense.ui.UserIntent$* { *; }

# ==================== Domain 层（v1.9.5 新增） ====================
# UseCase 类（Hilt 注入）
-keep class com.example.localexpense.domain.TransactionUseCases { *; }
-keep class com.example.localexpense.domain.StatisticsUseCases { *; }
-keep class com.example.localexpense.domain.CategoryUseCases { *; }
-keep class com.example.localexpense.domain.BudgetUseCases { *; }

# Repository 接口
-keep interface com.example.localexpense.domain.repository.ITransactionRepository { *; }

# Service 接口
-keep interface com.example.localexpense.domain.service.ICryptoService { *; }
-keep interface com.example.localexpense.domain.service.IDuplicateDetector { *; }
-keep class com.example.localexpense.domain.service.DuplicateStats { *; }

# Result 类
-keep class com.example.localexpense.domain.Result { *; }
-keep class com.example.localexpense.domain.Result$* { *; }

# ==================== DI 模块（v1.9.5 新增） ====================
# Hilt Modules
-keep class com.example.localexpense.di.DatabaseModule { *; }
-keep class com.example.localexpense.di.AppModule { *; }

# EntryPoint 接口（非 Hilt 组件获取依赖）
-keep interface com.example.localexpense.di.RepositoryEntryPoint { *; }

# ==================== Batch 操作结果类 ====================
-keep class com.example.localexpense.data.BatchInsertResult { *; }
-keep class com.example.localexpense.data.BatchDeleteResult { *; }

# ==================== EncryptedSharedPreferences ====================
# Tink 加密库（EncryptedSharedPreferences 依赖）
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { *; }

# MasterKey 和 KeyGenParameterSpec
-keep class android.security.keystore.** { *; }
-dontwarn android.security.keystore.**

# ==================== 性能监控 ====================
# Release 版本移除性能监控代码
-assumenosideeffects class com.example.localexpense.util.PerformanceMonitor {
    public static long startTimer(...);
    public static void endTimer(...);
    public static void increment(...);
    public static void logReport();
}

# ==================== 额外优化 ====================
# 移除 Kotlin 断言（Release 模式不需要）
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
    public static void throwNpe(...);
    public static void throwJavaNpe(...);
    public static void throwAssert(...);
    public static void throwIllegalArgument(...);
    public static void throwIllegalState(...);
}

# 移除 Debug 相关代码
-assumenosideeffects class kotlin.jvm.internal.Reflection {
    public static kotlin.reflect.KClass getOrCreateKotlinClass(...);
}

# ==================== 代码收缩配置 ====================
# 激进的代码收缩（移除未使用的代码）
-allowaccessmodification
-repackageclasses 'o'

# 优化迭代次数
-optimizationpasses 5

# 不混淆泛型签名（某些反射库需要）
-keepattributes Signature

# 保留注解（用于依赖注入等）
-keepattributes *Annotation*

# ==================== 调试辅助（可选） ====================
# 如需调试 Release 版本，取消下面的注释
# -dontobfuscate
# -dontoptimize