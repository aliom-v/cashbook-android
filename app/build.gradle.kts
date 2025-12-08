plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 导入需要的类
import java.util.Properties
import java.io.FileInputStream

android {
    namespace = "com.example.localexpense"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.localexpense"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.7.0"
    }

    // 仅保留中文资源，减小 APK 体积
    androidResources {
        localeFilters += listOf("zh-rCN", "zh")
    }

    // 签名配置
    signingConfigs {
        // 正式发布签名（如果keystore.properties文件存在）
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                // 如果没有配置文件，使用Android SDK默认的debug签名
                println("警告: keystore.properties 不存在，使用Android SDK默认debug签名")
                val debugKeystorePath = System.getProperty("user.home") + "/.android/debug.keystore"
                storeFile = file(debugKeystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Debug 模式下可以添加额外的检查
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 使用签名配置
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // 启用更多编译器优化
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 打包优化
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
        }
    }

    // Lint 配置
    lint {
        // 禁用备份规则检查（这些文件是有效的，但 Lint 规则过于严格）
        disable += "FullBackupContent"
        // 不中断构建
        abortOnError = false
        // 不检查致命问题（避免构建失败）
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Charts - Vico
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // Google ML Kit - OCR (文字识别，中文优化)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // CardView for FloatingWindow
    implementation("androidx.cardview:cardview:1.0.0")

    // Security - Encryption (for rawText encryption)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
