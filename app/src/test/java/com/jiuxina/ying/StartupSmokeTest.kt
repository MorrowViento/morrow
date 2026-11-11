package com.jiuxina.ying

import android.os.Looper
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 启动冒烟测试（临时诊断用）。
 *
 * Robolectric 会按 manifest 实例化 `YingApp` 并调用 `onCreate()`，
 * 因此依赖装配（Room / DataStore / 通知渠道 / 小部件 / 备份仓库）里任何
 * 同步抛错都会在这里暴露成失败用例，而不是只留一句「打开就闪退」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupSmokeTest {

    @Test
    fun applicationOnCreateCompletes() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue("manifest 里的 Application 应为 YingApp", app is YingApp)
        val ying = app as YingApp

        // 逐个触碰装配结果：lateinit 未赋值会在这里抛 UninitializedPropertyAccessException
        ying.database
        ying.eventRepository
        ying.settingsRepository
        ying.categoryRepository
        ying.undoRepository
        ying.reminderScheduler
        ying.reminderNotifier
        ying.reminderPermissions
        ying.widgetRefresher
        ying.backupRepository
    }

    @Test
    fun mainActivityReachesResumed() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        val activity = controller.get()
        org.junit.Assert.assertFalse("Activity 应已进入 resumed", activity.isFinishing)
        controller.pause().stop().destroy()
    }

    /**
     * 强制走完首帧测量与布局。
     *
     * `setup()` 只到 onResume，Compose 的组合与测量要到真正 measure 时才发生——
     * 上一轮「点 + 号闪退」就是测量期抛的 infinity 约束异常，只有走到这里才暴露。
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun mainActivityFirstFrameMeasures() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val decor = controller.get().window.decorView
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)

        decor.measure(widthSpec, heightSpec)
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        org.junit.Assert.assertTrue("decorView 应完成测量", decor.measuredWidth > 0)
        controller.pause().stop().destroy()
    }
}
