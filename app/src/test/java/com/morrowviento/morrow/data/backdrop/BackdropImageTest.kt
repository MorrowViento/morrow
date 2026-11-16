package com.morrowviento.morrow.data.backdrop

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
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
class BackdropImageTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var context: ContextWrapper

    @Before fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = temp.root
        }
    }

    @After fun tearDown() { BackdropImage.clear(context) }

    private fun source(name: String, width: Int = 40, height: Int = 20): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 140, 220))
        val file = File(temp.root, name)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        bitmap.recycle()
        return file
    }

    @Test fun sameColorImportsHaveDifferentIdentityAndOldBitmapIsNotRecycled() {
        val uri = Uri.fromFile(source("source.jpg"))
        val first = BackdropImage.import(context, uri)
        val bitmap = BackdropImage.load(context, first.name)!!
        val second = BackdropImage.import(context, uri)
        assertNotEquals(first.name, second.name)
        assertEquals(first.seed, second.seed)
        assertNotEquals(0L, first.seed)
        assertNotSame(bitmap, BackdropImage.load(context, second.name))
        BackdropImage.retain(context, second.name)
        assertFalse(BackdropImage.file(context, first.name).exists())
        assertTrue(BackdropImage.file(context, second.name).exists())
        assertFalse(bitmap.isRecycled)
        assertEquals(listOf(second.name), BackdropImage.dir(context).listFiles()!!.map { it.name })
    }

    @Test fun removingUncommittedNewImageKeepsOldImage() {
        val uri = Uri.fromFile(source("source.jpg"))
        val old = BackdropImage.import(context, uri)
        val next = BackdropImage.import(context, uri)
        BackdropImage.remove(context, next.name)
        assertTrue(BackdropImage.file(context, old.name).isFile)
        assertFalse(BackdropImage.file(context, next.name).exists())
        assertNotNull(BackdropImage.load(context, old.name))
    }

    @Test fun longestSideIsBoundedAndLegacyCustomJpegStillLoads() {
        val imported = BackdropImage.import(context, Uri.fromFile(source("large.jpg", 3000, 1000)))
        val bitmap = BackdropImage.load(context, imported.name)!!
        assertEquals(2400, bitmap.width)
        assertEquals(800, bitmap.height)
        BackdropImage.file(context, imported.name).copyTo(BackdropImage.file(context, "custom.jpg"))
        val legacy = BackdropImage.load(context, "custom.jpg")!!
        assertEquals(2400, legacy.width)
        assertEquals(800, legacy.height)
    }

    @Test fun exifRotationIsAppliedBeforeReencoding() {
        val jpeg = source("plain.jpg", 40, 20).readBytes()
        // Minimal APP1: little-endian TIFF, one SHORT orientation tag set to 6 (90 degrees clockwise).
        val payload = byteArrayOf(
            69, 120, 105, 102, 0, 0, 73, 73, 42, 0, 8, 0, 0, 0,
            1, 0, 18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0,
        )
        val encoded = ByteArrayOutputStream().apply {
            write(jpeg, 0, 2)
            write(0xff); write(0xe1)
            write((payload.size + 2) ushr 8); write((payload.size + 2) and 0xff)
            write(payload); write(jpeg, 2, jpeg.size - 2)
        }.toByteArray()
        val rotated = File(temp.root, "rotated.jpg").apply { writeBytes(encoded) }
        val imported = BackdropImage.import(context, Uri.fromFile(rotated))
        val bitmap = BackdropImage.load(context, imported.name)!!
        assertEquals(20, bitmap.width)
        assertEquals(40, bitmap.height)
    }

    @Test fun corruptSourceDoesNotChangePublishedFiles() {
        val imported = BackdropImage.import(context, Uri.fromFile(source("source.jpg")))
        val corrupt = File(temp.root, "corrupt.jpg").apply { writeText("invalid jpeg") }
        assertNotNull(runCatching { BackdropImage.import(context, Uri.fromFile(corrupt)) }.exceptionOrNull())
        assertEquals(listOf(imported.name), BackdropImage.dir(context).listFiles()!!.map { it.name })
    }

    @Test fun cropGeometryPreservesRatioFocusAndBounds() {
        val initial = BackdropCrop.centered(400, 200, 0.5f)
        assertEquals(BackdropCrop(150f, 0f, 100f, 200f), initial)
        val zoomed = initial.transform(400, 200, 0.5f, 0.25f, 0.75f, 0f, 0f, 2f)
        assertEquals(175f, zoomed.left + zoomed.width * 0.25f, 0.001f)
        assertEquals(150f, zoomed.top + zoomed.height * 0.75f, 0.001f)
        val moved = zoomed.transform(400, 200, 0.5f, 0.5f, 0.5f, 100f, -100f, 100f)
        assertEquals(0f, moved.left, 0.001f)
        assertEquals(200f, moved.top + moved.height, 0.001f)
        assertEquals(initial.width / 8f, moved.width, 0.001f)
        assertEquals(0.5f, moved.width / moved.height, 0.001f)
        val resetZoom = moved.transform(400, 200, 0.5f, 0.5f, 0.5f, 0f, 0f, 0.001f)
        assertEquals(initial.width, resetZoom.width, 0.001f)
        assertEquals(initial.height, resetZoom.height, 0.001f)
        assertEquals(BackdropCrop(0f, 75f, 400f, 50f), BackdropCrop.centered(400, 200, 8f))
    }

    @Test fun cropPublishesOnlySelectedPixelsAndExtractsTheirColor() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.RED)
        canvas.drawRect(50f, 0f, 100f, 100f, android.graphics.Paint().apply { color = Color.BLUE })
        val crop = BackdropCrop(50f, 0f, 50f, 100f)
        val imported = BackdropImage.importCropped(context, bitmap, crop)
        val selected = BackdropImage.load(context, imported.name)!!
        assertEquals(50, selected.width)
        assertEquals(100, selected.height)
        assertTrue(Color.blue(selected.getPixel(25, 50)) > 240)
        assertTrue(Color.red(selected.getPixel(25, 50)) < 10)
        assertEquals(PaletteExtractor.dominantSeed(selected), imported.seed)
        assertFalse(bitmap.isRecycled)
        BackdropImage.remove(context, imported.name)
        assertFalse(selected.isRecycled)
        assertTrue(BackdropImage.dir(context).listFiles()!!.isEmpty())
        assertTrue(runCatching { BackdropImage.importCropped(context, bitmap, crop.copy(left = -1f)) }.isFailure)
        assertTrue(BackdropImage.dir(context).listFiles()!!.isEmpty())
    }

    @Test fun pathsCannotEscapeTheBackdropDirectory() {
        for (name in listOf("../outside.jpg", "a/b.jpg", "a\\b.jpg", ".", "..")) {
            assertTrue(runCatching { BackdropImage.file(context, name) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
