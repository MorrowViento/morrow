package com.morrowviento.morrow.service.update

import android.app.Application
import android.content.ContextWrapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ApkDownloaderTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: HttpServer
    private lateinit var context: ContextWrapper
    private val bytes = ByteArray(1024) { (it % 251).toByte() }

    @Before fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = temp.root
        }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @After fun tearDown() { server.stop(0) }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"
    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }
    private fun asset(path: String = "/apk", digest: String? = sha(bytes)) =
        UpdateAsset("test.apk", url(path), bytes.size.toLong(), digest)
    private fun downloader(vararg paths: String) = ApkDownloader(context) { paths.map(::url) }
    private fun parts() = File(temp.root, "updates").listFiles().orEmpty().filter { it.name.endsWith(".part") }
    private fun serve(path: String = "/apk", handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            try { handler(exchange) } finally { exchange.close() }
        }
    }
    private fun HttpExchange.respond(data: ByteArray = bytes, code: Int = 200, declared: Long = data.size.toLong()) {
        sendResponseHeaders(code, declared)
        responseBody.write(data)
        responseBody.flush()
    }

    @Test fun full200VerifiesAndPublishes() = runBlocking {
        serve { it.respond() }
        val file = downloader("/apk").download(asset()) { _, _ -> }
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(parts().isEmpty())
    }

    @Test fun shortReadIsRetainedAndValid206Resumes() = runBlocking {
        val calls = AtomicInteger()
        var range: String? = null
        serve { exchange ->
            if (calls.getAndIncrement() == 0) exchange.respond(bytes.copyOfRange(0, 300), declared = bytes.size.toLong())
            else {
                range = exchange.requestHeaders.getFirst("Range")
                exchange.responseHeaders.add("Content-Range", "bytes 300-1023/1024")
                exchange.respond(bytes.copyOfRange(300, bytes.size), code = 206)
            }
        }
        val downloader = downloader("/apk")
        assertTrue(runCatching { downloader.download(asset()) { _, _ -> } }.exceptionOrNull() is IOException)
        assertEquals(300L, parts().single().length())
        val result = downloader.download(asset()) { _, _ -> }
        assertEquals("bytes=300-", range)
        assertArrayEquals(bytes, result.readBytes())
    }

    @Test fun ignoredRange200OverwritesRatherThanAppends() = runBlocking {
        val calls = AtomicInteger()
        var range: String? = null
        serve { exchange ->
            range = exchange.requestHeaders.getFirst("Range")
            if (calls.getAndIncrement() == 0) exchange.respond(bytes.copyOfRange(0, 300), declared = bytes.size.toLong())
            else exchange.respond()
        }
        val downloader = downloader("/apk")
        runCatching { downloader.download(asset()) { _, _ -> } }
        assertArrayEquals(bytes, downloader.download(asset()) { _, _ -> }.readBytes())
        assertEquals("bytes=300-", range)
    }

    @Test fun mismatched206RangeDiscardsPartial() = runBlocking {
        val calls = AtomicInteger()
        serve { exchange ->
            if (calls.getAndIncrement() == 0) exchange.respond(bytes.copyOfRange(0, 300), declared = bytes.size.toLong())
            else {
                exchange.responseHeaders.add("Content-Range", "bytes 299-1023/1024")
                exchange.respond(bytes.copyOfRange(299, bytes.size), code = 206)
            }
        }
        val downloader = downloader("/apk")
        runCatching { downloader.download(asset()) { _, _ -> } }
        assertNotNull(runCatching { downloader.download(asset()) { _, _ -> } }.exceptionOrNull())
        assertTrue(parts().isEmpty())
    }

    @Test fun declaredTotalMismatchAndMissingRangeAreRejected() = runBlocking {
        serve { exchange ->
            exchange.responseHeaders.add("Content-Range", "bytes 0-1023/2048")
            exchange.respond(code = 206)
        }
        assertNotNull(runCatching { downloader("/apk").download(asset()) { _, _ -> } }.exceptionOrNull())
        server.removeContext("/apk")
        serve { it.respond(code = 206) }
        assertNotNull(runCatching { downloader("/apk").download(asset()) { _, _ -> } }.exceptionOrNull())
        assertTrue(parts().isEmpty())
    }

    @Test fun digestMismatchDiscardsBadContent() = runBlocking {
        serve { it.respond(ByteArray(bytes.size) { 7 }) }
        assertNotNull(runCatching { downloader("/apk").download(asset()) { _, _ -> } }.exceptionOrNull())
        assertTrue(parts().isEmpty())
    }

    @Test fun withoutDigestMirrorsDoNotSharePartialBytes() = runBlocking {
        var secondRange: String? = "not requested"
        serve("/one") { it.respond(ByteArray(300) { 7 }, declared = bytes.size.toLong()) }
        serve("/two") {
            secondRange = it.requestHeaders.getFirst("Range")
            it.respond()
        }
        val result = downloader("/one", "/two").download(asset("/one", digest = null)) { _, _ -> }
        assertNull(secondRange)
        assertArrayEquals(bytes, result.readBytes())
    }

    @Test fun differentAssetsDoNotSharePartials() = runBlocking {
        serve("/old") { it.respond(bytes.copyOfRange(0, 300), declared = bytes.size.toLong()) }
        var range: String? = "not requested"
        serve("/new") { range = it.requestHeaders.getFirst("Range"); it.respond() }
        runCatching { downloader("/old").download(asset("/old")) { _, _ -> } }
        assertArrayEquals(bytes, downloader("/new").download(asset("/new")) { _, _ -> }.readBytes())
        assertNull(range)
    }

    @Test fun cancelledDownloadDoesNotFallBackToAnotherMirror() = runBlocking {
        val secondCalls = AtomicInteger()
        serve("/one") { it.respond() }
        serve("/two") { secondCalls.incrementAndGet(); it.respond() }
        val download = async(Dispatchers.IO) {
            val job = currentCoroutineContext()[Job]!!
            downloader("/one", "/two").download(asset("/one")) { _, _ -> job.cancel() }
        }
        try {
            download.await()
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(0, secondCalls.get())
        }
    }

    @Test fun diskFailureDoesNotTryAnyMirror() = runBlocking {
        val calls = AtomicInteger()
        serve { calls.incrementAndGet(); it.respond() }
        File(temp.root, "updates").writeText("not a directory")
        assertNotNull(runCatching { downloader("/apk").download(asset()) { _, _ -> } }.exceptionOrNull())
        assertEquals(0, calls.get())
    }

    @Test fun textFetchUsesTheSameConnectionBoundary() = runBlocking {
        serve("/text") { it.respond("hello".toByteArray()) }
        assertEquals("hello", withContext(Dispatchers.IO) { UpdateNetwork.fetch(url("/text")) })
    }
}
