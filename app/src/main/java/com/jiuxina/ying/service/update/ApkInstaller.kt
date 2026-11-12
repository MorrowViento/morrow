package com.jiuxina.ying.service.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * 安装已下载的 APK。
 *
 * 两条路：优先 `PackageInstaller` 会话（应用内完成，进度可控），
 * 失败再退到系统安装页（ACTION_VIEW + FileProvider）。
 *
 * **关于「静默安装」的实情**：普通第三方应用不是设备所有者，即便用 PackageInstaller
 * 提交会话，系统仍会弹一次「是否安装」确认（STATUS_PENDING_USER_ACTION）。
 * 真正无人值守的静默安装需要设备所有者/系统签名，不在本应用能力范围内。
 * 所以这里的目标是把用户留在应用内直到最后一步确认，而不是绕过系统确认。
 */
object ApkInstaller {

    fun install(context: Context, apk: File): Boolean {
        if (!apk.isFile || apk.length() == 0L) return false
        val bySession = runCatching { installBySession(context, apk) }
            .onFailure { Log.w(TAG, "会话安装不可用，改用系统安装页", it) }
            .getOrDefault(false)
        if (bySession) return true
        return runCatching { installByViewIntent(context, apk) }
            .onFailure { Log.w(TAG, "系统安装页也拉不起来", it) }
            .isSuccess
    }

    private fun installBySession(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val receiver = Intent(context, UpdateInstallReceiver::class.java)
                .setPackage(context.packageName)
            val pending = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                receiver,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
        Log.i(TAG, "已提交安装会话 sessionId=$sessionId")
        return true
    }

    private fun installByViewIntent(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private const val TAG = "YingUpdate"
}
