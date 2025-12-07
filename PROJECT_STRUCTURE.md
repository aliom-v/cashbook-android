# 🏗️ CashBook 项目结构

## 📁 完整项目结构

```
D:\project\flow\
│
├── 📄 QUICKSTART.md                    # 快速上手指南 (6000+ 字)
├── 📄 IMPROVEMENT_GUIDE.md             # 完整实施指南 (9000+ 字)
├── 📄 UPDATE_SUMMARY.md                # 更新总结文档 (3000+ 字)
├── 📄 RELEASE-1.0.md                   # v1.0 发布说明 (已删除)
│
├── app/
│   ├── build.gradle.kts                # ✨ 已更新：添加 ML Kit 依赖
│   │
│   └── src/main/
│       ├── AndroidManifest.xml         # ✨ 已更新：添加悬浮窗权限
│       │
│       ├── java/com/example/localexpense/
│       │   │
│       │   ├── 📁 accessibility/       # 无障碍服务
│       │   │   └── ExpenseAccessibilityService.kt  # ✨ 已更新：集成所有新功能
│       │   │
│       │   ├── 📁 parser/              # 解析器模块
│       │   │   ├── TransactionParser.kt            # ✨ 已更新：优先使用规则引擎
│       │   │   └── RuleEngine.kt                   # ✨ 新增：热更新规则引擎
│       │   │
│       │   ├── 📁 ocr/                 # ✨ 新增：OCR 模块
│       │   │   ├── OcrParser.kt                    # ✨ 新增：OCR 识别解析器
│       │   │   └── ScreenCaptureManager.kt         # ✨ 新增：截图管理器
│       │   │
│       │   ├── 📁 ui/                  # UI 模块
│       │   │   ├── MainActivity.kt
│       │   │   ├── MainViewModel.kt    # ✨ 已优化：Flow 响应式
│       │   │   └── FloatingConfirmWindow.kt        # ✨ 新增：悬浮窗管理器
│       │   │
│       │   ├── 📁 data/                # 数据层
│       │   │   ├── ExpenseEntity.kt
│       │   │   ├── BudgetEntity.kt     # ✨ 已优化
│       │   │   ├── CategoryEntity.kt
│       │   │   ├── ExpenseDao.kt
│       │   │   └── TransactionRepository.kt
│       │   │
│       │   └── 📁 util/                # 工具类
│       │       ├── Constants.kt
│       │       ├── DateUtils.kt
│       │       ├── AmountUtils.kt
│       │       └── Channel.kt
│       │
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml
│       │   │   └── floating_confirm_window.xml     # ✨ 新增：悬浮窗布局
│       │   │
│       │   └── xml/
│       │       ├── expense_accessibility_config.xml
│       │       ├── backup_rules.xml
│       │       ├── data_extraction_rules.xml
│       │       └── file_paths.xml
│       │
│       └── assets/
│           └── transaction_rules.json              # ✨ 新增：规则配置文件
│
└── build.gradle.kts                    # 项目级 Gradle 配置
```

---

## 🎯 核心模块说明

### 1. 无障碍服务模块 (accessibility/)

**文件：** `ExpenseAccessibilityService.kt`

**职责：**
- 监听微信、支付宝、云闪付的界面变化
- 收集页面文本
- 调用解析器识别交易
- 管理悬浮窗显示
- 触发 OCR 备用方案

**关键方法：**
```kotlin
onServiceConnected()          // 初始化服务
onAccessibilityEvent()        // 处理无障碍事件
handleTransactionFound()      // 处理识别到的交易
tryOcrFallback()             // OCR 备用方案
```

**代码量：** ~380 行（原 260 行 + 新增 120 行）

---

### 2. 解析器模块 (parser/)

#### 2.1 TransactionParser.kt

**职责：**
- 从文本中提取交易信息
- 优先使用规则引擎匹配
- 降级到传统解析逻辑

