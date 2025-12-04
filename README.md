# CashBook 📒

一款纯本地的 Android 记账应用，支持自动识别微信/支付宝交易。

## ✨ 功能特性

| 功能 | 描述 |
|------|------|
| 📝 手动记账 | 支持支出/收入记录，自定义分类 |
| 🤖 自动记账 | 无障碍服务自动识别微信、支付宝、云闪付交易 |
| 📊 统计图表 | 饼图、柱状图展示消费分布和趋势 |
| 📅 日历视图 | 按日期查看和管理账单 |
| 💰 预算管理 | 设置月度预算，实时追踪支出进度 |
| 🏷️ 分类管理 | 自定义收支分类，支持图标和颜色 |
| 🔍 搜索功能 | 快速搜索历史账单 |
| 📤 数据导出 | 导出 CSV 文件，支持 Excel 打开 |
| 🌙 深色模式 | 跟随系统主题自动切换 |

## 📱 截图

*待添加*

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **数据库**: Room
- **异步**: Kotlin Coroutines & Flow
- **架构**: MVVM

## 🚀 构建

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 📋 权限说明

| 权限 | 用途 | 必需 |
|------|------|------|
| 无障碍服务 | 自动识别支付页面 | 否（可选） |
| 存储权限 | 导出 CSV 文件 | 否（导出时需要） |

## 🔒 隐私

- ✅ 所有数据存储在本地设备
- ✅ 不收集任何用户信息
- ✅ 不需要网络权限
- ✅ 不上传任何数据到服务器

## 📄 License

MIT License

---

Made with ❤️ using Kotlin & Jetpack Compose
