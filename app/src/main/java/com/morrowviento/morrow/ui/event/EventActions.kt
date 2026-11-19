package com.morrowviento.morrow.ui.event

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
    /** 时间块视图拖底边改时长：(事件 id, 新时长分钟)。 */
    val resizeDuration: (String, Int) -> Unit = { _, _ -> },
    /** 详情页勾选子任务：(事件 id, 子任务 id)。 */
    val toggleChecklist: (String, String) -> Unit = { _, _ -> },
    /** 详情页点「编辑」时按 id 取回完整事件并转成表单状态。 */
    val loadForm: suspend (String) -> EventFormState?,
    /** 归档：移出首页/日历/提醒，但保留数据与完成状态。 */
    val archive: (String) -> Unit = {},
    /** 从归档箱恢复到正常列表。 */
    val unarchive: (String) -> Unit = {},
    /** 清空归档箱（真删除，进撤销队列）。 */
    val clearArchived: () -> Unit = {},
)