**关键方法：**
```kotlin
parse()                      // 主解析方法（已优化）
parseNotification()          // 解析通知
extractAmount()              // 提取金额
extractMerchant()            // 提取商户
```

**代码量：** ~250 行（原 190 行 + 优化 60 行）

#### 2.2 RuleEngine.kt ✨ 新增

**职责：**
- 管理识别规则
- 从 JSON 加载规则
- 优先级匹配
- 支持热更新

**关键方法：**
```kotlin
init()                       // 初始化规则引擎
match()                      // 匹配交易规则
updateRules()                // 更新规则
parseRules()                 // 解析 JSON 规则
```

**代码量：** ~300 行

---

### 3. OCR 模块 (ocr/) ✨ 新增

#### 3.1 OcrParser.kt

**职责：**
- 使用 Google ML Kit 识别文字
- 提取交易信息
- 与规则引擎集成

**关键方法：**
```kotlin
parseFromBitmap()            // 从截图识别
handleOcrSuccess()           // 处理识别结果
release()                    // 释放资源
```

**代码量：** ~150 行

#### 3.2 ScreenCaptureManager.kt

**职责：**
- Android 11+ 截图
- 频率控制
- 资源管理

**关键方法：**
```kotlin
captureScreen()              // 截取屏幕
captureScreenInternal()      // 内部实现
handleScreenshotSuccess()    // 处理截图结果
```

**代码量：** ~200 行

---

### 4. UI 模块 (ui/)

#### 4.1 FloatingConfirmWindow.kt ✨ 新增

**职责：**
- 显示悬浮窗确认界面
- 处理用户交互
- 权限检查

**关键方法：**
```kotlin
show()                       // 显示悬浮窗
dismiss()                    // 关闭悬浮窗
hasPermission()              // 检查权限
requestPermission()          // 请求权限
```

**代码量：** ~150 行

#### 4.2 MainViewModel.kt

**已有优化：**
- Flow 响应式数据流
- combine 合并多个数据源
- 防抖搜索
- 响应式统计

**代码量：** ~300 行

---

### 5. 数据层 (data/)

**主要类：**
- `ExpenseEntity` - 交易记录实体
- `BudgetEntity` - 预算实体（已优化）
- `CategoryEntity` - 分类实体
- `TransactionRepository` - 数据仓库

**特点：**
- Room 数据库
- Flow 响应式
- 单例模式

---

### 6. 资源文件

#### 6.1 transaction_rules.json ✨ 新增

**路径：** `app/src/main/assets/transaction_rules.json`

**内容：**
- 版本信息
- 15+ 种支付场景规则
- 正则表达式配置
- 优先级设置

**大小：** ~200 行 JSON

#### 6.2 floating_confirm_window.xml ✨ 新增

**路径：** `app/src/main/res/layout/floating_confirm_window.xml`

**内容：**
- CardView 胶囊设计
- 金额、商户、分类显示
- 确认、编辑、关闭按钮

**大小：** ~120 行 XML

---

## 📊 代码规模统计

### 按模块统计

| 模块 | 原有代码 | 新增代码 | 修改代码 | 总计 |
|------|---------|---------|---------|------|
| accessibility/ | 260 | 120 | 0 | 380 |
| parser/ | 190 | 300 | 60 | 550 |
| ocr/ | 0 | 350 | 0 | 350 |
| ui/ | 800 | 150 | 0 | 950 |
| data/ | 500 | 0 | 30 | 530 |
| util/ | 200 | 0 | 0 | 200 |
| **总计** | **1950** | **920** | **90** | **2960** |

### 按语言统计

| 语言 | 代码量 | 占比 |
|------|--------|------|
| Kotlin | ~2400 行 | 81% |
| XML | ~300 行 | 10% |
| JSON | ~200 行 | 7% |
| Gradle | ~60 行 | 2% |
| **总计** | **~2960 行** | **100%** |

---

## 🔗 模块依赖关系

