package io.github.whywood.deepdeep.market

import android.content.Context
import android.util.Log
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.data.SkillArchiveResult
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.runtime.McpConfigStore
import java.io.File
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

class MarketRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val skillRepository: SkillRepository,
    private val pluginManager: PluginManagerFacade,
    private val mcpConfigStore: McpConfigStore,
    private val http: OkHttpClient = defaultHttpClient(),
) : MarketRepository {
    private val filesDir = File(context.filesDir, MarketDefaults.MARKET_DIR)
    private val registryFile = File(filesDir, "registry.json")
    private val signatureFile = File(filesDir, "registry.json.sig")
    private val metaFile = File(filesDir, "meta.json")
    private val skillVersionsFile = File(filesDir, "skills.json")
    private val downloadDir = File(filesDir, "download")

    private val refreshMutex = Mutex()
    private val installMutex = Mutex()

    private val _state = MutableStateFlow<MarketState>(MarketState.Loading)
    override val state: StateFlow<MarketState> = _state.asStateFlow()

    private val _installStates = MutableStateFlow<Map<String, MarketInstallState>>(emptyMap())
    override val installStates: StateFlow<Map<String, MarketInstallState>> = _installStates.asStateFlow()

    private val installedContent = MutableStateFlow(InstalledContent())
    private val autoUpdatePlanner = MarketAutoUpdatePlanner()

    init {
        scope.launch {
            merge(
                pluginManager.state.map { plugins -> plugins.plugins }.distinctUntilChanged(),
                skillRepository.changes,
                mcpConfigStore.changes,
            ).collect { refreshInstalledStates() }
        }
    }

    override fun entries(type: MarketEntryType): List<MarketEntry> {
        val ready = _state.value as? MarketState.Ready ?: return emptyList()
        return ready.registry.entries.filter { it.type == type }
    }

    override fun installedVersion(entry: MarketEntry): String? = installedContent.value.versionOf(entry)

    override suspend fun refresh(force: Boolean): Boolean = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            filesDir.mkdirs()
            val cached = readCached()
            val fresh = !force && cached != null && System.currentTimeMillis() - cached.fetchedAt < MarketDefaults.CACHE_TTL_MS
            if (fresh) {
                return@withContext completeRefresh(cached.registry, MarketSource.Cache, cached.fetchedAt)
            }
            val network = runCatching { fetchRemote() }.onFailure { failure ->
                Log.w(TAG, "market refresh failed: ${failure.message}")
            }.getOrNull()
            if (network != null) {
                saveCache(network)
                return@withContext completeRefresh(network.registry, MarketSource.Network, System.currentTimeMillis())
            }
            if (cached != null) {
                return@withContext completeRefresh(cached.registry, MarketSource.Cache, cached.fetchedAt)
            }
            val seed = runCatching { fetchSeed() }.getOrNull()
            if (seed != null) {
                return@withContext completeRefresh(seed, MarketSource.Seed, 0L)
            }
            if (_state.value !is MarketState.Ready) {
                _state.value = MarketState.Failed("市场暂时无法加载，请稍后再试")
            }
            false
        }
    }

    override suspend fun refreshInstalledStates() = installMutex.withLock {
        withContext(Dispatchers.IO) { refreshInstalled() }
    }

    private suspend fun completeRefresh(registry: MarketRegistry, source: MarketSource, fetchedAt: Long): Boolean {
        _state.value = MarketState.Ready(registry, source, fetchedAt)
        installMutex.withLock { withContext(Dispatchers.IO) { refreshInstalled() } }
        autoUpdateInstalled(registry)
        return true
    }

    private suspend fun autoUpdateInstalled(registry: MarketRegistry) {
        val pending = runCatching {
            pluginManager.state.value.plugins
                .filter { it.phase == PluginPhaseUi.PENDING_RESTART }
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())
        val targets = autoUpdatePlanner.plan(registry.entries, _installStates.value)
            .filter { it.id !in pending }
        for (entry in targets) {
            autoUpdatePlanner.markAttempt(entry.id)
            val result = install(entry)
            if (result.isSuccess) {
                autoUpdatePlanner.markInstalled(entry.id)
            } else {
                Log.w(TAG, "market auto update failed: ${entry.id}")
            }
        }
    }

    override suspend fun install(entry: MarketEntry, apiKey: String?): Result<Unit> = installMutex.withLock {
        withContext(Dispatchers.IO) {
            setInstallState(entry.id, MarketInstallState.Installing(null))
            try {
                when (entry.type) {
                    MarketEntryType.Plugin -> installPlugin(entry)
                    MarketEntryType.Skill -> installSkill(entry)
                    MarketEntryType.Mcp -> installMcp(entry, apiKey)
                }
                refreshInstalled(clearInstallingId = entry.id)
                when (_installStates.value[entry.id]) {
                    is MarketInstallState.Installed,
                    is MarketInstallState.UpdateAvailable,
                    -> Result.success(Unit)

                    else -> {
                        setInstallState(entry.id, MarketInstallState.Failed("安装失败，请稍后再试"))
                        Result.failure(IllegalStateException("安装失败，请稍后再试"))
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                Log.w(TAG, "install ${entry.id} failed: ${failure.message}")
                if (entry.type == MarketEntryType.Plugin) {
                    runCatching { pluginManager.refreshNow() }
                }
                setInstallState(entry.id, MarketInstallState.Failed(failure.message ?: "安装失败，请稍后再试"))
                Result.failure(failure)
            }
        }
    }

    private suspend fun refreshInstalled(clearInstallingId: String? = null) {
        val skillVersions = readSkillVersions()
        val skills = runCatching { skillRepository.list() }
            .getOrNull()?.getOrNull()
            .orEmpty()
            .filter { it.globalEnabled && it.id.isNotBlank() }
            .associate { it.id to skillVersions[it.id] }
        val content = InstalledContent(
            plugins = runCatching { pluginManager.state.value.plugins }
                .getOrDefault(emptyList())
                .mapNotNull { snapshot -> snapshot.installedVersion?.let { snapshot.id to it } }
                .toMap(),
            skills = skills,
            mcp = runCatching { mcpConfigStore.loadServers() }
                .getOrDefault(emptyMap())
                .mapNotNull { (name, config) -> (config as? McpServerConfig.Http)?.let { name to it.url } }
                .toMap(),
        )
        installedContent.value = content
        val previous = if (clearInstallingId == null) {
            _installStates.value
        } else {
            _installStates.value - clearInstallingId
        }
        _installStates.value = resolveInstallStates(
            entries = currentRegistry()?.entries.orEmpty(),
            installed = content,
            previous = previous,
        )
    }

    private fun readSkillVersions(): Map<String, String> = runCatching {
        Json.parseToJsonElement(skillVersionsFile.readText()).jsonObject.mapNotNull { (id, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeSkillVersion(id: String, version: String) {
        runCatching {
            filesDir.mkdirs()
            val versions = readSkillVersions() + (id to version)
            skillVersionsFile.writeText(
                buildJsonObject {
                    versions.forEach { (skillId, skillVersion) -> put(skillId, JsonPrimitive(skillVersion)) }
                }.toString(),
            )
        }.onFailure { failure -> Log.w(TAG, "skill version write failed: ${failure.message}") }
    }

    private suspend fun installPlugin(entry: MarketEntry) {
        val artifact = entry.artifact ?: error("插件缺少下载信息")
        val file = download(artifact, "plugins", MarketDefaults.MAX_PLUGIN_BYTES)
        try {
            val inspection = pluginManager.inspectExternal(file) ?: error("插件包无法识别")
            if (inspection.pluginId != entry.id) error("插件 ID 与市场条目不匹配")
            if (!inspection.sha256.equals(artifact.sha256, ignoreCase = true)) error("插件包校验失败")
            val result = pluginManager.installExternal(file, acceptPublisher = true)
            if (!result.success) error(result.error ?: "插件安装失败")
            pluginManager.refreshNow()
            val snapshot = pluginManager.state.value.plugins.firstOrNull { it.id == entry.id }
            if (snapshot?.installedVersion == null) error("插件安装失败")
        } finally {
            file.delete()
        }
    }

    private suspend fun installSkill(entry: MarketEntry) {
        val artifact = entry.artifact ?: error("技能缺少下载信息")
        val file = download(artifact, "skills", MarketDefaults.MAX_SKILL_BYTES)
        try {
            val bytes = file.readBytes()
            val result = skillRepository.install(bytes, entry.id)
            if (result is SkillArchiveResult.Failure) error(result.error.name)
            val skill = (result as SkillArchiveResult.Success).skill
            if (!skillRepository.setGlobalEnabled(skill.id, true)) error("安装失败，请稍后再试")
            skillRepository.syncActiveSkills()
            writeSkillVersion(skill.id, entry.version)
        } finally {
            file.delete()
        }
    }

    private suspend fun installMcp(entry: MarketEntry, apiKey: String?) {
        val meta = entry.mcp ?: error("MCP 配置缺失")
        if (meta.transport != "http") error("暂不支持该连接方式")
        val servers = mcpConfigStore.loadServers()
        val existingName = servers.entries.firstOrNull { (_, config) ->
            (config as? McpServerConfig.Http)?.url == meta.url
        }?.key
        val name = existingName ?: uniqueMcpName(servers.keys, entry.id)
        mcpConfigStore.upsert(
            name,
            McpServerConfig.Http(
                url = meta.url,
                headers = meta.resolvedHeaders(apiKey),
                description = entry.summary("zh"),
            ),
        )
    }

    private fun download(artifact: MarketArtifact, kind: String, maxBytes: Long): File {
        downloadDir.mkdirs()
        val target = File(downloadDir, "$kind-${artifact.sha256.take(16)}.bin")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(artifact.url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载失败（${response.code}）")
            val body = response.body
            if (body.contentLength() > maxBytes) error("文件超过大小限制")
            target.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) error("文件超过大小限制")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        val sha = digest.digest().toHex()
        if (!sha.equals(artifact.sha256, ignoreCase = true)) {
            target.delete()
            error("文件校验失败")
        }
        if (target.length() != artifact.size) {
            target.delete()
            error("文件大小不符")
        }
        return target
    }

    private fun fetchRemote(): FetchedRegistry {
        val registryBytes = fetchBytes(MarketDefaults.REGISTRY_URL, MarketDefaults.MAX_REGISTRY_BYTES)
        val signatureBytes = fetchBytes(MarketDefaults.REGISTRY_URL + MarketDefaults.SIGNATURE_SUFFIX, 4096)
        return verifyAndParse(registryBytes, signatureBytes)
    }

    private fun fetchSeed(): MarketRegistry {
        val registryBytes = context.assets.open("market/registry.json").use { it.readBytes() }
        val signatureBytes = context.assets.open("market/registry.json.sig").use { it.readBytes() }
        return verifyAndParse(registryBytes, signatureBytes).registry
    }

    private fun fetchBytes(url: String, maxBytes: Long): ByteArray {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body
            if (body.contentLength() > maxBytes) error("response too large")
            val bytes = body.bytes()
            if (bytes.size > maxBytes) error("response too large")
            return bytes
        }
    }

    private fun verifyAndParse(registryBytes: ByteArray, signatureBytes: ByteArray): FetchedRegistry {
        val signatureDer = runCatching { MarketSignature.decodeSignatureText(signatureBytes.decodeToString()) }
            .getOrElse { signatureBytes }
        if (!MarketSignature.verify(registryBytes, signatureDer, MarketDefaults.PUBLIC_KEY_DER_BASE64)) {
            error("市场索引签名校验失败")
        }
        val registry = MarketRegistryParser.parse(registryBytes)
        if (registry.keyId != MarketDefaults.KEY_ID) error("市场索引密钥不匹配")
        return FetchedRegistry(registry, registryBytes, signatureBytes)
    }

    private fun readCached(): CachedRegistry? {
        if (!registryFile.isFile || !signatureFile.isFile) return null
        val parsed = runCatching {
            val registry = verifyAndParse(registryFile.readBytes(), signatureFile.readBytes()).registry
            val fetchedAt = runCatching {
                val root = Json.parseToJsonElement(metaFile.readText()).jsonObject
                (root["fetchedAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            }.getOrDefault(0L)
            CachedRegistry(registry, fetchedAt)
        }.getOrNull() ?: return null
        return parsed
    }

    private fun saveCache(fetched: FetchedRegistry) {
        filesDir.mkdirs()
        registryFile.writeBytes(fetched.registryBytes)
        signatureFile.writeBytes(fetched.signatureBytes)
        metaFile.writeText("{\"fetchedAt\":${System.currentTimeMillis()}}")
    }

    private fun currentRegistry(): MarketRegistry? = (_state.value as? MarketState.Ready)?.registry

    private fun setInstallState(id: String, state: MarketInstallState) {
        _installStates.value = _installStates.value + (id to state)
    }

    private data class CachedRegistry(val registry: MarketRegistry, val fetchedAt: Long)

    private data class FetchedRegistry(val registry: MarketRegistry, val registryBytes: ByteArray, val signatureBytes: ByteArray)

    private companion object {
        const val TAG = "DeepDeepMarket"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
    }
}
