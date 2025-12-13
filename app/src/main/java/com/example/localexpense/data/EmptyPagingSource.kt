package com.example.localexpense.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * 空的 PagingSource 实现
 * 用于空查询条件时返回空结果
 */
class EmptyPagingSource<T : Any> : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        return LoadResult.Page(
            data = emptyList(),
            prevKey = null,
            nextKey = null
        )
    }
}