```
MainActivity
    ↓
MainViewModel
    ↓
TransactionRepository
    ↓
ExpenseDao (Room)
    ↓
Database

ExpenseAccessibilityService
    ↓
├── RuleEngine (规则引擎)
├── TransactionParser
│       ↓
│   RuleEngine.match()
│
├── FloatingConfirmWindow (悬浮窗)
│
└── ScreenCaptureManager (截图)
        ↓
    OcrParser (OCR识别)
        ↓
    RuleEngine.match()
```

---

## 🎯 数据流向

### 正常流程（节点读取）

```
用户支付
    ↓
无障碍事件触发
    ↓
收集页面文本
    ↓
规则引擎匹配 ✅
    ↓
显示悬浮窗
    ↓ (用户确认)
保存到数据库
    ↓
通知用户
```

### 备用流程（OCR识别）

```
用户支付
    ↓
无障碍事件触发
    ↓
收集页面文本
    ↓
规则引擎匹配 ❌
    ↓
截图 (Android 11+)
    ↓
OCR 文字识别
    ↓
规则引擎匹配 ✅
    ↓
显示悬浮窗
    ↓ (用户确认)
保存到数据库
    ↓
通知用户
```

---

## 📦 依赖关系

### 核心依赖

```gradle
// Jetpack Compose
implementation("androidx.compose.ui")
implementation("androidx.compose.material3")
implementation("androidx.lifecycle.viewmodel.compose")

// Room 数据库
implementation("androidx.room.runtime")
implementation("androidx.room.ktx")

// Navigation
implementation("androidx.navigation.compose")

// 图表库
implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

// ✨ 新增：Google ML Kit OCR
implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

// ✨ 新增：CardView
implementation("androidx.cardview:cardview:1.0.0")
```

---

## 🚀 构建流程

### 1. 编译

```bash
./gradlew assembleDebug
```

**输出：**
- `app/build/outputs/apk/debug/app-debug.apk`

### 2. 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 运行

```bash
adb shell am start -n com.example.localexpense/.ui.MainActivity
```

---

## 📝 配置文件说明

### 1. build.gradle.kts

**关键配置：**
- `minSdk = 26` (Android 8.0)
- `targetSdk = 35` (Android 15)
- `compileSdk = 35`

### 2. AndroidManifest.xml

**关键权限：**
- `POST_NOTIFICATIONS` - 通知权限
- `SYSTEM_ALERT_WINDOW` - 悬浮窗权限（新增）
- `QUERY_ALL_PACKAGES` - 查询应用包名

**关键组件：**
- `ExpenseAccessibilityService` - 无障碍服务
- `FileProvider` - 文件共享

### 3. expense_accessibility_config.xml

**关键配置：**
- `typeWindowStateChanged` - 窗口状态变化
- `typeWindowContentChanged` - 窗口内容变化
- `typeNotificationStateChanged` - 通知状态变化
- `canRetrieveWindowContent="true"` - 允许读取窗口内容
- `packageNames="com.tencent.mm,..."` - 监听的应用

---

## 🎨 UI 层次结构

```
MainActivity (Activity)
    ↓
NavigationHost
    ↓
├── HomeScreen (首页)
│   ├── 本月统计卡片
│   ├── 交易列表
│   └── 底部导航
│
├── StatsScreen (统计)
│   ├── 时间选择器
│   ├── 分类饼图
│   └── 日趋势图
│
├── CalendarScreen (日历)
│   ├── 月份选择器
│   ├── 日历网格
│   └── 日交易列表
│
└── SettingsScreen (设置)
    ├── 预算设置
    ├── 分类管理
    └── 关于页面

FloatingConfirmWindow (独立悬浮窗)
    ├── 标题栏
    ├── 金额显示
    ├── 商户/分类
    └── 操作按钮
```

---

## 📚 相关文档

- **快速上手**: `QUICKSTART.md`
- **完整指南**: `IMPROVEMENT_GUIDE.md`
- **更新总结**: `UPDATE_SUMMARY.md`
- **本文档**: `PROJECT_STRUCTURE.md`

---

**最后更新：** 2025-12-06
