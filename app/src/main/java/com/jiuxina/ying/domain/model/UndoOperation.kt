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
    /**
     * 首次记录时刻。
     *
     * 冷启动恢复要靠它判断「这条删除还值不值得再给一次窗口」：系统随时可能回收进程，
     * 用户不该为此付出代价，但隔夜回来的旧撤销也没有意义。默认值等价于「按满窗口倒推」，
     * 旧版落盘数据（没有这个字段）解码后也落到同一个值上。
     */
    val pushedAtMillis: Long = expiresAtMillis - WINDOW_MILLIS,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis

    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    /** 撤销条倒计时用：向上取整，避免最后不足 1 秒时显示 0。 */
    fun remainingSeconds(nowMillis: Long): Int = ((remainingMillis(nowMillis) + 999L) / 1000L).toInt()

    /** 复制一条新窗口的撤销项（冷启动/回到前台时用），保留创建时刻。 */
    fun withDeadline(deadlineMillis: Long): UndoOperation = copy(expiresAtMillis = deadlineMillis)

    companion object {
        /** 撤销窗口：与原 Flutter 版一致的 6 秒。 */
        const val WINDOW_MILLIS = 6_000L
    }
}
