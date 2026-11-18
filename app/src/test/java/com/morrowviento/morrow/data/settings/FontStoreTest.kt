package com.morrowviento.morrow.data.settings

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 字体导入/删除的回归测试。
 *
 * 这一层最怕的不是「导入失败」，而是**导入成功但界面什么都没变**：
 * 文件写进去了、偏好也记了，可文件名解析错位（多一个前缀或少一段显示名），
 * 设置行上就会变成一串哈希。所以这里既验文件落盘，也验「显示名能原样回来」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FontStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Application get() = RuntimeEnvironment.getApplication()

    /** 造一份「看起来像字体」的字节：只要前四个字节是 sfnt 魔数，校验就会放行。 */
    private fun ttfBytes(magic: IntArray = intArrayOf(0x00, 0x01, 0x00, 0x00)): ByteArray =
        ByteArray(512).also { bytes ->
            magic.forEachIndexed { i, v -> bytes[i] = v.toByte() }
        }

    private fun sourceFile(name: String, bytes: ByteArray): Uri {
        val file = temp.newFile(name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun clearFontDir() {
        FontStore.dir(context).listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `导入成功后文件落盘并可被列出`() {
        clearFontDir()
        val uri = sourceFile("霞鹜文楷.ttf", ttfBytes())

        val font = FontStore.import(context, uri).getOrThrow()

        assertTrue("文件应写进私有目录", FontStore.fileOf(context, font.fileName).isFile)
        assertTrue("选择记录应指向存在的文件", FontStore.exists(context, font.fileName))
        // 显示名要能穿过「font-<hash>-<名称>.<ext>」这层编码回到原文，
        // 否则设置行上显示的是一串哈希
        assertEquals("霞鹜文楷", font.displayName)
        assertEquals("霞鹜文楷", FontStore.installed(context).single().displayName)
    }

    @Test
    fun `同名字体重复导入不产生第二份文件`() {
        clearFontDir()
        // 同一个来源文件导两次：内容与显示名都相同，落在同一个哈希文件名上
        val uri = sourceFile("字体.ttf", ttfBytes())
        val first = FontStore.import(context, uri).getOrThrow()
        val second = FontStore.import(context, uri).getOrThrow()

        assertEquals(first.fileName, second.fileName)
        assertEquals("同内容同名字体应复用同一份文件", 1, FontStore.installed(context).size)
    }

    @Test
    fun `内容不是字体时拒绝导入`() {
        clearFontDir()
        // PNG 头：用户完全可能把图片当字体选进来
        val png = Uri.fromFile(temp.newFile("fake.ttf").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        })

        val result = FontStore.import(context, png)

        assertTrue("应判定为失败", result.isFailure)
        assertTrue("提示要说明不是有效字体", result.exceptionOrNull()!!.message!!.contains("字体"))
        assertEquals("失败时不该留下文件", 0, FontStore.installed(context).size)
    }

    @Test
    fun `扩展名不是字体时拒绝导入`() {
        clearFontDir()
        val apk = Uri.fromFile(temp.newFile("伪装.apk").apply { writeBytes(ttfBytes()) })

        val result = FontStore.import(context, apk)

        assertTrue(result.isFailure)
        assertEquals(0, FontStore.installed(context).size)
    }

    @Test
    fun `删除后既不在列表里也不再视为存在`() {
        clearFontDir()
        val font = FontStore.import(context, sourceFile("临时.ttf", ttfBytes())).getOrThrow()
        assertNotNull(font.fileName)

        assertTrue(FontStore.delete(context, font.fileName))

        assertFalse(FontStore.exists(context, font.fileName))
        assertTrue(FontStore.installed(context).isEmpty())
    }

    @Test
    fun `删除时不允许越过私有目录去删别处的文件`() {
        clearFontDir()
        val outside = temp.newFile("外部.ttf").apply { writeBytes(ttfBytes()) }

        // 传入带路径的名字：实现里只取最后一段，所以这里应当只是「找不到」而不是删掉外部文件
        assertFalse(FontStore.delete(context, "../../${outside.name}"))
        assertTrue("外部文件不能被删除", outside.isFile)
    }

    @Test
    fun `系统默认字体不触发任何文件判断`() {
        assertFalse(AppFont.SYSTEM.isCustom)
        assertEquals("系统默认", AppFont.SYSTEM.label)
        assertFalse(FontStore.exists(context, ""))
    }

    @Test
    fun `otf 与 ttc 的魔数也能通过`() {
        clearFontDir()
        // OTTO
        val otf = FontStore.import(
            context,
            sourceFile("思源宋体.otf", ttfBytes(intArrayOf(0x4F, 0x54, 0x54, 0x4F))),
        )
        // ttcf
        val ttc = FontStore.import(
            context,
            sourceFile("集合.ttc", ttfBytes(intArrayOf(0x74, 0x74, 0x63, 0x66))),
        )

        assertTrue(otf.isSuccess)
        assertTrue(ttc.isSuccess)
        assertEquals(2, FontStore.installed(context).size)
    }

    @Test
    fun `空文件被拒绝`() {
        clearFontDir()
        val empty = Uri.fromFile(temp.newFile("空.ttf"))

        assertTrue(FontStore.import(context, empty).isFailure)
    }

    @Test
    fun `已导入列表按显示名排序`() {
        clearFontDir()
        FontStore.import(context, sourceFile("b字体.ttf", ttfBytes())).getOrThrow()
        FontStore.import(
            context,
            sourceFile("a字体.ttf", ttfBytes(intArrayOf(0x4F, 0x54, 0x54, 0x4F))),
        ).getOrThrow()

        assertEquals(listOf("a字体", "b字体"), FontStore.installed(context).map { it.displayName })
    }

    @Test
    fun `目录外的临时文件不会被当成字体`() {
        clearFontDir()
        // 直接往目录里塞一个扩展名不对的文件，列举时应当忽略
        File(FontStore.dir(context), "notes.txt").writeText("not a font")
        FontStore.import(context, sourceFile("真字体.ttf", ttfBytes())).getOrThrow()

        assertEquals(1, FontStore.installed(context).size)
    }
}
