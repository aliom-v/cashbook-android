# CashBook 1.0 Release 发布说明

## 📦 版本信息

**版本号**: 1.0
**版本代码**: 1
**发布日期**: 2025-12-06
**APK文件**: `app/build/outputs/apk/release/app-release.apk`
**文件大小**: ~1.9 MB
**最低Android版本**: Android 8.0 (API 26)
**目标Android版本**: Android 14 (API 35)

---

## ✨ 主要功能

### 1. 自动记账
- ✅ 无障碍服务自动监听微信、支付宝、云闪付交易
- ✅ 自动解析交易金额、商户、类型
- ✅ 智能识别收入/支出
- ✅ 防重复记录机制

### 2. 手动记账
- ✅ 支持手动添加/编辑/删除交易
- ✅ 自定义分类和金额
- ✅ 备注功能

### 3. 数据统计
- ✅ 日/周/月统计视图
- ✅ 分类占比图表
- ✅ 趋势分析
- ✅ 预算管理和进度提醒

### 4. 数据管理
- ✅ 本地数据库存储（Room）
- ✅ 数据导出/导入（JSON格式）
- ✅ 数据备份和恢复

### 5. 界面
- ✅ Material Design 3 设计规范
- ✅ 深色模式支持
- ✅ Jetpack Compose 现代化UI
- ✅ 流畅的动画效果

---

## 🐛 本次更新修复的问题

### 关键Bug修复
1. ✅ **Repository初始化竞态条件** - 修复了无障碍服务启动时数据库未就绪的问题
2. ✅ **Android 11+包名过滤失效** - 添加了QUERY_ALL_PACKAGES权限
3. ✅ **AccessibilityNodeInfo资源泄漏** - 添加了手动回收机制
4. ✅ **通知超时优化** - 从500ms降低到100ms，减少事件丢失
5. ✅ **浮点精度问题** - 使用BigDecimal进行精确金额计算
6. ✅ **SQL LIKE注入** - 添加了特殊字符转义
7. ✅ **Flow重复订阅** - 使用stateIn缓存优化性能

### 代码质量改进
- ✅ 完善的异常处理
- ✅ 线程安全的日期格式化
- ✅ ProGuard代码混淆和优化
- ✅ 资源压缩（仅保留中文）

---

## 📲 安装说明

### 方式1: 直接安装APK
```bash
# APK文件位置
app/build/outputs/apk/release/app-release.apk

# 通过ADB安装
adb install app/build/outputs/apk/release/app-release.apk
```

### 方式2: 传输到手机安装
1. 将APK文件复制到手机
2. 在手机上打开文件管理器
3. 点击APK文件安装（需要允许"未知来源"）

---

## ⚙️ 首次使用配置

### 1. 启用无障碍服务
```
设置 → 无障碍 → 已安装的服务 → CashBook → 启用
```

### 2. 授予必要权限
- ✅ 通知权限（Android 13+）
- ✅ 查询所有应用权限（Android 11+）

### 3. 测试功能
- 打开微信/支付宝
- 进行一笔测试支付
- 返回CashBook查看是否自动记录

---

## ⚠️ 重要提示

### 关于签名
**本版本使用的是Android SDK默认的debug签名**

#### Debug签名的特点：
- ✅ 可以正常安装和使用
- ✅ 所有功能完全可用
- ❌ **不能上传到应用商店**（Google Play、华为应用市场等）
- ❌ **无法保证应用身份**（其他人也可以用debug签名）
- ❌ **更新时可能需要卸载重装**

#### 如果需要正式发布：
请参考 `如何创建正式签名.md` 文档

---

## 🔐 创建正式签名（用于正式发布）

### 为什么需要正式签名？
1. **应用更新** - 使用同一签名才能无缝更新
2. **应用商店** - Google Play等要求正式签名
3. **安全性** - 防止他人冒充发布假的更新版本

### 创建步骤

#### 方式1: 使用提供的PowerShell脚本
```powershell
# 在项目根目录运行
.\generate-keystore.ps1
```

这将生成：
- `cashbook-release.jks` - 密钥库文件（**务必备份！**）
- `keystore.properties` - 配置文件（**不要提交到Git！**）

#### 方式2: 手动创建
```bash
keytool -genkeypair -v \
    -keystore cashbook-release.jks \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -alias cashbook-key \
    -storepass "你的密码" \
    -keypass "你的密码" \
    -dname "CN=你的名字,OU=Development,O=CashBook,L=Beijing,ST=Beijing,C=CN"
```

#### 创建keystore.properties文件
在项目根目录创建 `keystore.properties`:
```properties
storeFile=cashbook-release.jks
storePassword=你的密码
keyAlias=cashbook-key
keyPassword=你的密码
```

#### 重新构建正式版APK
```bash
./gradlew clean assembleRelease
```

### 🚨 密钥库安全警告
**密钥库一旦丢失或密码忘记，将永远无法更新应用！**

请务必：
1. ✅ 备份密钥文件到3个安全位置（本地、U盘、云盘）
2. ✅ 记录密码到密码管理器（1Password/Bitwarden等）
3. ✅ 不要将密钥文件提交到Git（已添加到.gitignore）
4. ✅ 不要与他人分享密钥和密码

---

## 📊 性能指标

- **APK大小**: ~1.9 MB（已启用ProGuard混淆和资源压缩）
- **最小内存占用**: ~50 MB
- **数据库**: 使用Room，高效的SQLite封装
- **启动时间**: < 2秒（冷启动）

---

## 🛠️ 技术栈

- **语言**: Kotlin 2.0.21
- **UI框架**: Jetpack Compose + Material Design 3
- **数据库**: Room 2.6.1
- **异步处理**: Kotlin Coroutines + Flow
- **图表**: Vico 1.13.1
- **架构**: MVVM
- **构建工具**: Gradle 8.13.1 + KSP

---

## 📝 已知问题

1. **AccessibilityNodeInfo.recycle()已弃用** - 警告信息，不影响功能
2. **Native库符号剥离失败** - 不影响功能，仅影响APK大小（~0.1MB）

---

## 🔮 未来计划（2.0版本）

- [ ] 支持更多支付App（美团、京东、拼多多）
- [ ] 云同步功能
- [ ] 账单分享和导出PDF
- [ ] 更多图表类型
- [ ] 智能分类建议
- [ ] 多账户支持

---

## 📧 反馈与支持

如有问题或建议，请通过以下方式反馈：
- GitHub Issues（如果开源）
- 邮件联系
- 应用内反馈（未来版本）

---

## 📄 许可证

[待补充]

---

**感谢使用 CashBook！祝你记账愉快！** 📒✨
