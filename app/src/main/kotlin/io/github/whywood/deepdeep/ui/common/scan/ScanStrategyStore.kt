package io.github.whywood.deepdeep.ui.common.scan

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class ScanStrategyStore(context: Context) {
    private val appContext = context.applicationContext

    val file: File = File(File(appContext.filesDir, "scan"), "strategies.json")

    private val repository = ScanStrategyRepository(::builtinText, file)

    fun load(force: Boolean = false): ScanStrategyRepository.Snapshot {
        val cached = cache[file.absolutePath]
        if (!force && cached != null && cached.signature == signature()) return cached.snapshot
        val snapshot = repository.load()
        cache[file.absolutePath] = Cached(signature(), snapshot)
        return snapshot
    }

    fun saveCustom(custom: List<ScanStrategy>): ScanStrategyRepository.Snapshot {
        repository.saveCustom(custom)
        return load(force = true)
    }

    fun restoreOfficial(snapshot: ScanStrategyRepository.Snapshot): ScanStrategyRepository.Snapshot {
        val restored = repository.restoreOfficial(snapshot)
        cache[file.absolutePath] = Cached(signature(), restored)
        return restored
    }

    fun importCustom(text: String): ScanStrategyRepository.ImportResult {
        val result = repository.importCustom(text)
        if (result.count != null) load(force = true)
        return result
    }

    fun exportFile(): File? = runCatching {
        val directory = File(appContext.cacheDir, "share-incoming").apply { mkdirs() }
        val target = File(directory, "scan-strategies.json")
        target.writeText(ScanStrategyJson.render(load(force = true).custom))
        target
    }.getOrNull()

    fun sharedText(): String = ScanStrategyJson.render(load(force = true).custom)

    private fun signature(): Long {
        if (!file.isFile) return -1L
        return file.lastModified() * 31 + file.length()
    }

    private fun builtinText(): String? = runCatching {
        appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    }.getOrNull()

    private data class Cached(val signature: Long, val snapshot: ScanStrategyRepository.Snapshot)

    private companion object {
        const val ASSET_PATH = "scan/strategies.json"
        val cache = ConcurrentHashMap<String, Cached>()
    }
}
