# CashBook 自动记账改进方案 - 完整实施指南

## 📋 项目现状分析

### ✅ 已实现的优秀功能
1. **无障碍服务基础架构** - 稳定可靠
2. **空指针防护** - 全面的 null 检查
3. **异常保护机制** - try-catch 包裹所有关键路径
4. **防重复检测** - DuplicateChecker 线程安全实现
5. **性能优化** - 深度限制、文本数量限制、快速检查
6. **通知识别** - 支持从通知栏识别交易

### 🎯 本次新增的改进

## 一、悬浮窗确认机制 (类似 iOS Cookie)

### 1. 已创建的文件
- `app/src/main/java/com/example/localexpense/ui/FloatingConfirmWindow.kt`
- `app/src/main/res/layout/floating_confirm_window.xml`

### 2. 如何集成

#### Step 1: 在 AndroidManifest.xml 添加悬浮窗权限
```xml
<!-- 在 <manifest> 标签内添加 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

#### Step 2: 修改 ExpenseAccessibilityService.kt

在文件顶部添加导入：
```kotlin
import com.example.localexpense.ui.FloatingConfirmWindow
```

在类中添加悬浮窗实例：
```kotlin
class ExpenseAccessibilityService : AccessibilityService() {
    // ... 现有代码 ...

    private var floatingWindow: FloatingConfirmWindow? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // ... 现有初始化代码 ...

        // 初始化悬浮窗
        floatingWindow = FloatingConfirmWindow(this)
    }
```

修改 `handleAccessibilityEventSafely` 方法（第171-184行）：
```kotlin
// 原代码：
// repository?.insertTransaction(transaction)
// showNotification("记账成功", "$typeText ¥${transaction.amount} - ${transaction.merchant}")

