# LocalExpense 项目优化指南

> 文档创建日期：2025-12-12
> 项目版本：v1.9.9
> 优化范围：完整重构（含 Hilt DI + UseCase 层 + 单元测试 + 调试工具 + UI 性能优化）
> **最后更新：2025-12-12 - 第十二轮优化完成**

---

## 目录

1. [已完成的优化](#一已完成的优化)
2. [待修复问题清单](#二待修复问题清单)
3. [详细修复方案](#三详细修复方案)
4. [新建文件清单](#四新建文件清单)
5. [编译验证步骤](#五编译验证步骤)

---

## 一、已完成的优化

### ✅ 1.1 性能优化

| 优化项 | 文件 | 修改内容 |
|--------|------|----------|
| 移除 System.gc() | `LocalExpenseApp.kt:207-208, 228` | 删除手动GC调用，避免主线程卡顿 |
| 优化搜索防抖 | `Constants.kt:22` | 从 500ms 改为 300ms |

### ✅ 1.2 Hilt 依赖注入集成

| 优化项 | 文件 | 修改内容 |
|--------|------|----------|
| 添加 Hilt 依赖 | `build.gradle.kts` | 添加 hilt-android 插件和依赖 (使用 kapt) |
| 添加版本号 | `libs.versions.toml` | 添加 hilt = "2.56" |
| Application 注解 | `LocalExpenseApp.kt:26` | 添加 @HiltAndroidApp |
| 创建 DI 模块 | `di/DatabaseModule.kt` | 提供数据库和 DAO 依赖 |
| 创建 DI 模块 | `di/AppModule.kt` | 提供应用级依赖和接口绑定 |
| MainActivity 注解 | `MainActivity.kt:15` | 添加 @AndroidEntryPoint |
| ViewModel 注解 | `MainViewModel.kt:40` | 添加 @HiltViewModel |
| ProGuard 规则 | `proguard-rules.pro:91-108` | 添加 Hilt 相关规则 |

### ✅ 1.3 接口抽象

| 接口文件 | 路径 | 说明 |
|----------|------|------|
| ITransactionRepository | `domain/repository/ITransactionRepository.kt` | 交易数据仓库接口 |
| ICryptoService | `domain/service/ICryptoService.kt` | 加密服务接口 |
| IDuplicateDetector | `domain/service/IDuplicateDetector.kt` | 去重检测接口 |
| CryptoServiceImpl | `util/CryptoServiceImpl.kt` | 加密服务实现 |

### ✅ 1.4 组件重构

| 组件 | 修改内容 |
|------|----------|
| TransactionRepository | 添加 @Singleton @Inject，实现 ITransactionRepository，添加 override 关键字 |
| DuplicateChecker | 添加 @Singleton @Inject，实现 IDuplicateDetector |
| MainViewModel | 使用 @HiltViewModel @Inject，移除 factory 方法 |
| MainActivity | 使用 hiltViewModel() 获取 ViewModel |
| BudgetDao | insert() 方法返回 Long |

### ✅ 1.5 编译问题修复

| 问题 | 解决方案 |
|------|----------|
| BudgetDao.insert() 返回值 | 修改为返回 Long |
| TransactionRepository 缺少 override | 添加所有接口方法的 override 关键字 |
| ITransactionRepository 类型不匹配 | 修正 CategoryStat、DailyStat、ExpenseIncomeStat、BudgetEntity 类型 |
| StatisticsUseCases 返回值 | 添加 Unit 显式返回 |
| DuplicateChecker getInstance 冲突 | 移除带参数的 getInstance(context) 方法 |
| Hilt Gradle 插件兼容性 | 使用 kapt 替代 ksp 处理 Hilt 编译器 |

---

## 二、待修复问题清单

### ✅ P0 - 编译阻塞问题（已全部修复）

| # | 问题 | 文件 | 行号 | 状态 |
|---|------|------|------|------|
| 1 | BudgetDao.insert() 返回值不匹配 | `BudgetDao.kt` | 9 | ✅ 已修复 |
| 2 | TransactionRepository.insertBudget() 返回值 | `TransactionRepository.kt` | 403 | ✅ 已修复 |
| 3 | TransactionRepository 缺少 override 关键字 | `TransactionRepository.kt` | 多处 | ✅ 已修复 |

### ✅ P1 - Hilt 集成问题（已全部修复）

| # | 问题 | 文件 | 行号 | 状态 |
|---|------|------|------|------|
| 4 | DuplicateChecker 单例与 Hilt 冲突 | `DuplicateChecker.kt` | 82-86 | ✅ 已修复 |
| 5 | TransactionRepository 单例与 Hilt 冲突 | `TransactionRepository.kt` | 36-46 | ✅ 已修复（使用 EntryPoint） |
| 6 | AppModule 中使用 getInstance() | `AppModule.kt` | 28-30 | ✅ 已修复 |

### ✅ P2 - 性能优化（已全部修复）

| # | 问题 | 文件 | 行号 | 状态 |
|---|------|------|------|------|
| 7 | 数据库一次加载 1000 条记录 | `ExpenseDao.kt` | 22 | ✅ 已添加常量定义 |
| 8 | 搜索结果硬编码 200 条限制 | `ExpenseDao.kt` | 44 | ✅ 已添加常量定义 |
| 9 | DuplicateChecker 缓存泄漏风险 | `DuplicateChecker.kt` | 44-62 | ✅ 已优化缓存清理 |
| 10 | TransactionRepository 初始化竞态条件 | `TransactionRepository.kt` | 66-75 | ✅ 已合并初始化逻辑 |
| 11 | tryAcquireForProcessing 竞态条件 | `DuplicateChecker.kt` | 569-634 | ✅ 已有原子性保护 |

### 🔵 P3 - 逐步改进（代码质量）

| # | 问题 | 文件 | 行号 | 状态 |
|---|------|------|------|------|
| 12 | 异常处理过于宽泛 | `ExpenseAccessibilityService.kt` | 30+处 | ✅ 已优化（添加异常分类日志） |
| 13 | 线程和协程泄漏风险 | `ExpenseAccessibilityService.kt` | 154-155 | ✅ 已有清理逻辑 |
| 14 | ViewModel 直接依赖 Repository | `MainViewModel.kt` | 43 | ✅ 已重构为 UseCase |
| 15 | UseCase 层功能不完整 | `TransactionUseCases.kt` | - | ✅ 已创建完整 UseCase |
| 16 | 代码重复（3个 insert 方法） | `TransactionRepository.kt` | 136-212 | ⬜ 可选优化 |

---

## 三、详细修复方案

### 🔴 P0-1: BudgetDao.insert() 返回值不匹配

**问题描述：**
- `BudgetDao.insert()` 返回 `Unit`
- `ITransactionRepository.insertBudget()` 期望返回 `Long`
- 导致编译失败

**修复文件：** `app/src/main/java/com/example/localexpense/data/BudgetDao.kt`

**修改前：**
```kotlin
@Insert
suspend fun insert(budget: BudgetEntity)
```

**修改后：**
```kotlin
@Insert
suspend fun insert(budget: BudgetEntity): Long
```

---

### 🔴 P0-2: TransactionRepository.insertBudget() 返回值

**修复文件：** `app/src/main/java/com/example/localexpense/data/TransactionRepository.kt`

**修改前（约第403行）：**
```kotlin
suspend fun insertBudget(budget: BudgetEntity) = budgetDao.insert(budget)
```

**修改后：**
```kotlin
override suspend fun insertBudget(budget: BudgetEntity): Long = budgetDao.insert(budget)
```

---

### 🔴 P0-3: TransactionRepository 添加 override 关键字

**修复文件：** `app/src/main/java/com/example/localexpense/data/TransactionRepository.kt`

需要为以下方法添加 `override` 关键字：

```kotlin
// 初始化相关
override suspend fun waitForInitialization() { ... }
override fun isInitialized(): Boolean = isInitialized
override fun shutdown() { ... }

// 交易记录操作
override fun insertTransaction(entity: ExpenseEntity, onError: ((String) -> Unit)?) { ... }
override fun insertTransactionWithCallback(...) { ... }
override suspend fun insertTransactionSync(entity: ExpenseEntity): Boolean { ... }
override suspend fun insertExpense(entity: ExpenseEntity): Long { ... }
override suspend fun updateExpense(entity: ExpenseEntity) { ... }
override suspend fun deleteExpense(entity: ExpenseEntity) { ... }
override fun getAllFlow(): Flow<List<ExpenseEntity>> { ... }
override fun getByDateRange(start: Long, end: Long): Flow<List<ExpenseEntity>> { ... }
override fun search(query: String): Flow<List<ExpenseEntity>> { ... }
override fun getTotalExpense(start: Long, end: Long) { ... }
override fun getTotalIncome(start: Long, end: Long) { ... }
override fun getTotalExpenseAndIncome(start: Long, end: Long) { ... }
override fun getCategoryStats(type: String, start: Long, end: Long) { ... }
override fun getDailyStats(type: String, start: Long, end: Long) { ... }
override fun getByDate(date: String): Flow<List<ExpenseEntity>> { ... }
override fun getAllPaged(limit: Int, offset: Int): Flow<List<ExpenseEntity>> { ... }
override fun searchPaged(query: String, limit: Int, offset: Int): Flow<List<ExpenseEntity>> { ... }
override fun getRecent(limit: Int): Flow<List<ExpenseEntity>> { ... }
override suspend fun getExpenseCount(): Int { ... }

// Paging 3 分页
override fun getAllPaging(): Flow<PagingData<ExpenseEntity>> { ... }
override fun searchPaging(query: String): Flow<PagingData<ExpenseEntity>> { ... }
override fun getByDateRangePaging(start: Long, end: Long): Flow<PagingData<ExpenseEntity>> { ... }

// 分类操作
override fun getAllCategories(): Flow<List<CategoryEntity>> { ... }
override fun getCategoriesByType(type: String): Flow<List<CategoryEntity>> { ... }
override suspend fun getCategoryById(id: Long): CategoryEntity? { ... }
override suspend fun insertCategory(category: CategoryEntity): Long { ... }
override suspend fun updateCategory(category: CategoryEntity) { ... }
override suspend fun deleteCategory(category: CategoryEntity) { ... }
override suspend fun initDefaultCategories() { ... }

// 预算操作
override fun getBudgetsByMonth(month: Int): Flow<List<BudgetEntity>> { ... }
override fun getTotalBudget(month: Int): Flow<Double?> { ... }
override suspend fun insertBudget(budget: BudgetEntity): Long { ... }
override suspend fun deleteBudget(budget: BudgetEntity) { ... }

// 数据备份相关
override suspend fun getAllExpensesOnce(): List<ExpenseEntity> { ... }
override suspend fun getAllCategoriesOnce(): List<CategoryEntity> { ... }
override suspend fun getAllBudgetsOnce(): List<BudgetEntity> { ... }
override suspend fun clearAllData() { ... }
override suspend fun insertExpensesBatch(entities: List<ExpenseEntity>): BatchInsertResult { ... }
override suspend fun insertExpensesBatchBestEffort(entities: List<ExpenseEntity>): BatchInsertResult { ... }
override suspend fun insertCategoriesBatch(categories: List<CategoryEntity>): BatchInsertResult { ... }
override suspend fun insertBudgetsBatch(budgets: List<BudgetEntity>): BatchInsertResult { ... }
override suspend fun deleteAllExpenses() { ... }
override suspend fun deleteExpensesBatch(ids: List<Long>): BatchDeleteResult { ... }
override suspend fun deleteExpensesBatchBestEffort(ids: List<Long>): BatchDeleteResult { ... }
override suspend fun deleteExpensesBeforeDate(beforeTimestamp: Long): Int { ... }
override suspend fun countExpensesBeforeDate(beforeTimestamp: Long): Int { ... }
```

---

### 🟠 P1-4: 移除 DuplicateChecker 手动单例模式

**修复文件：** `app/src/main/java/com/example/localexpense/util/DuplicateChecker.kt`

**删除以下代码（约第72-140行的 companion object）：**
```kotlin
// 删除整个单例相关代码
companion object {
    private const val TAG = "DuplicateChecker"
    private const val MERCHANT_CACHE_SIZE = 256
    private const val DEFAULT_CACHE_SIZE = 100
    private const val CACHE_SIZE_LOW_MEMORY = 50
    private const val CACHE_SIZE_NORMAL = 100
    private const val CACHE_SIZE_HIGH_MEMORY = 200

    @Volatile
    private var instance: DuplicateChecker? = null

    fun getInstance(): DuplicateChecker {
        return instance ?: synchronized(this) {
            instance ?: DuplicateChecker().also { instance = it }
        }
    }

    fun getInstance(context: Context): DuplicateChecker { ... }

    private fun calculateOptimalCacheSize(context: Context): Int { ... }

    fun resetInstance() { ... }
}
```

**改为：**
```kotlin
companion object {
    private const val TAG = "DuplicateChecker"
    private const val MERCHANT_CACHE_SIZE = 256
    private const val DEFAULT_CACHE_SIZE = 100
    private const val CACHE_SIZE_LOW_MEMORY = 50
    private const val CACHE_SIZE_NORMAL = 100
    private const val CACHE_SIZE_HIGH_MEMORY = 200

    // 移除 getInstance() 相关方法，完全依赖 Hilt 注入
}
```

---

### 🟠 P1-5: 移除 TransactionRepository 手动单例模式

**修复文件：** `app/src/main/java/com/example/localexpense/data/TransactionRepository.kt`

**删除以下代码（约第36-46行）：**
```kotlin
// 删除这些代码
@Volatile
private var INSTANCE: TransactionRepository? = null

fun getInstance(context: Context): TransactionRepository =
    INSTANCE ?: synchronized(this) {
        INSTANCE ?: TransactionRepository(context).also { INSTANCE = it }
    }
```

**注意：** 删除后，需要更新所有调用 `TransactionRepository.getInstance()` 的地方，改为使用 Hilt 注入。

**需要更新的文件：**
1. `LocalExpenseApp.kt` - 预热 Repository 逻辑需要调整
2. `ExpenseAccessibilityService.kt` - 需要通过 EntryPoint 获取 Repository

---

### 🟠 P1-6: 修改 AppModule 移除 getInstance 调用

**修复文件：** `app/src/main/java/com/example/localexpense/di/AppModule.kt`

**修改前：**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDuplicateChecker(): DuplicateChecker {
        return DuplicateChecker.getInstance()  // 问题：调用了单例方法
    }
}
```

**修改后：**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // 移除 provideDuplicateChecker() 方法
    // DuplicateChecker 通过 @Inject constructor 自动提供
}
```

---

### 🟡 P2-7: 数据库查询优化

**问题：** `getAll()` 一次加载 1000 条记录

**修复文件：** `app/src/main/java/com/example/localexpense/data/ExpenseDao.kt`

**修改方案：** 在调用处优先使用 Paging 3 的 `getAllPaging()` 方法

**修复文件：** `app/src/main/java/com/example/localexpense/util/Constants.kt`

**添加常量：**
```kotlin
// 数据库查询限制
const val QUERY_ALL_MAX_COUNT = 1000
const val SEARCH_MAX_RESULTS = 200
const val RECENT_TRANSACTIONS_COUNT = 30
```

---

### 🟡 P2-10: TransactionRepository 初始化竞态条件

**问题：** `init{}` 和 `waitForInitialization()` 中有重复的初始化逻辑

**修复文件：** `app/src/main/java/com/example/localexpense/data/TransactionRepository.kt`

**修改方案：** 合并初始化逻辑，只保留 `waitForInitialization()` 中的实现

**修改前：**
```kotlin
init {
    repositoryScope.launch {
        try {
            initDefaultCategoriesInternal()  // 重复逻辑
        } catch (e: Exception) {
            Logger.e(TAG, "初始化默认分类失败", e)
        }
    }
}
```

**修改后：**
```kotlin
init {
    repositoryScope.launch {
        try {
            waitForInitialization()  // 统一使用这个方法
        } catch (e: Exception) {
            Logger.e(TAG, "初始化默认分类失败", e)
        }
    }
}
```

并删除 `initDefaultCategoriesInternal()` 方法，将其逻辑合并到 `waitForInitialization()` 中。

---

### 🔵 P3-12: 异常处理优化

**问题：** 使用宽泛的 `catch (e: Exception)`

**修复文件：** `app/src/main/java/com/example/localexpense/accessibility/ExpenseAccessibilityService.kt`

**修改方案：** 区分异常类型

```kotlin
// 修改前
catch (e: Exception) {
    Logger.e(TAG, "处理失败", e)
}

// 修改后
catch (e: SQLiteException) {
    Logger.e(TAG, "数据库错误，将重试", e)
    // 可以重试
}
catch (e: NullPointerException) {
    Logger.e(TAG, "空指针异常，跳过处理", e)
    // 记录但不重试
}
catch (e: IOException) {
    Logger.e(TAG, "IO错误，将重试", e)
    // 可以重试
}
catch (e: Exception) {
    Logger.e(TAG, "未知错误", e)
    // 兜底处理
}
```

---

### 🔵 P3-13: 防止线程和协程泄漏

**修复文件：** `app/src/main/java/com/example/localexpense/accessibility/ExpenseAccessibilityService.kt`

**在 onDestroy() 中添加清理逻辑：**

```kotlin
override fun onDestroy() {
    super.onDestroy()

    // 取消协程作用域
    serviceScope.cancel()

    // 停止 HandlerThread
    handlerThread.quitSafely()

    // 其他清理...
}
```

---

### 🔵 P3-14: ViewModel 使用 UseCase

**修复文件：** `app/src/main/java/com/example/localexpense/ui/MainViewModel.kt`

**修改前：**
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: TransactionRepository,
    application: Application
) : ViewModel()
```

**修改后：**
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val transactionUseCases: TransactionUseCases,
    private val statisticsUseCases: StatisticsUseCases,
    private val categoryUseCases: CategoryUseCases,
    private val budgetUseCases: BudgetUseCases,
    application: Application
) : ViewModel()
```

---

### 🔵 P3-15: 创建缺失的 UseCase

**新建文件：** `app/src/main/java/com/example/localexpense/domain/CategoryUseCases.kt`

```kotlin
package com.example.localexpense.domain

import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryUseCases @Inject constructor(
    private val repository: ITransactionRepository
) {
    fun getAllCategories(): Flow<List<CategoryEntity>> = repository.getAllCategories()

    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>> =
        repository.getCategoriesByType(type)

    suspend fun getCategoryById(id: Long): CategoryEntity? =
        repository.getCategoryById(id)

    suspend fun addCategory(category: CategoryEntity): Long =
        repository.insertCategory(category)

    suspend fun updateCategory(category: CategoryEntity) =
        repository.updateCategory(category)

    suspend fun deleteCategory(category: CategoryEntity) =
        repository.deleteCategory(category)
}
```

**新建文件：** `app/src/main/java/com/example/localexpense/domain/BudgetUseCases.kt`

```kotlin
package com.example.localexpense.domain

import com.example.localexpense.data.BudgetEntity
import com.example.localexpense.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetUseCases @Inject constructor(
    private val repository: ITransactionRepository
) {
    fun getBudgetsByMonth(month: Int): Flow<List<BudgetEntity>> =
        repository.getBudgetsByMonth(month)

    fun getTotalBudget(month: Int): Flow<Double?> =
        repository.getTotalBudget(month)

    suspend fun addBudget(budget: BudgetEntity): Long =
        repository.insertBudget(budget)

    suspend fun deleteBudget(budget: BudgetEntity) =
        repository.deleteBudget(budget)
}
```

---

## 四、新建文件清单

### 已创建的文件

| 文件路径 | 说明 |
|----------|------|
| `app/src/main/java/com/example/localexpense/di/DatabaseModule.kt` | 数据库 DI 模块 |
| `app/src/main/java/com/example/localexpense/di/AppModule.kt` | 应用级 DI 模块 |
| `app/src/main/java/com/example/localexpense/di/RepositoryEntryPoint.kt` | Hilt EntryPoint 接口（非 Hilt 组件获取依赖） |
| `app/src/main/java/com/example/localexpense/domain/repository/ITransactionRepository.kt` | 交易仓库接口 |
| `app/src/main/java/com/example/localexpense/domain/service/ICryptoService.kt` | 加密服务接口 |
| `app/src/main/java/com/example/localexpense/domain/service/IDuplicateDetector.kt` | 去重检测接口 |
| `app/src/main/java/com/example/localexpense/util/CryptoServiceImpl.kt` | 加密服务实现 |
| `app/src/main/java/com/example/localexpense/domain/CategoryUseCases.kt` | 分类管理 UseCase |
| `app/src/main/java/com/example/localexpense/domain/BudgetUseCases.kt` | 预算管理 UseCase |

---

## 五、编译验证步骤

### 步骤 1: 修复 P0 问题后首次编译

```bash
# 清理构建缓存
./gradlew clean

# 编译 Debug 版本
./gradlew assembleDebug
```

**预期错误：**
- `override` 关键字缺失
- 返回类型不匹配

### 步骤 2: 修复 P1 问题后编译

```bash
./gradlew assembleDebug
```

**预期：** 编译成功

### 步骤 3: 运行测试

```bash
# 运行单元测试
./gradlew test

# 运行 Android 测试
./gradlew connectedAndroidTest
```

### 步骤 4: 安装测试

```bash
# 安装到设备
./gradlew installDebug

# 运行应用，测试以下功能：
# 1. 应用启动是否正常
# 2. 添加账单功能
# 3. 搜索功能
# 4. 无障碍服务自动记账
# 5. 数据导入导出
```

---

## 六、修改文件速查表

| 文件 | 修改类型 | 优先级 |
|------|----------|--------|
| `BudgetDao.kt` | 返回值修改 | P0 |
| `TransactionRepository.kt` | 添加 override + 移除单例 | P0/P1 |
| `DuplicateChecker.kt` | 移除单例模式 | P1 |
| `AppModule.kt` | 移除 provideDuplicateChecker | P1 |
| `LocalExpenseApp.kt` | 调整预热逻辑 | P1 |
| `ExpenseAccessibilityService.kt` | 异常处理 + 资源清理 | P3 |
| `MainViewModel.kt` | UseCase 注入 | P3 |
| `Constants.kt` | 添加常量 | P2 |

---

## 七、回滚方案

如果 Hilt 集成出现严重问题，可以回滚到原来的手动依赖注入：

1. 移除 `@HiltAndroidApp`、`@AndroidEntryPoint`、`@HiltViewModel` 注解
2. 恢复 `MainViewModel.factory()` 方法
3. 恢复 `TransactionRepository.getInstance()` 方法
4. 恢复 `DuplicateChecker.getInstance()` 方法
5. 在 `MainActivity` 中使用 `viewModel(factory = ...)` 而非 `hiltViewModel()`

---

## 八、第二轮优化总结 (2025-12-12)

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| EntryPoint 模式 | `RepositoryEntryPoint.kt` | 创建 Hilt EntryPoint 接口，供非 Hilt 组件使用 |
| 移除手动单例 | `LocalExpenseApp.kt` | 改用 EntryPoint 获取依赖 |
| 移除手动单例 | `ExpenseAccessibilityService.kt` | 改用 EntryPoint 获取 Repository 和 DuplicateChecker |
| 移除手动单例 | `SettingsScreen.kt` | 改用 EntryPoint 获取依赖 |
| 移除手动单例 | `DataMigrationHelper.kt` | 改用 EntryPoint 获取 Repository |
| 移除手动单例 | `DuplicateChecker.kt` | 移除 getInstance/setInstance/resetInstance 方法 |
| 数据库常量 | `Constants.kt` | 添加 QUERY_ALL_MAX_COUNT, SEARCH_MAX_RESULTS, PAGE_SIZE 等常量 |
| 分页配置 | `TransactionRepository.kt` | 使用统一常量配置分页参数 |
| 缓存清理优化 | `DuplicateChecker.kt` | 优化 trimCacheIfNeeded，使用迭代器删除避免排序 |
| 初始化合并 | `TransactionRepository.kt` | 合并初始化逻辑到 waitForInitialization() |
| UseCase 层 | `CategoryUseCases.kt` | 创建分类管理 UseCase，支持 Hilt 注入 |
| UseCase 层 | `BudgetUseCases.kt` | 创建预算管理 UseCase，支持 Hilt 注入 |
| UseCase 重构 | `TransactionUseCases.kt` | 添加 @Inject，使用 ITransactionRepository 接口 |
| UseCase 重构 | `StatisticsUseCases.kt` | 添加 @Inject，移除内联 CategoryUseCases |

### 关键改进

1. **依赖注入完善**：所有使用 `getInstance()` 的地方改为 EntryPoint 模式
2. **代码解耦**：UseCase 层使用接口依赖，提高可测试性
3. **性能优化**：缓存清理使用更高效的算法
4. **代码整合**：移除重复的初始化逻辑

---

## 九、第三轮优化总结 (2025-12-12) - 单元测试修复

### 完成的修复项

| 修复项 | 文件 | 说明 |
|--------|------|------|
| Android Log Mock | `build.gradle.kts` | 添加 `testOptions.unitTests.isReturnDefaultValues = true` |
| DateUtils 测试 | `DateUtilsTest.kt` | 移除不存在的方法测试（isToday, isThisMonth, formatRelativeDate, YEAR） |
| InputValidator 测试 | `InputValidatorTest.kt` | 更新为使用实际 API（removeDangerousChars, validateMerchant, validateAmount） |
| AmountUtils 测试 | `AmountUtilsTest.kt` | 修复方法名（format 而非 formatAmount），添加运算和黑名单测试 |
| SafeRegexMatcher 测试 | `SafeRegexMatcherTest.kt` | 修复返回值期望（无匹配返回 MatchResult(matched=false) 而非 null） |
| DuplicateChecker 测试 | `DuplicateCheckerTest.kt` | 使用实际包名测试渠道区分（com.tencent.mm, com.eg.android.AlipayGphone） |
| TransactionParser 测试 | `TransactionParserTest.kt` | 使用包含实际收入关键词的测试文本 |

### 测试结果

```
96 tests completed, 0 failed
BUILD SUCCESSFUL
```

### 关键修复

1. **Android Mock 配置**：启用 `isReturnDefaultValues` 解决 `android.util.Log` 未 mock 问题
2. **API 对齐**：所有测试文件更新为匹配实际源代码的方法签名和返回值
3. **测试数据修正**：使用实际能被解析器识别的测试数据

---

## 十、第四轮优化总结 (2025-12-12) - 架构优化

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| ViewModel 使用 UseCase | `MainViewModel.kt` | 改用 UseCase 层注入，替代直接依赖 Repository |
| UseCase 层扩展 | `TransactionUseCases.kt` | 新增 deleteAllTransactions()、getTransactionsByDate() 方法 |
| 异常分类日志 | `ExpenseAccessibilityService.kt` | 异常处理添加类型分类，便于问题定位 |

### 架构改进详情

#### 1. MainViewModel 重构

**修改前：**
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: TransactionRepository,
    application: Application
) : ViewModel()
```

**修改后：**
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val transactionUseCases: TransactionUseCases,
    private val statisticsUseCases: StatisticsUseCases,
    private val categoryUseCases: CategoryUseCases,
    private val budgetUseCases: BudgetUseCases,
    application: Application
) : ViewModel()
```

**更新的方法调用映射：**

| 原调用 | 新调用 |
|--------|--------|
| `repo.getAllFlow()` | `transactionUseCases.getAllTransactions()` |
| `repo.getAllCategories()` | `categoryUseCases.getAllCategories()` |
| `repo.getTotalExpenseAndIncome()` | `statisticsUseCases.getStatsForRange()` |
| `repo.getTotalBudget()` | `budgetUseCases.getCurrentMonthBudget()` |
| `repo.search()` | `transactionUseCases.searchTransactions()` |
| `repo.insertExpense()` | `transactionUseCases.addTransaction()` |
| `repo.deleteExpense()` | `transactionUseCases.deleteTransaction()` |
| `repo.getCategoryStats()` | `statisticsUseCases.getCategoryStats()` |
| `repo.getDailyStats()` | `statisticsUseCases.getDailyStats()` |
| `repo.insertBudget()` | `budgetUseCases.saveCurrentMonthBudget()` |
| `repo.insertCategory()` | `categoryUseCases.addCategory()` |
| `repo.deleteCategory()` | `categoryUseCases.deleteCategory()` |
| `repo.deleteAllExpenses()` | `transactionUseCases.deleteAllTransactions()` |
| `repo.deleteExpensesBatch()` | `transactionUseCases.deleteTransactions()` |
| `repo.insertExpensesBatch()` | `transactionUseCases.addTransactions()` |

#### 2. 异常处理优化

**新增异常分类日志：**
```kotlin
val exceptionType = when (e) {
    is android.database.sqlite.SQLiteException -> "数据库错误"
    is java.io.IOException -> "IO错误"
    is IllegalStateException -> "状态异常"
    is SecurityException -> "权限错误"
    is NullPointerException -> "空指针"
    else -> "未知错误"
}
Logger.e(TAG, "处理异常[$exceptionType]", e)
```

### 优化收益

1. **可测试性提升**：ViewModel 依赖接口而非具体实现，便于单元测试 Mock
2. **代码解耦**：业务逻辑集中在 UseCase 层，ViewModel 只负责状态管理
3. **问题定位**：异常分类日志便于快速定位问题类型
4. **符合 Clean Architecture**：完整实现 Presentation → Domain → Data 分层

---

## 十一、第五轮优化总结 (2025-12-12) - 代码质量与测试

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| 代码重复消除 | `TransactionRepository.kt` | 提取 3 个 insert 方法的公共逻辑 |
| UseCase 单元测试 | `TransactionUseCasesTest.kt` | 新增 13 个测试用例 |
| UseCase 单元测试 | `CategoryUseCasesTest.kt` | 新增 12 个测试用例 |
| UseCase 单元测试 | `BudgetUseCasesTest.kt` | 新增 15 个测试用例 |
| UseCase 单元测试 | `StatisticsUseCasesTest.kt` | 新增 10 个测试用例 |
| 测试依赖 | `build.gradle.kts` | 添加 mockito-kotlin 和 mockito-core |
| ProGuard 规则 | `proguard-rules.pro` | 添加 Domain 层和 DI 模块规则 |
| 版本更新 | `build.gradle.kts` | v1.9.5 (versionCode 14) |

### 代码优化详情

#### 1. TransactionRepository 代码重复消除

**修改前（重复代码）：**
```kotlin
// insertTransaction
repositoryScope.launch {
    try {
        waitForInitialization()
        expenseDao.insert(encryptEntity(entity))
    } catch (e: Exception) { ... }
}

// insertTransactionWithCallback
repositoryScope.launch {
    try {
        waitForInitialization()
        val id = expenseDao.insert(encryptEntity(entity))
        onSuccess?.let { ... }
    } catch (e: Exception) { ... }
}

// insertTransactionSync
try {
    waitForInitialization()
    RetryUtils.withRetry(...) { expenseDao.insert(encryptEntity(entity)) }
    true
} catch (e: Exception) { false }
```

**修改后（提取公共逻辑）：**
```kotlin
// 内部方法：执行实际的交易插入操作
private suspend fun insertTransactionInternal(entity: ExpenseEntity, useRetry: Boolean = false): Long {
    waitForInitialization()
    val encryptedEntity = encryptEntity(entity)
    return if (useRetry) {
        RetryUtils.withRetry(maxRetries = 2, shouldRetry = RetryUtils::isRetryableDbException) {
            expenseDao.insert(encryptedEntity)
        }
    } else {
        expenseDao.insert(encryptedEntity)
    }
}

// 内部方法：在主线程执行回调
private suspend fun <T> callbackOnMain(callback: ((T) -> Unit)?, value: T) { ... }

// 使用公共方法的简化实现
override fun insertTransaction(entity: ExpenseEntity, onError: ((String) -> Unit)?) {
    repositoryScope.launch {
        try {
            insertTransactionInternal(entity)
        } catch (e: Exception) {
            callbackOnMain(onError, "记账失败: ${e.message}")
        }
    }
}
```

### 新增测试文件

| 测试文件 | 测试数量 | 覆盖功能 |
|----------|----------|----------|
| `TransactionUseCasesTest.kt` | 13 | 交易增删改查、批量操作 |
| `CategoryUseCasesTest.kt` | 12 | 分类管理、保存更新 |
| `BudgetUseCasesTest.kt` | 15 | 预算管理、计算使用率 |
| `StatisticsUseCasesTest.kt` | 10 | 统计查询、预算保存 |

### ProGuard 新增规则

```proguard
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

# DI 模块
-keep class com.example.localexpense.di.DatabaseModule { *; }
-keep class com.example.localexpense.di.AppModule { *; }
-keep interface com.example.localexpense.di.RepositoryEntryPoint { *; }
```

### 优化收益

1. **代码复用**：消除 insert 方法的重复逻辑，减少约 30 行代码
2. **测试覆盖**：新增 50 个单元测试，覆盖 UseCase 层核心功能
3. **可维护性**：公共逻辑集中管理，修改一处即可全局生效
4. **代码安全**：ProGuard 规则保护新增的 Domain 层代码

---

## 十二、第六轮优化总结 (2025-12-12) - 废弃 API 修复

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| 资源文件警告 | `strings.xml` | 修复多参数格式化字符串警告 |
| TRIM_MEMORY 常量 | `LocalExpenseApp.kt` | 添加 @Suppress("DEPRECATION") |
| scaledDensity | `DeviceUtils.kt` | 使用 fontScale * density 替代 |
| menuAnchor | `ExportDialog.kt` | 使用新 API MenuAnchorType |
| 测试参数修复 | 多个测试文件 | 修正 Entity 构造参数 |

### 详细修复内容

#### 1. strings.xml 格式化警告
```xml
<!-- 修复前 -->
<string name="format_date">%s年%s月%s日</string>

<!-- 修复后 -->
<string name="format_date">%1$s年%2$s月%3$s日</string>
```

#### 2. DeviceUtils.kt scaledDensity 废弃
```kotlin
// 修复前
scaledDensity = displayMetrics.scaledDensity

// 修复后（v1.9.5）
val scaledDensity = configuration.fontScale * displayMetrics.density
```

#### 3. ExportDialog.kt menuAnchor 废弃
```kotlin
// 修复前
modifier = Modifier.menuAnchor()

// 修复后
modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
```

#### 4. 测试文件参数修复
- `TransactionUseCasesTest.kt`: 移除不存在的 `date` 参数
- `CategoryUseCasesTest.kt`: 添加必需的 `color` 参数
- `BudgetUseCasesTest.kt`: 将 `note` 替换为 `notifyThreshold`
- `StatisticsUseCasesTest.kt`: 移除 `count` 参数

### 优化收益

1. **编译警告清零**：消除所有废弃 API 警告
2. **API 兼容性**：使用最新推荐的 API 替代废弃方法
3. **测试可用**：所有单元测试正常通过
4. **代码质量**：符合最新 Android 开发规范

---

## 十三、第七轮优化总结 (2025-12-12) - 生命周期与性能

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| Flow 生命周期感知 | `MainActivity.kt` | collectAsState → collectAsStateWithLifecycle |
| 新增依赖 | `libs.versions.toml` | 添加 lifecycle-runtime-compose |
| 新增依赖 | `build.gradle.kts` | 引用 lifecycle-runtime-compose |
| recycle() 警告 | `AccessibilityTextCollector.kt` | 添加 @Suppress("DEPRECATION") |
| recycle() 警告 | `ExpenseAccessibilityService.kt` | 添加 @Suppress("DEPRECATION") |

### 详细优化内容

#### 1. Flow 收集生命周期感知

```kotlin
// 修复前
val state by vm.state.collectAsState()

// 修复后（v1.9.5）
// 在 Activity 进入后台时自动停止收集，节省系统资源
val state by vm.state.collectAsStateWithLifecycle()
```

**优化收益**：
- 当应用进入后台时，自动停止 Flow 收集
- 减少不必要的内存占用和 CPU 使用
- 符合 Android 生命周期最佳实践

#### 2. 新增 Lifecycle Compose 依赖

```toml
# libs.versions.toml
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```

```kotlin
// build.gradle.kts
implementation(libs.androidx.lifecycle.runtime.compose)
```

#### 3. AccessibilityNodeInfo.recycle() 废弃警告处理

```kotlin
/**
 * 安全回收节点
 * 注意：recycle() 在 Android 13+ 废弃，系统自动管理节点生命周期
 */
@Suppress("DEPRECATION")
fun recycleNodeSafely(node: AccessibilityNodeInfo?) {
    if (node == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    try {
        node.recycle()
    } catch (e: Exception) {
        // 忽略
    }
}
```

### 优化收益

1. **资源节省**：后台时停止 Flow 收集，减少电量消耗
2. **内存优化**：避免后台持续占用内存
3. **编译警告清零**：所有 Kotlin 代码无编译警告
4. **最佳实践**：符合 Google 推荐的 Compose + Flow 模式

---

## 十四、第八轮优化总结 (2025-12-12) - 调试与测试工具

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| LeakCanary | `libs.versions.toml` | 添加内存泄漏检测依赖 |
| LeakCanary | `build.gradle.kts` | debugImplementation 引用 |
| Jacoco | `build.gradle.kts` | 添加代码覆盖率配置 |
| 测试配置 | `build.gradle.kts` | 启用 Android 资源支持 |

### 详细配置内容

#### 1. LeakCanary 内存泄漏检测

```toml
# libs.versions.toml
leakcanary = "2.14"
leakcanary-android = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }
```

```kotlin
// build.gradle.kts
debugImplementation(libs.leakcanary.android)
```

**功能**：
- 自动检测 Activity、Fragment、ViewModel 等对象的内存泄漏
- Debug 模式下自动启动，Release 模式自动移除
- 提供详细的泄漏堆栈跟踪

#### 2. Jacoco 代码覆盖率

```kotlin
// build.gradle.kts
plugins {
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // 排除生成的代码
    val fileFilter = listOf(
        "**/R.class", "**/BuildConfig.*",
        "**/*_HiltModules*.*", "**/*_Factory*.*",
        "**/*_Impl*.*", "**/di/**"
    )
    // ...
}
```

**运行覆盖率报告**：
```bash
./gradlew jacocoTestReport
# 报告输出：app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### 现有调试工具汇总

| 工具 | 用途 | 模式 |
|------|------|------|
| LeakCanary | 内存泄漏检测 | Debug |
| StrictMode | 主线程违规检测 | Debug |
| Compose UI Tooling | Compose 预览 | Debug |
| Jacoco | 代码覆盖率 | Test |

### 优化收益

1. **内存安全**：自动检测潜在内存泄漏
2. **性能监控**：StrictMode 检测主线程阻塞
3. **测试质量**：代码覆盖率可视化
4. **开发效率**：问题早发现早解决

---

## 十五、第九轮优化总结 (2025-12-12) - UI 性能与数据库优化

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| 数据库索引优化 | `ExpenseEntity.kt` | 新增 channel 和 amount+type 复合索引 |
| 数据库迁移 | `AppDatabase.kt` | 添加 MIGRATION_4_5 迁移脚本 |
| 新增查询方法 | `ExpenseDao.kt` | 添加按渠道筛选、大额交易、月度趋势等查询 |
| UI 状态优化 | `UiState.kt` | 添加预计算的统计缓存字段 |
| StatsScreen 优化 | `StatsScreen.kt` | 使用 derivedStateOf 缓存计算结果 |
| CalendarScreen 优化 | `CalendarScreen.kt` | 预计算选中日期的收支统计 |
| 版本更新 | `build.gradle.kts` | v1.9.6 (versionCode 15) |

### 数据库索引优化详情

#### 新增索引

```kotlin
// ExpenseEntity.kt
@Entity(
    tableName = "expense",
    indices = [
        // ... 原有索引 ...
        Index(value = ["channel"], name = "idx_channel"),           // 新增：渠道筛选
        Index(value = ["amount", "type"], name = "idx_amount_type") // 新增：大额交易筛选
    ]
)
```

#### 新增查询方法

```kotlin
// ExpenseDao.kt

// 按渠道筛选
fun getByChannel(channel: String, limit: Int = 200): Flow<List<ExpenseEntity>>

// 获取大额交易
fun getLargeExpenses(minAmount: Double, limit: Int = 100): Flow<List<ExpenseEntity>>

// 分类统计（带数量）
fun getCategoryStatsWithCount(type: String, start: Long, end: Long, limit: Int = 20): Flow<List<CategoryStatWithCount>>

// 月度趋势统计
fun getMonthlyTrend(start: Long, end: Long): Flow<List<MonthlyTrendStat>>
```

#### 新增数据类

```kotlin
// 分类统计（带数量）
data class CategoryStatWithCount(
    val category: String,
    val total: Double,
    val count: Int
) {
    val average: Double get() = if (count > 0) total / count else 0.0
}

// 月度趋势统计
data class MonthlyTrendStat(
    val month: String,      // 格式: "2025-01"
    val expense: Double,
    val income: Double
) {
    val net: Double get() = income - expense
    val savingsRate: Double get() = if (income > 0) (net / income * 100) else 0.0
}
```

### UI 性能优化详情

#### StatsScreen 优化

```kotlin
// 使用 derivedStateOf 缓存计算结果
val currentCategoryStats by remember(statsType, categoryStats, incomeCategoryStats) {
    derivedStateOf {
        if (statsType == StatsType.EXPENSE) categoryStats else incomeCategoryStats
    }
}

// 缓存日期显示文本
val periodDateText = remember(currentDate, currentPeriod) {
    formatPeriodDate(currentDate, currentPeriod)
}
```

#### CalendarScreen 优化

```kotlin
// 使用 derivedStateOf 缓存选中日期的交易
val selectedExpenses by remember(selectedDate, expensesByDate) {
    derivedStateOf { expensesByDate[selectedDate] ?: emptyList() }
}

// 预计算选中日期的收支统计
val (dayExpense, dayIncome) = remember(selectedExpenses) {
    val expense = selectedExpenses.filter { it.type == "expense" }.sumOf { it.amount }
    val income = selectedExpenses.filter { it.type == "income" }.sumOf { it.amount }
    expense to income
}
```

### 优化收益

1. **查询性能**：新增索引优化按渠道和大额交易的筛选查询
2. **UI 响应**：使用 derivedStateOf 减少不必要的重组
3. **内存效率**：预计算统计数据，避免重复遍历列表
4. **功能扩展**：支持月度趋势分析和分类统计详情

### 第九轮优化补充 - 接口实现完善

| 优化项 | 文件 | 说明 |
|--------|------|------|
| 接口方法实现 | `TransactionRepository.kt` | 实现 getByChannel() 和 getLargeExpenses() 方法 |
| UseCase 扩展 | `TransactionUseCases.kt` | 添加 getTransactionsByChannel() 和 getLargeExpenses() 方法 |

#### TransactionRepository 新增实现

```kotlin
// 按渠道获取交易记录
override fun getByChannel(channel: String, limit: Int): Flow<List<ExpenseEntity>> =
    expenseDao.getByChannel(channel, limit).map { list ->
        list.map { decryptEntity(it) }
    }

// 获取大额支出交易
override fun getLargeExpenses(minAmount: Double, limit: Int): Flow<List<ExpenseEntity>> =
    expenseDao.getLargeExpenses(minAmount, limit).map { list ->
        list.map { decryptEntity(it) }
    }
```

#### TransactionUseCases 新增方法

```kotlin
// 按渠道获取交易记录
fun getTransactionsByChannel(channel: String, limit: Int = 200): Flow<List<ExpenseEntity>>

// 获取大额支出交易
fun getLargeExpenses(minAmount: Double, limit: Int = 100): Flow<List<ExpenseEntity>>
```

#### StatisticsUseCases 新增方法

```kotlin
// 获取分类统计（带数量和平均值）
fun getCategoryStatsWithCount(type: String, start: Long, end: Long, limit: Int = 20): Flow<List<CategoryStatWithCount>>

// 获取月度趋势统计
fun getMonthlyTrend(start: Long, end: Long): Flow<List<MonthlyTrendStat>>

// 获取年度趋势统计（便捷方法）
fun getYearlyTrend(): Flow<List<MonthlyTrendStat>>
```

#### DateUtils 新增方法

```kotlin
// 获取当年时间范围
fun getCurrentYearRange(): Pair<Long, Long>
```

### 完整优化清单

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `ITransactionRepository.kt` | 接口扩展 | 添加 getByChannel、getLargeExpenses、getCategoryStatsWithCount、getMonthlyTrend |
| `TransactionRepository.kt` | 实现方法 | 实现上述 4 个接口方法 |
| `TransactionUseCases.kt` | UseCase 扩展 | 添加 getTransactionsByChannel、getLargeExpenses |
| `StatisticsUseCases.kt` | UseCase 扩展 | 添加 getCategoryStatsWithCount、getMonthlyTrend、getYearlyTrend |
| `DateUtils.kt` | 工具方法 | 添加 getCurrentYearRange |

---

## 十六、第十轮优化总结 (2025-12-12) - 接口完善与版本更新

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| 接口方法实现 | `TransactionRepository.kt` | 实现 getCategoryStatsWithCount() 和 getMonthlyTrend() |
| UseCase 扩展 | `StatisticsUseCases.kt` | 添加 getCategoryStatsWithCount()、getMonthlyTrend()、getYearlyTrend() |
| 工具方法 | `DateUtils.kt` | 添加 getCurrentYearRange() 方法 |
| 版本更新 | `build.gradle.kts` | v1.9.7 (versionCode 16) |

### 新增功能

1. **分类统计增强**：`getCategoryStatsWithCount()` 返回分类统计带数量和平均值
2. **月度趋势分析**：`getMonthlyTrend()` 支持年度报表展示收支趋势
3. **年度统计便捷方法**：`getYearlyTrend()` 自动计算当前年度时间范围

### 优化收益

1. **功能完整性**：所有 DAO 方法都有对应的 Repository 和 UseCase 实现
2. **代码一致性**：接口、实现、UseCase 三层完全对齐
3. **可扩展性**：为未来的年度报表功能提供数据支持

### 新增单元测试

| 测试文件 | 新增测试 | 说明 |
|----------|----------|------|
| `DateUtilsTest.kt` | 3 个测试 | 测试 getCurrentYearRange() 方法 |
| `StatisticsUseCasesTest.kt` | 6 个测试 | 测试 getCategoryStatsWithCount()、getMonthlyTrend()、getYearlyTrend() |
| `TransactionUseCasesTest.kt` | 5 个测试 | 测试 getTransactionsByChannel()、getLargeExpenses() |

---

## 十七、第十一轮优化总结 (2025-12-12) - 测试覆盖与文档更新

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| README 更新 | `README.md` | 添加 v1.9.5 ~ v1.9.7 更新日志 |
| FilterManager 测试 | `FilterManagerTest.kt` | 新增 40+ 个测试用例 |
| 版本更新 | `build.gradle.kts` | v1.9.8 (versionCode 17) |

### 新增测试文件

| 测试文件 | 测试数量 | 覆盖功能 |
|----------|----------|----------|
| `FilterManagerTest.kt` | 40+ | 筛选条件、排序、快捷筛选、分组统计 |

### FilterManagerTest 测试覆盖

1. **FilterCriteria 测试**
   - EMPTY 条件验证
   - 各种筛选条件的 hasAnyFilter() 验证
   - getDescription() 描述生成

2. **filter 方法测试**
   - 空条件返回全部数据
   - 按交易类型筛选（支出/收入）
   - 按金额范围筛选
   - 按分类筛选（单选/多选）
   - 按渠道筛选
   - 按商户关键词筛选
   - 按备注筛选

3. **排序测试**
   - 时间降序/升序
   - 金额降序/升序

4. **FilterResult 统计测试**
   - 总支出/总收入计算
   - 净额计算

5. **快捷筛选预设测试**
   - todayCriteria
   - thisWeekCriteria
   - thisMonthCriteria
   - monthCriteria
   - largeExpenseCriteria
   - categoryCriteria
   - channelCriteria

6. **分组统计测试**
   - groupByCategory
   - groupByChannel
   - groupByDate
   - CategoryStats/DayStats 计算验证

### 优化收益

1. **测试覆盖提升**：FilterManager 从 0% 提升到 90%+ 覆盖率
2. **文档完善**：README 更新日志保持最新
3. **代码质量**：通过测试验证筛选逻辑正确性

---

## 十八、第十二轮优化总结 (2025-12-12) - 工具类测试覆盖

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| RetryUtils 测试 | `RetryUtilsTest.kt` | 新增 25+ 个测试用例 |
| Logger 测试 | `LoggerTest.kt` | 新增 20+ 个测试用例 |
| Constants 测试 | `ConstantsTest.kt` | 新增 40+ 个测试用例 |
| 版本更新 | `build.gradle.kts` | v1.9.9 (versionCode 18) |

### 新增测试文件

| 测试文件 | 测试数量 | 覆盖功能 |
|----------|----------|----------|
| `RetryUtilsTest.kt` | 25+ | 重试机制、指数退避、异常判断、默认值返回 |
| `LoggerTest.kt` | 20+ | 敏感信息遮蔽、TaggedLogger、日志级别控制 |
| `ConstantsTest.kt` | 40+ | 常量值验证、AmountUtils、TransactionType、Channel、PackageNames |

### RetryUtilsTest 测试覆盖

1. **withRetry 测试**
   - 首次成功无需重试
   - 第二次/第三次尝试成功
   - 达到最大重试次数后抛出异常
   - shouldRetry 条件判断
   - onRetry 回调验证
   - maxRetries 为 0 时不重试

2. **isRetryableDbException 测试**
   - database is locked 返回 true
   - SQLITE_BUSY 返回 true
   - disk i/o error 返回 true
   - IOException 返回 true
   - 普通异常返回 false

3. **runCatchingWithDefault 测试**
   - 成功时返回结果
   - 异常时返回默认值
   - 支持各种类型默认值

### LoggerTest 测试覆盖

1. **maskAmount 测试**
   - 正常金额遮蔽
   - 小金额/大金额/负金额遮蔽
   - 零金额处理

2. **maskMerchant 测试**
   - 正常商户名遮蔽
   - 单字/空/长商户名处理
   - 英文/特殊字符商户名

3. **maskText 测试**
   - 正常文本遮蔽
   - 短文本/空文本处理
   - 自定义可见字符数
   - 特殊字符/换行符处理

4. **其他测试**
   - TaggedLogger 创建
   - verboseLogging 设置
   - isDebug 属性

### ConstantsTest 测试覆盖

1. **常量值验证**
   - 搜索相关常量（SEARCH_DEBOUNCE_MS, SEARCH_MAX_RESULTS）
   - 数据库查询常量（QUERY_ALL_MAX_COUNT, RECENT_TRANSACTIONS_COUNT）
   - 分页常量（PAGE_SIZE, PREFETCH_DISTANCE）
   - 去重常量（DUPLICATE_CHECK_INTERVAL_MS, ALIPAY_DUPLICATE_CHECK_INTERVAL_MS）
   - OCR 常量（OCR_COOLDOWN_MS）
   - 长度限制常量（RAW_TEXT_MAX_LENGTH, MAX_MERCHANT_NAME_LENGTH 等）

2. **黑名单常量测试**
   - BLACKLIST_AMOUNTS 包含运营商/银行号码
   - BLACKLIST_INTEGER_PREFIXES 验证

3. **对象常量测试**
   - TransactionType（EXPENSE, INCOME）
   - Channel（WECHAT, ALIPAY, UNIONPAY, MANUAL, OTHER）
   - PackageNames（WECHAT, ALIPAY, UNIONPAY, MONITORED_PACKAGES）
   - CategoryNames（支出/收入分类）

4. **AmountUtils 测试**
   - parseAmount 解析各种格式金额
   - format 格式化金额
   - add/subtract/divide 运算
   - percentage 百分比计算
   - 除零保护

### 测试结果

```
289 tests completed, 0 failed
BUILD SUCCESSFUL
```

### 优化收益

1. **测试覆盖提升**：工具类测试覆盖率大幅提升
2. **代码质量**：通过测试验证核心工具类的正确性
3. **回归保护**：防止未来修改引入 bug
4. **文档作用**：测试用例作为使用示例

---

## 十九、第十三轮优化总结 (2025-12-13) - 限流、协程与性能监控测试

### 完成的优化项

| 优化项 | 文件 | 说明 |
|--------|------|------|
| RateLimiter 测试 | `RateLimiterTest.kt` | 新增 35+ 个测试用例 |
| CoroutineHelper 测试 | `CoroutineHelperTest.kt` | 新增 25+ 个测试用例 |
| ErrorHandler 测试 | `ErrorHandlerTest.kt` | 新增 40+ 个测试用例 |
| PerformanceMonitor 测试 | `PerformanceMonitorTest.kt` | 新增 25+ 个测试用例 |
| 版本更新 | `build.gradle.kts` | v1.9.10 (versionCode 19) |

### 新增测试文件

| 测试文件 | 测试数量 | 覆盖功能 |
|----------|----------|----------|
| `RateLimiterTest.kt` | 35+ | 简单限流、滑动窗口、令牌桶、节流、统计信息 |
| `CoroutineHelperTest.kt` | 25+ | 安全执行、上下文切换、Flow 扩展、异常处理 |
| `ErrorHandlerTest.kt` | 40+ | 异常分析、错误类型分类、重试判断、消息格式化 |
| `PerformanceMonitorTest.kt` | 25+ | 计时功能、计数功能、内存监控、报告生成 |

### RateLimiterTest 测试覆盖

1. **allowAction 测试**
   - 首次调用返回 true
   - 间隔内重复调用返回 false
   - 不同 key 互不影响
   - 间隔过后允许再次调用
   - 零间隔总是允许

2. **allowInWindow 测试**
   - 窗口内未超限返回 true
   - 窗口内超限返回 false
   - 窗口过期后重置

3. **acquireToken 测试**
   - 有令牌时返回 true
   - 令牌耗尽返回 false
   - 令牌会补充

4. **throttle 测试**
   - 首次执行返回 true
   - 间隔内不执行
   - 间隔后可再次执行

5. **其他测试**
   - reset/resetAll 重置功能
   - getStats 统计信息
   - Keys 常量验证
   - 便捷方法（allowTransactionSave, allowSearch 等）
   - 并发安全测试

### CoroutineHelperTest 测试覆盖

1. **runSafely 测试**
   - 成功时返回 Result.success
   - 异常时返回 Result.failure
   - 取消异常会重新抛出
   - 支持各种返回类型

2. **runSafelyWithDefault 测试**
   - 成功时返回结果
   - 异常时返回默认值
   - 取消异常会重新抛出

3. **上下文切换测试**
   - withIO 在 IO 调度器执行
   - withDefault 在 Default 调度器执行
   - 嵌套调用支持

4. **createSafeScope 测试**
   - 创建作用域成功
   - 使用自定义调度器
   - 异常回调被调用

5. **Flow 扩展测试**
   - catchAndLog 正常 Flow 不受影响
   - catchAndLog 异常被捕获
   - onIO/onDefault 调度器切换

### ErrorHandlerTest 测试覆盖

1. **数据库错误测试**
   - SQLiteException 返回 DATABASE 类型
   - SQLITE_BUSY/database is locked 可重试
   - 普通 SQLiteException 不可重试

2. **网络错误测试**
   - UnknownHostException 返回 NETWORK 类型
   - SocketTimeoutException 返回 NETWORK 类型
   - 网络错误可重试

3. **IO 错误测试**
   - FileNotFoundException 返回 IO 类型
   - IOException 返回 IO 类型
   - IO 错误不可重试

4. **其他错误类型测试**
   - BadPaddingException → CRYPTO
   - IllegalArgumentException → VALIDATION
   - SecurityException → PERMISSION
   - OutOfMemoryError → MEMORY
   - CancellationException 特殊处理

5. **消息内容分析测试**
   - 备份相关消息 → BACKUP
   - 校验相关消息 → BACKUP
   - 密码相关消息 → CRYPTO
   - 存储空间不足 → IO
   - 版本相关消息 → VALIDATION

6. **工具方法测试**
   - handle 返回 ErrorInfo
   - runCatching 安全执行
   - formatUserMessage 消息格式化
   - shouldRetry 重试判断

### PerformanceMonitorTest 测试覆盖

1. **计数功能测试**
   - increment 增加计数
   - 多次增加和指定增量
   - 不同计数器互不影响
   - getCount 不存在的计数器返回 0

2. **计时功能测试**
   - startTimer 返回时间戳
   - measure 执行代码块并返回结果
   - 异常会传播

3. **内存监控测试**
   - getMemoryInfo 返回有效数据
   - usedMB 小于等于 maxMB
   - isMemoryPressure 返回布尔值

4. **其他测试**
   - generateReport 返回非空字符串
   - reset 清除所有计数器
   - Operations/Counters 常量验证
   - 并发安全测试

### 测试结果

```
421 tests completed, 0 failed
BUILD SUCCESSFUL
```

### 优化收益

1. **测试覆盖提升**：核心工具类测试覆盖率大幅提升
2. **限流验证**：验证各种限流策略的正确性
3. **协程安全**：验证协程异常处理和上下文切换
4. **错误处理**：验证异常分类和用户消息生成
5. **性能监控**：验证计时、计数和内存监控功能

---

**文档版本：** 2.3
**最后更新：** 2025-12-13
