package com.morrowviento.morrow.service.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** 一次检查的完整结果：最终采用的版本 + 每条线路的探测明细（诊断用）。 */
data class UpdateCheckResult(
    val release: ReleaseInfo?,
    val probes: List<ProbeResult>,
)

/**
 * 多线路并发探测最新版本。
 *
 * 三条默认线路（GitHub API / jsDelivr 清单 / raw 清单）同时发，谁先回来都在
 * [UpdatePolicy.newest] 里按版本号取最大者——所以「测速」决定的是**下载走哪条**，
 * 「有没有更新」永远以版本号为准，不受 CDN 缓存影响。
 */
class UpdateRepository {

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val urls = UpdateEndpoints.checkUrls()
        val probes = coroutineScope {
            urls.map { url -> async { probe(url) } }.awaitAllCompat()
        }
        probes.forEach {
            Log.d(TAG, "探测 ${it.url} → ${it.elapsedMillis}ms, ${if (it.release != null) "成功" else "失败: ${it.error}"}")
        }
        UpdateCheckResult(UpdatePolicy.newest(probes), probes)
    }

    private fun probe(url: String): ProbeResult {
        val start = System.nanoTime()
        return try {
            val text = UpdateNetwork.fetch(url)
            ProbeResult(url, elapsed(start), ReleaseJson.parse(text))
        } catch (error: Exception) {
            ProbeResult(url, elapsed(start), null, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun elapsed(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    // awaitAll 需要 kotlinx-coroutines-core 的扩展；显式等待避免额外 import 造成版本歧义
    private suspend fun List<kotlinx.coroutines.Deferred<ProbeResult>>.awaitAllCompat(): List<ProbeResult> =
        map { it.await() }

    private companion object {
        const val TAG = "MorrowUpdate"
    }
}
