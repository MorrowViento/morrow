package com.jiuxina.ying.ui.update

import com.jiuxina.ying.service.update.ReleaseInfo
import com.jiuxina.ying.service.update.UpdateAsset
import com.jiuxina.ying.service.update.UpdatePolicy

enum class UpdateStage {
    /** 没有弹层。 */
    IDLE,

    /** 正在并发探测各条线路。 */
    CHECKING,

    /** 有新版本，等用户决定。 */
    AVAILABLE,
    DOWNLOADING,
    READY,
    INSTALLING,

    /** 检查/下载/安装失败，展示原因与重试。 */
    FAILED,
}

data class UpdateUiState(
    val stage: UpdateStage = UpdateStage.IDLE,
    val release: ReleaseInfo? = null,
    val asset: UpdateAsset? = null,
    val doneBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val message: String? = null,
) {
    val visible: Boolean get() = stage != UpdateStage.IDLE
    val percent: Int get() = UpdatePolicy.percent(doneBytes, totalBytes)
    val busy: Boolean
        get() = stage == UpdateStage.CHECKING || stage == UpdateStage.DOWNLOADING ||
            stage == UpdateStage.INSTALLING
}

data class UpdateActions(
    val check: () -> Unit,
    val download: () -> Unit,
    val install: () -> Unit,
    val dismiss: () -> Unit,
)
