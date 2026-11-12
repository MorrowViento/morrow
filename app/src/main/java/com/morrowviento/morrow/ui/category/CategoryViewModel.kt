package com.morrowviento.morrow.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.morrowviento.morrow.data.category.CategoryDelete
import com.morrowviento.morrow.data.category.CategoryEdit
import com.morrowviento.morrow.data.category.CategoryItem
import com.morrowviento.morrow.data.category.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * 分类管理的操作集合。
 *
 * 有意暴露挂起函数而不是「发射事件 + 回读状态」：分类编辑是用户直接输入的操作，
 * 需要把「重名 / 超长 / 至少保留一个」这类失败原因同步回给输入框，异步状态流做不到这一点。
 */
data class CategoryActions(
    val add: suspend (String) -> CategoryEdit,
    val rename: suspend (String, String) -> CategoryEdit,
    val delete: suspend (String, String?) -> CategoryDelete,
)

/**
 * 分类管理 ViewModel（M2b2）。
 *
 * 只负责列表状态的持有与操作转发；校验规则在 `CategoryRules`，存储与级联在 `CategoryRepository`。
 */
class CategoryViewModel(private val repository: CategoryRepository) : ViewModel() {

    val items: StateFlow<List<CategoryItem>> = repository.observeItems()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(name: String): CategoryEdit = repository.add(name)

    suspend fun rename(from: String, to: String): CategoryEdit = repository.rename(from, to)

    suspend fun delete(name: String, target: String?): CategoryDelete = repository.delete(name, target)

    companion object {
        fun factory(repository: CategoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CategoryViewModel(repository) as T
            }
    }
}
