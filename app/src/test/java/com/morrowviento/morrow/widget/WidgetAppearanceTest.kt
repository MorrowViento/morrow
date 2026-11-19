package com.morrowviento.morrow.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import androidx.compose.ui.graphics.asAndroidBitmap
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.data.settings.BackdropSettings
import com.morrowviento.morrow.ui.glass.GlassOptics
import com.morrowviento.morrow.ui.glass.GlassWallpaper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetAppearanceTest {
    @get:Rule val temp = TemporaryFolder()
    private val context by lazy {
        object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = temp.root
        }
    }

    @After fun clearImage() = BackdropImage.clear(context)

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun blurChangesActualRgbAndPreservesFlatColors() {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        for (y in 0 until 20) for (x in 20 until 40) bitmap.setPixel(x, y, Color.WHITE)
        WidgetAppearance.blur(bitmap, 3)
        assertTrue(Color.red(bitmap.getPixel(18, 10)) in 1..254)
        assertTrue(Color.red(bitmap.getPixel(22, 10)) in 1..254)
        assertEquals(Color.BLACK, bitmap.getPixel(0, 10))
        assertEquals(Color.WHITE, bitmap.getPixel(39, 10))
        bitmap.eraseColor(Color.rgb(40, 140, 220))
        WidgetAppearance.blur(bitmap, 8)
        assertTrue(pixels(bitmap).all { it == Color.rgb(40, 140, 220) })
        bitmap.recycle()
    }

    @Test fun transparentEdgesDoNotIntroduceBlackRgbHalos() {
        val bitmap = Bitmap.createBitmap(32, 10, Bitmap.Config.ARGB_8888)
        for (y in 0 until 10) for (x in 16 until 32) bitmap.setPixel(x, y, Color.RED)
        WidgetAppearance.blur(bitmap, 3)
        val edge = bitmap.getPixel(14, 5)
        assertTrue(Color.alpha(edge) in 1..254)
        assertTrue(Color.red(edge) >= 250)
        assertEquals(0, Color.green(edge))
        assertEquals(0, Color.blue(edge))
        bitmap.recycle()
    }

    @Test fun dimensionBudgetKeepsAspectAndHandlesExtremeSizes() {
        for ((w, h) in listOf(4096 to 2048, 1080 to 2400, 100000 to 2, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val (bw, bh) = WidgetAppearance.boundedSize(w, h)
            assertTrue(bw > 0 && bh > 0 && maxOf(bw, bh) <= 1024)
            assertTrue(bw.toLong() * bh <= WidgetAppearance.MAX_PIXELS)
            if (minOf(w, h) > 10) assertEquals(w.toDouble() / h, bw.toDouble() / bh, 0.02)
        }
        assertEquals(1 to 1, WidgetAppearance.boundedSize(0, -1))
        var total = 0L
        repeat(16) {
            val (w, h) = WidgetAppearance.boundedSize(2000, 1000, WidgetAppearance.MAX_PIXELS / 16)
            total += w.toLong() * h
        }
        assertTrue(total <= WidgetAppearance.MAX_PIXELS)
    }

    @Test fun renderHasTransparentContinuousCornersAndDoesNotReplaceHomePlaceholder() = runBlocking {
        val settings = AppSettings(glassFrost = 0.34f)
        val home = GlassWallpaper.render(100, 200, androidx.compose.ui.graphics.Color.White, false,
            settings.resolvedAccent.blobs(false))
        val bitmap = WidgetAppearance.render(context, 320, 180, 1f, settings, WidgetStyle().colors(settings, false))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(255, Color.alpha(bitmap.getPixel(160, 90)))
        assertSame(home, GlassWallpaper.placeholder())
        assertFalse(home.asAndroidBitmap().isRecycled)
        assertSame(bitmap, WidgetAppearance.render(context, 320, 180, 1f, settings, WidgetStyle().colors(settings, false)))
    }

    @Test fun imageAndFrostProduceDifferentPixelsWithoutRecyclingSharedSource() = runBlocking {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 200) source.setPixel(x, y,
            if ((x / 8 + y / 8) % 2 == 0) Color.RED else Color.BLUE)
        val file = File(BackdropImage.dir(context).apply { mkdirs() }, "sample.jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        val settings = AppSettings(backdrop = BackdropSettings(mode = BackdropMode.IMAGE, image = file.name), glassFrost = 0f)
        val colors = WidgetStyle().colors(settings, false)
        val clear = WidgetAppearance.render(context, 200, 100, 1f, settings, colors)
        val shared = BackdropImage.load(context, file.name)!!
        val frosted = WidgetAppearance.render(context, 200, 100, 1f, settings.copy(glassFrost = 0.62f), colors)
        val satin = WidgetAppearance.render(context, 200, 100, 1f, settings.copy(backdrop = BackdropSettings()), colors)
        assertFalse(pixels(clear).contentEquals(pixels(frosted)))
        assertFalse(pixels(clear).contentEquals(pixels(satin)))
        assertFalse(shared.isRecycled)
        assertEquals(200, shared.width)
        val changed = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        file.outputStream().use { changed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.setLastModified(file.lastModified() + 2000)
        changed.recycle()
        val next = WidgetAppearance.render(context, 200, 100, 1f, settings, colors)
        assertNotSame(clear, next)
        assertFalse(pixels(clear).contentEquals(pixels(next)))
    }

    @Test fun cacheIsBoundedAndEvictionDoesNotRecycleBorrowedBitmaps() = runBlocking {
        val settings = AppSettings(glassFrost = 0f)
        val colors = WidgetStyle().colors(settings, false)
        val borrowed = WidgetAppearance.render(context, 512, 512, 1f, settings, colors)
        repeat(12) {
            WidgetAppearance.render(context, 512, 512, 1f, settings.copy(glassFrost = (it + 1) / 20f), colors)
            assertTrue(WidgetAppearance.cachedBytes <= WidgetAppearance.CACHE_BYTES)
        }
        assertFalse(borrowed.isRecycled)
        assertEquals(255, Color.alpha(borrowed.getPixel(256, 256)))
        val densityChanged = WidgetAppearance.render(context, 512, 512, 2f, settings, colors)
        assertNotSame(borrowed, densityChanged)
    }

    @Test fun structuredKeysDoNotAliasOldArithmeticSizeCollisions() = runBlocking {
        val settings = AppSettings(glassFrost = 0.34f)
        val colors = WidgetStyle().colors(settings, false)
        assertEquals(320 * 31 + 180 * 17, 337 * 31 + 149 * 17)
        val first = WidgetAppearance.render(context, 320, 180, 1f, settings, colors)
        val second = WidgetAppearance.render(context, 337, 149, 1f, settings, colors)
        assertNotSame(first, second)
        assertEquals(320, first.width)
        assertEquals(149, second.height)
    }

    @Test fun opticsAndRowVeilShareHomepageParameters() {
        assertEquals(0.5f, GlassOptics.frostRadiusDp(0f), 0f)
        assertEquals(25.5f, GlassOptics.frostRadiusDp(1f), 0f)
        assertEquals(0.26f, GlassOptics.veilAlpha(1f), 0f)
        assertEquals(0.72f, GlassOptics.veilAlpha(0f, true), 0f)
        for (dark in listOf(false, true)) {
            val colors = WidgetStyle().colors(dark, AppSettings().resolvedAccent)
            assertEquals(if (dark) 23 else 13, Color.alpha(WidgetAppearance.rowTint(colors, 0f, false)))
            // 4.4.0 起行卡片不透明度封顶 62%（158/255）：不封顶时「不透明」档会刷成实心白纸片
            assertEquals(158, Color.alpha(WidgetAppearance.rowTint(colors, 0f, true)))
        }
    }

    @Test fun responsiveSizesUseHostVariantsOrCorrectOrientationRange() {
        val sizes = arrayListOf(SizeF(200f, 100f), SizeF(400f, 80f), SizeF(200f, 100f))
        val options = Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes) }
        assertEquals(sizes.distinct(), WidgetRefresher.widgetSizes(options, false))
        val range = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 400)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
        }
        assertEquals(listOf(SizeF(200f, 100f)), WidgetRefresher.widgetSizes(range, false))
        assertEquals(listOf(SizeF(400f, 80f)), WidgetRefresher.widgetSizes(range, true))
        val many = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                ArrayList((1..30).map { SizeF(it * 10f, 100f) }))
        }
        assertEquals(16, WidgetRefresher.widgetSizes(many, false).size)
    }
}
