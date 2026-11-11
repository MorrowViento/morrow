package com.jiuxina.ying.ui.event

/**
 * 详情页与批量清理用到的回调集合。
 *
 * 单独收成一个结构体，而不是继续给 `HomeScreen` 平铺参数：
 * 后续 M2d 日历页还会增加事件操作，逐个加参数会让调用点难以维护。
 */
data class EventActions(
    val delete: (String) -> Unit,
    val toggleCompleted: (String) -> Unit,
    val togglePinned: (String) -> Unit,
    val clearCompleted: () -> Unit,
    val clearAll: () -> Unit,
    val undo: () -> Unit,
    /** 详情页点「编辑」时按 id 取回完整事件并转成表单状态。 */
    val loadForm: suspend (String) -> EventFormState?,
)
