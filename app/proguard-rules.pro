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
# Compose 运行时（不需要保留所有类，让 R8 优化）
-keep,allowoptimization class androidx.compose.runtime.** { *; }

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
# 移除调试日志（Release 版本）
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}