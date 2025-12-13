package com.example.localexpense

import com.example.localexpense.data.CategoryEntity
import com.example.localexpense.domain.CategoryUseCases
import com.example.localexpense.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * CategoryUseCases 单元测试
 * v1.9.5 新增
 */
class CategoryUseCasesTest {

    private lateinit var repository: ITransactionRepository
    private lateinit var useCase: CategoryUseCases

    @Before
    fun setup() {
        repository = mock()
        useCase = CategoryUseCases(repository)
    }

    @Test
    fun `getAllCategories should return flow from repository`() = runTest {
        // Given
        val categories = listOf(
            createCategoryEntity(1L, "餐饮", "expense"),
            createCategoryEntity(2L, "工资", "income")
        )
        whenever(repository.getAllCategories()).thenReturn(flowOf(categories))

        // When
        val result = useCase.getAllCategories().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("餐饮", result[0].name)
        verify(repository).getAllCategories()
    }

    @Test
    fun `getCategoriesByType should filter by type`() = runTest {
        // Given
        val type = "expense"
        val categories = listOf(
            createCategoryEntity(1L, "餐饮", type),
            createCategoryEntity(2L, "交通", type)
        )
        whenever(repository.getCategoriesByType(type)).thenReturn(flowOf(categories))

        // When
        val result = useCase.getCategoriesByType(type).first()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.type == type })
        verify(repository).getCategoriesByType(type)
    }

    @Test
    fun `getCategoryById should return category`() = runTest {
        // Given
        val id = 5L
        val category = createCategoryEntity(id, "购物", "expense")
        whenever(repository.getCategoryById(id)).thenReturn(category)

        // When
        val result = useCase.getCategoryById(id)

        // Then
        assertNotNull(result)
        assertEquals("购物", result?.name)
        verify(repository).getCategoryById(id)
    }

    @Test
    fun `getCategoryById should return null when not found`() = runTest {
        // Given
        val id = 999L
        whenever(repository.getCategoryById(id)).thenReturn(null)

        // When
        val result = useCase.getCategoryById(id)

        // Then
        assertNull(result)
        verify(repository).getCategoryById(id)
    }

    @Test
    fun `addCategory should insert and return id`() = runTest {
        // Given
        val category = createCategoryEntity(0L, "新分类", "expense")
        whenever(repository.insertCategory(category)).thenReturn(10L)

        // When
        val result = useCase.addCategory(category)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(10L, result.getOrNull())
        verify(repository).insertCategory(category)
    }

    @Test
    fun `addCategory should return failure on exception`() = runTest {
        // Given
        val category = createCategoryEntity(0L, "分类", "expense")
        whenever(repository.insertCategory(category)).thenThrow(RuntimeException("插入失败"))

        // When
        val result = useCase.addCategory(category)

        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `updateCategory should call repository update`() = runTest {
        // Given
        val category = createCategoryEntity(5L, "更新分类", "expense")
        whenever(repository.updateCategory(category)).thenReturn(Unit)

        // When
        val result = useCase.updateCategory(category)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).updateCategory(category)
    }

    @Test
    fun `deleteCategory should call repository delete`() = runTest {
        // Given
        val category = createCategoryEntity(3L, "待删除", "expense")
        whenever(repository.deleteCategory(category)).thenReturn(Unit)

        // When
        val result = useCase.deleteCategory(category)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).deleteCategory(category)
    }

    @Test
    fun `saveCategory should insert new category`() = runTest {
        // Given
        val category = createCategoryEntity(0L, "新分类", "expense")
        whenever(repository.insertCategory(category)).thenReturn(20L)

        // When
        val result = useCase.saveCategory(category)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(20L, result.getOrNull())
        verify(repository).insertCategory(category)
        verify(repository, never()).updateCategory(any())
    }

    @Test
    fun `saveCategory should update existing category`() = runTest {
        // Given
        val category = createCategoryEntity(10L, "已存在分类", "expense")
        whenever(repository.updateCategory(category)).thenReturn(Unit)

        // When
        val result = useCase.saveCategory(category)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(10L, result.getOrNull())
        verify(repository).updateCategory(category)
        verify(repository, never()).insertCategory(any())
    }

    // 辅助方法：创建测试用 CategoryEntity
    private fun createCategoryEntity(
        id: Long,
        name: String,
        type: String,
        icon: String = "default"
    ): CategoryEntity {
        return CategoryEntity(
            id = id,
            name = name,
            type = type,
            icon = icon,
            color = 0xFF000000
        )
    }
}
