package com.morrowviento.morrow.ui.component

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 页级头部与圆形按钮的共用尺寸。
 *
 * 这些值原先各页面各写一套：首页「⋯」32dp、设置页返回 32dp、日历的翻页与关闭 34dp，
 * 头部内边距也各不相同（22/18、12/16、14/14）。单看一页没问题，来回切页就能看出
 * 「同一个位置的按钮大一圈」「标题比卡片多缩进一截」。集中到这里，改一处三页同步。
 *
 * 只收「跨页同形」的参数；像大标题字号（首页 34sp 折到 21sp）这种页面自身语义，
 * 留在各自文件里。
 */
internal object PageChrome {
    /** 头部圆形按钮（返回 / 关闭 / 更多 / 前后翻页）的直径。 */
    val CircleButtonSize = 34.dp

    /** 圆形按钮内的字形大小。 */
    val CircleGlyphSize = 17.sp

    /** 头部行的水平内边距，与卡片 16dp 缩进对齐。 */
    val HeaderHorizontal = 16.dp

    /** 头部行的上内边距。 */
    val HeaderTop = 12.dp

    /** 头部行的下内边距。 */
    val HeaderBottom = 4.dp

    /** 与圆形按钮同排的胶囊（排序胶囊等）的高度，取和圆钮同高才不显矮。 */
    val ChipHeight = 34.dp

    /**
     * 页内主操作按钮（详情页的「完成/置顶/删除」那一行）的高度。
     *
     * 同一行里起初混着 52dp 圆钮和 56dp 宽按钮，并排看圆钮矮一截；统一到 56dp。
     */
    val ActionButtonSize = 56.dp
}
