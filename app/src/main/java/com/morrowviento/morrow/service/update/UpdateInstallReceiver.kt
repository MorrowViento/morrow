package com.morrowviento.morrow.service.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * 接收 `PackageInstaller` 会话的安装结果。
 *
 * 关键一支是 `STATUS_PENDING_USER_ACTION`：系统把「确认安装」的 Intent 塞在 extra 里，
 * 必须由我们拉起，否则会话会一直挂着，用户点了「安装」却什么也没发生。
 * 成功 / 失败这里只记日志，最终状态以系统为准（应用此时可能已被替换、进程重启）。
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "安装结果 status=$status message=$message")

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm == null) {
                Log.w(TAG, "系统要求用户确认，但没给确认 Intent")
                return
            }
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirm) }
                .onFailure { Log.w(TAG, "拉起安装确认页失败", it) }
        }
    }

    private companion object {
        const val TAG = "MorrowUpdateInstall"
    }
}
