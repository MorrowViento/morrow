package com.jiuxina.ying.domain.model

/**
 * 可撤销操作的类型。
 *
 * 存储层面统一为「一批事件的快照」：
 * - [DELETE] / [CLEAR_COMPLETED] / [CLEAR_ALL]：快照是被删掉的事件，撤销即重新插入；
 * - [TOGGLE_COMPLETED] / [TOGGLE_PINNED]：快照是翻转前的那条事件，撤销即覆盖回旧值。
 *
 * 因此撤销动作只有一种实现（整批 upsert），不需要为每种操作各写一套回滚逻辑。
 */
enum class UndoKind { DELETE, TOGGLE_COMPLETED, TOGGLE_PINNED, CLEAR_COMPLETED, CLEAR_ALL }

/**
 * 待撤销操作。
 *
 * 设计前提：操作**立即落库**，撤销靠快照回填。这样 [expiresAtMillis] 可以被持久化，
 * 进程被杀后重启仍能按剩余时间继续提供撤销；若改成"延迟落库"，进程终止时删除意图无法重建。
 */
data class UndoOperation(
    val id: String,
    val kind: UndoKind,
    /** 撤销条上显示的一句话，例如「已删除「春节回家」」。 */
    val message: String,
    /** 受影响事件的完整快照（撤销时整批回填）。 */
    val snapshot: List<CountdownEvent>,
    val expiresAtMillis: Long,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis

    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    /** 撤销条倒计时用：向上取整，避免最后不足 1 秒时显示 0。 */
    fun remainingSeconds(nowMillis: Long): Int = ((remainingMillis(nowMillis) + 999L) / 1000L).toInt()

    companion object {
        /** 撤销窗口：与原 Flutter 版一致的 6 秒。 */
        const val WINDOW_MILLIS = 6_000L
    }
}