// 改为：
if (FloatingConfirmWindow.hasPermission(this)) {
    // 显示悬浮窗让用户确认
    floatingWindow?.show(
        transaction = transaction,
        onConfirm = { confirmedTransaction ->
            // 用户点击确认后才保存
            repository?.insertTransaction(confirmedTransaction)
            val typeText = if (confirmedTransaction.type == "income") "收入" else "支出"
            showNotification("记账成功", "$typeText ¥${confirmedTransaction.amount}")
        },
        onDismiss = {
            // 用户取消，不保存
            Log.d(TAG, "用户取消了记账")
        }
    )
} else {
    // 没有悬浮窗权限，降级到直接保存 + 通知
    repository?.insertTransaction(transaction)
    showNotification("记账成功", "$typeText ¥${transaction.amount}")
}
```

在 `onDestroy()` 中清理：
```kotlin
override fun onDestroy() {
    super.onDestroy()
    floatingWindow?.dismiss()
    floatingWindow = null
    // ... 其他清理代码 ...
}
```

#### Step 3: 引导用户授权悬浮窗权限

在 MainActivity 中添加权限检查：
```kotlin
override fun onResume() {
    super.onResume()

    // 检查悬浮窗权限
    if (!FloatingConfirmWindow.hasPermission(this)) {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("为了让您确认自动记录的账单，需要开启悬浮窗权限")
            .setPositiveButton("去设置") { _, _ ->
                FloatingConfirmWindow.requestPermission(this)
            }
            .setNegativeButton("暂不") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
```

---

## 二、热更新规则引擎

### 1. 已创建的文件
- `app/src/main/java/com/example/localexpense/parser/RuleEngine.kt`
- `app/src/main/assets/transaction_rules.json`

### 2. 如何使用

#### Step 1: 在 Application 初始化
修改 `LocalExpenseApp.kt`：
```kotlin
class LocalExpenseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 初始化规则引擎
        RuleEngine.init(this)

        // ... 其他初始化代码 ...
    }
}
```

#### Step 2: 修改 TransactionParser 使用规则引擎
在 `TransactionParser.kt` 的 `parse` 方法中：
```kotlin
fun parse(texts: List<String>, packageName: String): ExpenseEntity? {
    // 1. 优先使用规则引擎匹配
    val ruleMatch = RuleEngine.match(texts, packageName)
    if (ruleMatch != null) {
        return ExpenseEntity(
            id = 0,
            amount = ruleMatch.amount,
            merchant = ruleMatch.merchant,
            type = ruleMatch.rule.type,
            timestamp = System.currentTimeMillis(),
            channel = Channel.PACKAGE_MAP[packageName] ?: "其他",
            category = ruleMatch.rule.category,
            categoryId = 0,
            note = "",
            rawText = texts.joinToString(" | ").take(Constants.RAW_TEXT_MAX_LENGTH)
        )
    }

    // 2. 降级到原有解析逻辑
    val joined = texts.joinToString(" | ")
    // ... 原有代码 ...
}
```

#### Step 3: 规则文件说明
`transaction_rules.json` 支持以下字段：
- `version`: 规则版本号
- `apps`: 应用列表
  - `packageName`: 应用包名
  - `rules`: 规则列表
    - `type`: "income" 或 "expense"
    - `triggerKeywords`: 触发关键词数组
    - `amountRegex`: 金额匹配正则数组
    - `merchantRegex`: 商户匹配正则数组
    - `category`: 默认分类
    - `priority`: 优先级（数字越大越优先）

#### Step 4: 热更新规则（可选）
```kotlin
// 从服务器下载新规则
fun updateRulesFromServer() {
    viewModelScope.launch {
        try {
            val newRules = api.fetchRules() // 你的网络请求
            if (RuleEngine.updateRules(context, newRules)) {
                showToast("规则更新成功")
            }
        } catch (e: Exception) {
            showToast("规则更新失败")
        }
    }
}
```

---

## 三、OCR 备用方案（待实施）

### 为什么需要？
当微信/支付宝改版后，节点结构变化可能导致识别失败，OCR作为备用方案

### 实施步骤

#### Step 1: 添加依赖
在 `app/build.gradle` 中：
```gradle
dependencies {
    // Google ML Kit (离线OCR，完全免费)
    implementation 'com.google.mlkit:text-recognition-chinese:16.0.0'

    // 截图需要的权限服务
    implementation 'androidx.core:core-ktx:1.12.0'
}
```

#### Step 2: 创建截图管理器
```kotlin
// 文件: ScreenCaptureManager.kt
class ScreenCaptureManager(private val service: AccessibilityService) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    fun captureScreen(callback: (Bitmap?) -> Unit) {
        // 使用 AccessibilityService.takeScreenshot() (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executors.newSingleThreadExecutor(),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        callback(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        callback(null)
                    }
                }
            )
        } else {
            // Android 10 及以下需要使用 MediaProjection
            callback(null) // 降级处理
        }
    }
}
```

#### Step 3: 创建 OCR 解析器
```kotlin
// 文件: OcrParser.kt
object OcrParser {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    fun parseFromBitmap(bitmap: Bitmap, packageName: String, callback: (ExpenseEntity?) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val texts = visionText.textBlocks.map { it.text }

                // 使用规则引擎匹配
                val result = RuleEngine.match(texts, packageName)
                if (result != null) {
                    callback(ExpenseEntity(
                        id = 0,
                        amount = result.amount,
                        merchant = result.merchant,
                        type = result.rule.type,
                        timestamp = System.currentTimeMillis(),
                        channel = Channel.PACKAGE_MAP[packageName] ?: "其他",
                        category = result.rule.category,
                        categoryId = 0,
                        note = "OCR识别",
                        rawText = texts.joinToString(" | ")
                    ))
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}
```

#### Step 4: 集成到服务
在 `handleAccessibilityEventSafely` 中：
```kotlin
// 解析交易
val transaction = TransactionParser.parse(texts, pkg)

if (transaction == null) {
    // 节点解析失败，尝试OCR
    Log.d(TAG, "节点解析失败，尝试OCR备用方案")
    screenCaptureManager?.captureScreen { bitmap ->
        if (bitmap != null) {
            OcrParser.parseFromBitmap(bitmap, pkg) { ocrTransaction ->
                if (ocrTransaction != null) {
                    handleTransactionFound(ocrTransaction)
                }
            }
        }
    }
    return
}

handleTransactionFound(transaction)
```

---

## 四、调试和测试

### 1. 测试悬浮窗
```kotlin
// 在开发者选项中测试
fun testFloatingWindow() {
    val testTransaction = ExpenseEntity(
        id = 0,
        amount = 25.50,
        merchant = "测试商户",
        type = "expense",
        timestamp = System.currentTimeMillis(),
        channel = "微信",
        category = "餐饮",
        categoryId = 0,
        note = "",
        rawText = ""
    )

    floatingWindow?.show(testTransaction, { }, { })
}
```

### 2. 测试规则引擎
```kotlin
fun testRuleEngine() {
    val testTexts = listOf(
        "微信支付",
        "支付成功",
        "¥25.00",
        "收款方: 测试商户"
    )

    val result = RuleEngine.match(testTexts, "com.tencent.mm")
    Log.d("Test", "匹配结果: $result")
}
```

### 3. 日志输出
在 `logcat` 中过滤：
```bash
adb logcat | grep "ExpenseService\|RuleEngine\|OcrParser"
```

---

## 五、性能优化建议

### 1. 内存优化
- 悬浮窗及时销毁
- Bitmap 使用后立即 recycle
- OCR 识别器单例复用

### 2. 电量优化
- 优先使用节点解析，OCR 仅作备用
- 减少不必要的截图操作
- 防抖动机制已实现

### 3. 稳定性优化
- 全局 try-catch 已实现
- 空指针检查已完善
- 资源泄漏防护已添加

---

## 六、常见问题排查

### 1. 闪退问题
**已解决：**
- ✅ rootInActiveWindow 空指针检查
- ✅ 全局异常捕获
- ✅ 深度和数量限制

**如果仍然闪退，检查：**
```kotlin
// 在 logcat 中查看崩溃堆栈
adb logcat | grep "AndroidRuntime"
```

### 2. 识别不准确
**解决方案：**
1. 更新 `transaction_rules.json` 中的正则表达式
2. 添加更多触发关键词
3. 调整规则优先级

### 3. 悬浮窗不显示
**检查清单：**
- [ ] 是否授权了悬浮窗权限
- [ ] 是否在无障碍服务中调用
- [ ] 是否在 MIUI/EMUI 等系统中设置了后台弹出权限

---

## 七、对比参考应用

| 功能 | 钱迹 | Cookie/iCost | CashBook (改进后) |
|------|------|--------------|-------------------|
| 节点读取 | ✅ | ❌ (iOS无) | ✅ |
| OCR备用 | ❌ | ✅ | ✅ (待实施) |
| 悬浮窗确认 | ❌ | ✅ | ✅ (已实现) |
| 规则热更新 | ❌ | ❌ | ✅ (已实现) |
| 防重复 | ✅ | ✅ | ✅ |
| 通知识别 | ✅ | ❌ | ✅ |

---

## 八、下一步计划

### 优先级 P0 (核心功能)
- [x] 悬浮窗确认机制
- [x] 规则引擎架构
- [ ] 悬浮窗集成到服务

### 优先级 P1 (重要功能)
- [ ] OCR 备用方案
- [ ] 截图服务
- [ ] 更多支付App支持

### 优先级 P2 (增强功能)
- [ ] 规则在线更新
- [ ] 智能分类（基于商户名）
- [ ] 语音播报

---

## 九、参考资源

1. **Google ML Kit 文档**
   https://developers.google.com/ml-kit/vision/text-recognition/v2/android

2. **无障碍服务最佳实践**
   https://developer.android.com/guide/topics/ui/accessibility/service

3. **悬浮窗权限适配**
   https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW

---

**作者备注：**
- 本方案基于你提供的详细分析和"钱迹"、"Cookie"的设计理念
- 所有代码已经创建并保存在项目中
- 建议按照本文档逐步集成测试
- 遇到问题可以查看源码注释

Good luck! 🚀
