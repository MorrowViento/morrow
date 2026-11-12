package com.morrowviento.morrow.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 启动轨迹与崩溃记录。
 *
 * 存在理由：真机上「打开即闪退」时，本机没有 adb 通道、Robolectric 又跳过真实绘制，
 * 崩溃点只能靠设备自己留下证据。这里做两件事：
 *
 * 1. [mark] 把启动的每一步追加进 `startup.log`，**每步立即落盘**。
 *    即使崩溃是 native 层（SIGSEGV 不会被 Java 的 UncaughtExceptionHandler 捕获），
 *    日志里最后一条也能指出卡在哪一步。
 * 2. 安装 [Thread.setDefaultUncaughtExceptionHandler]，把 Java 异常堆栈写成 `crash-*.txt`。
 *
 * **只在 debug 包生效**：正式包不落盘、不接管崩溃处理器（接管会挡住系统/商店的崩溃上报）。
 * debug 判定取 `FLAG_DEBUGGABLE`，不依赖 buildConfig 开关。
 *
 * 落盘位置优先 `Android/media/<包名>/`：Android 11+ 起应用可自由读写自己的 media 目录，
 * 且**任何文件管理器都能直接看到**，用户取出日志不需要电脑、不需要 root。
 * 外部存储不可用时回落到应用私有目录，功能不受影响，只是用户取文件麻烦一点。
 */
object StartupTrace {

    private const val TAG = "MorrowStartup"

    /** 追踪文件超过这个大小就重开，避免每次启动都往里追加导致无限增长。 */
    private const val MAX_BYTES = 64L * 1024

    @Volatile
    private var dir: File? = null

    /** 诊断目录（对用户可见）；为空表示不落盘——正式包或落盘不可用时都为空。 */
    fun directory(): File? = dir

    /**
     * 安装崩溃捕获并写下第一条轨迹。必须尽早在 `Application.onCreate` 调用，
     * 否则 Application 自身的构造/初始化崩溃不会被记录。
     */
    fun install(context: Context) {
        if (dir != null) return

        val app = context.applicationContext
        // 正式包什么都不装：轨迹只进 logcat，崩溃处理器保持系统默认
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            dir = resolveDir(app)
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                // 写日志本身失败绝不能掩盖原始崩溃：包住后继续交给原处理器
                runCatching { writeCrash(app, thread, error) }
                previous?.uncaughtException(thread, error)
            }
        }

        mark("app", "install sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} " +
            "device=${Build.MANUFACTURER}/${Build.MODEL} pid=${android.os.Process.myPid()} " +
            "debug=$debuggable")
    }

    /** 记录一行启动轨迹：同时进 logcat 与 startup.log，且立刻 flush。 */
    fun mark(scope: String, message: String) {
        Log.i(TAG, "[$scope] $message")
        val target = dir ?: return
        runCatching {
            val file = File(target, "startup.log")
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText("${timestamp()} [$scope] $message\n")
        }
    }

    /** 把异常记进轨迹但不中断流程：用于「这一步失败也不该拖垮启动」的可选步骤。 */
    fun record(scope: String, message: String, error: Throwable) {
        Log.w(TAG, "[$scope] $message", error)
        mark(scope, "$message: ${error::class.java.simpleName}: ${error.message}")
    }

    private fun resolveDir(app: Context): File? {
        val candidates = ArrayList<File>(2)
        // Android/media/<pkg>：用户可见，无需任何存储权限
        app.getExternalMediaDirs()?.firstOrNull()?.let { candidates += it }
        // 私有目录兜底：至少保证诊断信息不丢
        candidates += File(app.filesDir, "diag")

        return candidates.firstOrNull { candidate ->
            runCatching {
                candidate.mkdirs()
                candidate.isDirectory && candidate.canWrite()
            }.getOrDefault(false)
        }
    }

    private fun writeCrash(app: Context, thread: Thread, error: Throwable) {
        val target = dir ?: return
        val text = buildString {
            appendLine("time    : ${timestamp()}")
            appendLine("thread  : ${thread.name}")
            appendLine("device  : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("app     : ${app.packageName} ${appVersionText(app)}")
            appendLine()
            appendLine("--- startup trace (tail) ---")
            runCatching {
                val trace = File(target, "startup.log")
                if (trace.exists()) trace.readLines().takeLast(40).forEach { appendLine(it) }
            }
            appendLine()
            appendLine("--- stacktrace ---")
            appendLine(Log.getStackTraceString(error))
        }
        File(target, "crash-${System.currentTimeMillis()}.txt").writeText(text)
    }

    private fun appVersionText(app: Context): String = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("unknown")

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
