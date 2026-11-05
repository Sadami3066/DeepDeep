package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface PluginIndexFetchResult {
    data class Loaded(
        val index: PluginIndex,
        val fromCache: Boolean,
        val fetchedAt: Long,
        val note: String? = null,
    ) : PluginIndexFetchResult

    data class Unavailable(val reason: String) : PluginIndexFetchResult
}

internal class PluginIndexClient(
    private val cacheFile: File,
    private val fetcher: PluginFetcher = HttpPluginFetcher(),
    private val maxIndexBytes: Long = PluginUpdateDefaults.MAX_INDEX_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var currentIndex: PluginIndex? = null

    val current: PluginIndex? get() = currentIndex

    suspend fun fetch(indexUrl: String): PluginIndexFetchResult = withContext(ioDispatcher) {
        val url = indexUrl.trim()
        if (url.isEmpty()) return@withContext PluginIndexFetchResult.Unavailable("index url is blank")
        try {
            val text = fetcher.readText(url, maxIndexBytes)
            if (text.toByteArray(Charsets.UTF_8).size.toLong() > maxIndexBytes) {
                throw IOException("index exceeds $maxIndexBytes bytes")
            }
            when (val parsed = PluginIndexCodec.parse(text)) {
                is PluginIndexParseResult.Invalid -> cacheFallback(url, "invalid index: ${parsed.reason}")

                is PluginIndexParseResult.Valid -> {
                    currentIndex = parsed.index
                    val fetchedAt = clock()
                    runCatching { PluginIndexCache.write(cacheFile, parsed.index, url, fetchedAt) }
                    PluginIndexFetchResult.Loaded(
                        index = parsed.index,
                        fromCache = false,
                        fetchedAt = fetchedAt,
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cacheFallback(url, error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun cached(indexUrl: String? = null): CachedPluginIndex? = withContext(ioDispatcher) {
        val cached = PluginIndexCache.read(cacheFile) ?: return@withContext null
        val expected = indexUrl?.trim()
        if (!expected.isNullOrEmpty() && cached.indexUrl != null && cached.indexUrl != expected) {
            return@withContext null
        }
        currentIndex = cached.index
        cached
    }

    fun updatesFor(
        installed: Map<String, ExternalPluginRecord>,
        policy: UpdatePolicy = UpdatePolicy(),
    ): List<PluginUpdateCandidate> {
        val index = currentIndex ?: return emptyList()
        return updatesFor(index, installed, policy)
    }

    fun updatesFor(
        index: PluginIndex,
        installed: Map<String, ExternalPluginRecord>,
        policy: UpdatePolicy = UpdatePolicy(),
    ): List<PluginUpdateCandidate> = PluginUpdateSelector.select(index, installed, policy)

    private suspend fun cacheFallback(indexUrl: String, reason: String): PluginIndexFetchResult {
        val cached = cached(indexUrl)
        return if (cached == null) {
            PluginIndexFetchResult.Unavailable(reason)
        } else {
            PluginIndexFetchResult.Loaded(
                index = cached.index,
                fromCache = true,
                fetchedAt = cached.fetchedAt,
                note = reason,
            )
        }
    }
}
