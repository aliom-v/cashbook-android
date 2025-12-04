package com.example.localexpense.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TransactionRepository private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val expenseDao = db.expenseDao()
    private val categoryDao = db.categoryDao()
    private val budgetDao = db.budgetDao()

    // 使用单一的协程作用域，避免每次创建新的
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 关闭资源（App退出时调用）
    fun shutdown() {
        repositoryScope.cancel()
    }

    // Expense operations - 异步插入，使用协程
    fun insertTransaction(entity: ExpenseEntity) {
        repositoryScope.launch {
            try {
                expenseDao.insert(entity)
            } catch (e: Exception) {
                android.util.Log.e("TransactionRepo", "插入失败: ${e.message}", e)
            }
        }
    }

    suspend fun insertExpense(entity: ExpenseEntity): Long = expenseDao.insert(entity)

    suspend fun updateExpense(entity: ExpenseEntity) = expenseDao.update(entity)

    suspend fun deleteExpense(entity: ExpenseEntity) = expenseDao.delete(entity)

    fun getAllFlow() = expenseDao.getAll()

    fun getByDateRange(start: Long, end: Long) = expenseDao.getByDateRange(start, end)

    fun search(query: String) = expenseDao.search(query)

    fun getTotalExpense(start: Long, end: Long) = expenseDao.getTotalExpense(start, end)

    fun getTotalIncome(start: Long, end: Long) = expenseDao.getTotalIncome(start, end)

    fun getCategoryStats(type: String, start: Long, end: Long) = expenseDao.getCategoryStats(type, start, end)

    fun getDailyStats(type: String, start: Long, end: Long) = expenseDao.getDailyStats(type, start, end)

    fun getByDate(date: String) = expenseDao.getByDate(date)

    // Category operations
    fun getAllCategories() = categoryDao.getAll()

    fun getCategoriesByType(type: String) = categoryDao.getByType(type)

    suspend fun getCategoryById(id: Long) = categoryDao.getById(id)

    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insert(category)

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun initDefaultCategories() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.expense + DefaultCategories.income)
        }
    }

    // Budget operations
    fun getBudgetsByMonth(month: Int) = budgetDao.getByMonth(month)

    fun getTotalBudget(month: Int) = budgetDao.getTotalBudget(month)

    suspend fun insertBudget(budget: BudgetEntity) = budgetDao.insert(budget)

    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.delete(budget)

    companion object {
        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun getInstance(context: Context): TransactionRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransactionRepository(context).also { INSTANCE = it }
            }
    }
}
