# CashBook

一款纯本地的 Android 记账应用，支持自动识别微信/支付宝/云闪付交易。

[![Release](https://img.shields.io/github/v/release/aliom-v/cashbook-android)](https://github.com/aliom-v/cashbook-android/releases/latest)
[![License](https://img.shields.io/github/license/aliom-v/cashbook-android)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](https://developer.android.com)

## 下载安装

[**下载最新版本 APK**](https://github.com/aliom-v/cashbook-android/releases/latest)

> 要求 Android 8.0 (API 26) 及以上版本

## 功能特性

### 核心功能

| 功能 | 描述 |
|------|------|
| 手动记账 | 支持支出/收入记录，自定义分类和备注 |
| 自动记账 | 无障碍服务自动识别微信、支付宝、云闪付交易 |
| 统计图表 | 饼图、柱状图展示消费分布和趋势 |
| 日历视图 | 按日期查看和管理账单 |
| 预算管理 | 设置月度预算，实时追踪支出进度 |
| 分类管理 | 自定义收支分类，支持图标和颜色 |
| 搜索功能 | 快速搜索历史账单 |
| 数据导出 | 导出 CSV/JSON 文件 |
| 深色模式 | 跟随系统主题自动切换 |

### 自动记账

支持自动识别以下场景：

| 应用 | 支持场景 |
|------|---------|
| 微信 | 扫码支付、转账收款、红包收发、退款 |
| 支付宝 | 扫码支付、转账收款、红包收发、退款 |
| 云闪付 | 扫码支付、转账 |

**工作原理**：通过无障碍服务读取支付成功页面的文本，使用规则引擎提取金额、商户等信息，自动创建账单记录。

**特性**：
- 智能去重：防止同一笔交易重复记录
- 悬浮确认：识别到交易后弹出确认窗口，可修改或取消
- 规则引擎：支持热更新的交易识别规则
- 隐私保护：原始文本加密存储

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material Design 3 |
| 数据库 | Room |
| 异步处理 | Kotlin Coroutines & Flow |
| 架构模式 | MVVM |
| 图表 | Vico |
| OCR | Google ML Kit (中文) |
| 安全 | AndroidX Security Crypto |

## 项目结构

```
app/src/main/java/com/example/localexpense/
├── data/           # 数据层（Entity、DAO、Repository）
├── parser/         # 交易解析（规则引擎、解析器）
├── service/        # 服务层（无障碍服务）
├── ui/             # UI 层（Compose 界面）
│   ├── components/ # 通用组件
│   ├── screens/    # 页面
│   └── theme/      # 主题
├── util/           # 工具类
└── viewmodel/      # ViewModel
```

## 构建

```bash
# 克隆项目
git clone https://github.com/aliom-v/cashbook-android.git
cd cashbook-android

# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

APK 输出位置：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 权限说明

| 权限 | 用途 | 必需 |
|------|------|------|
| 无障碍服务 | 自动识别支付页面 | 否（自动记账需要） |
| 悬浮窗权限 | 显示交易确认窗口 | 否（自动记账需要） |
| 存储权限 | 导出数据文件 | 否（导出时需要） |

## 隐私声明

- 所有数据存储在本地设备
- 不收集任何用户信息
- 不需要网络权限
- 不上传任何数据到服务器
- 原始交易文本使用 AES 加密存储

## 更新日志

### v1.8.0
- 修复 ANR 风险，改用协程异步处理
- 修复并发竞态条件，采用原子操作
- 新增 ReDoS 防护机制
- 优化缓存清理性能

### v1.7.0
- 安全与性能修复

### v1.6.0
- 增强转账和红包识别

[查看完整更新日志](https://github.com/aliom-v/cashbook-android/releases)

## License

MIT License

---

Made with Kotlin & Jetpack Compose
