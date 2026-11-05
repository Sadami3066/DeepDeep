package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecordStore
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import io.github.whywood.deepdeep.plugin.host.external.PublisherTrustStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ExternalUpdateCandidate(
    val pluginId: String,
    val installedVersion: String,
    val availableVersion: String,
    val sha256: String,
    val size: Long,
    val publisherKeyId: String,
    val url: String,
)

internal sealed interface UpdateCheckResultUi {
    data class Ready(
        val candidates: List<ExternalUpdateCandidate>,
        val fromCache: Boolean,
        val note: String?,
    ) : UpdateCheckResultUi

    data object NotConfigured : UpdateCheckResultUi

    data class Unavailable(val reason: String) : UpdateCheckResultUi
}

internal sealed interface UpdateStageResultUi {
    data class Staged(val pluginId: String, val version: String) : UpdateStageResultUi

    data class Rejected(val reason: String) : UpdateStageResultUi
}

internal class ExternalUpdateSupport(
    filesDir: File,
    hostVersion: String?,
    installer: PluginInstaller,
    trustStore: PublisherTrustStore,
    private val recordStore: ExternalPluginRecordStore,
    fetcher: PluginFetcher = HttpPluginFetcher(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val settingsStore = PluginSourceSettingsStore(
        file = PluginUpdateDefaults.settingsFile(filesDir),
        ioDispatcher = ioDispatcher,
    )

    private val client = PluginIndexClient(
        cacheFile = PluginUpdateDefaults.cacheFile(filesDir),
        fetcher = fetcher,
        ioDispatcher = ioDispatcher,
    )

    private val policy = UpdatePolicy(hostVersion = hostVersion)

    private val manager = PluginUpdateManager(
        installer = installer,
        trustStore = trustStore,
        downloader = PluginPackageDownloader(
            fetcher = fetcher,
            downloadsDir = PluginUpdateDefaults.downloadsDir(filesDir),
            ioDispatcher = ioDispatcher,
        ),
        policy = policy,
        ioDispatcher = ioDispatcher,
    )

    suspend fun loadSettings(): PluginSourceSettings = settingsStore.load()

    suspend fun saveSettings(settings: PluginSourceSettings) {
        settingsStore.save(settings)
    }

    suspend fun checkUpdates(): UpdateCheckResultUi = withContext(ioDispatcher) {
        val settings = settingsStore.load()
        val indexUrl = settings.indexUrl ?: return@withContext UpdateCheckResultUi.NotConfigured
        when (val fetched = client.fetch(indexUrl)) {
            is PluginIndexFetchResult.Unavailable -> UpdateCheckResultUi.Unavailable(fetched.reason)

            is PluginIndexFetchResult.Loaded -> {
                val installed = recordStore.load()
                val candidates = PluginUpdateSelector.select(fetched.index, installed, policy)
                UpdateCheckResultUi.Ready(
                    candidates = candidates.map { it.toUi() },
                    fromCache = fetched.fromCache,
                    note = fetched.note,
                )
            }
        }
    }

    suspend fun stageUpdate(pluginId: String): UpdateStageResultUi = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId)
            ?: return@withContext rejected("plugin '$pluginId' is not installed")
        record.pendingVersion?.let { pending ->
            return@withContext rejected("plugin '$pluginId' already has a pending update to $pending")
        }
        val settings = settingsStore.load()
        val indexUrl = settings.indexUrl
            ?: return@withContext rejected("plugin index is not configured")
        val index = when (val fetched = client.fetch(indexUrl)) {
            is PluginIndexFetchResult.Unavailable ->
                return@withContext rejected("plugin index is unavailable: ${fetched.reason}")

            is PluginIndexFetchResult.Loaded -> fetched.index
        }
        val entry = index.plugins.firstOrNull { it.id == pluginId }
            ?: return@withContext rejected("plugin '$pluginId' has no entry in the update index")
        when (val check = PluginUpdateSelector.checkUpdate(record, entry)) {
            is CandidateCheck.Rejected -> {
                val prefix = if (check.code == UpdateRejection.NOT_NEWER) {
                    "no update available for plugin '$pluginId'"
                } else {
                    check.code.name
                }
                return@withContext rejected("$prefix: ${check.reason}")
            }

            CandidateCheck.Ok -> Unit
        }
        when (val acceptable = PluginUpdateSelector.ensureAcceptable(entry, policy)) {
            is CandidateCheck.Rejected ->
                return@withContext rejected("${acceptable.code.name}: ${acceptable.reason}")

            CandidateCheck.Ok -> Unit
        }
        val candidate = PluginUpdateCandidate(
            entry = entry,
            installedVersion = record.version,
        )
        when (val result = manager.downloadAndStage(candidate)) {
            is UpdateStageResult.Staged -> UpdateStageResultUi.Staged(
                pluginId = result.record.id,
                version = result.record.pendingVersion ?: result.candidate.entry.version,
            )

            is UpdateStageResult.Rejected -> UpdateStageResultUi.Rejected(
                "${result.code.name}: ${result.reason}",
            )
        }
    }

    private fun PluginUpdateCandidate.toUi(): ExternalUpdateCandidate = ExternalUpdateCandidate(
        pluginId = entry.id,
        installedVersion = installedVersion,
        availableVersion = entry.version,
        sha256 = entry.sha256,
        size = entry.size,
        publisherKeyId = entry.publisherKeyId,
        url = entry.url,
    )

    private fun rejected(reason: String): UpdateStageResultUi.Rejected = UpdateStageResultUi.Rejected(reason)
}
