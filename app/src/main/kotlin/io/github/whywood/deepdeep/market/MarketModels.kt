package io.github.whywood.deepdeep.market

enum class MarketEntryType { Plugin, Skill, Mcp }

data class LocalizedText(
    val values: Map<String, String>,
) {
    fun resolve(language: String): String {
        val key = language.lowercase().substringBefore('-').substringBefore('_')
        return values[key] ?: values["zh"] ?: values.values.firstOrNull().orEmpty()
    }

    val any: String get() = resolve("zh")
}

fun LocalizedText.resolveOrDefault(): String = values["zh"] ?: values.values.firstOrNull().orEmpty()

data class MarketArtifact(
    val url: String,
    val sha256: String,
    val size: Long,
)

enum class MarketRequiredAccess { Normal, Adb, Root }

data class MarketPluginMeta(
    val apiLevel: Int,
    val minHostVersion: String?,
    val publisherKeyId: String?,
    val publisherName: String? = null,
    val requiredAccess: MarketRequiredAccess = MarketRequiredAccess.Normal,
    val previewUrls: List<String> = emptyList(),
)

data class MarketSkillMeta(
    val license: String?,
    val source: String?,
)

enum class MarketMcpAuthType { None, ApiKey, OAuth }

data class MarketMcpAuth(
    val type: MarketMcpAuthType,
    val header: String? = null,
    val prefix: String? = null,
    val placeholder: LocalizedText? = null,
    val helpUrl: String? = null,
)

data class MarketMcpMeta(
    val url: String,
    val transport: String,
    val headers: Map<String, String>,
    val authHint: LocalizedText?,
    val auth: MarketMcpAuth? = null,
)

data class MarketEntry(
    val type: MarketEntryType,
    val id: String,
    val name: LocalizedText,
    val summary: LocalizedText,
    val description: LocalizedText,
    val icon: String?,
    val tags: Map<String, List<String>>,
    val version: String,
    val artifact: MarketArtifact?,
    val plugin: MarketPluginMeta? = null,
    val skill: MarketSkillMeta? = null,
    val mcp: MarketMcpMeta? = null,
) {
    fun name(language: String): String = name.resolve(language)

    fun summary(language: String): String = summary.resolve(language)

    fun description(language: String): String = description.resolve(language)

    fun tags(language: String): List<String> =
        tags[language.lowercase().substringBefore('-')] ?: tags["zh"] ?: tags.values.firstOrNull().orEmpty()
}

data class MarketRegistry(
    val schemaVersion: Int,
    val generatedAt: String,
    val keyId: String,
    val entries: List<MarketEntry>,
)

enum class MarketSource { Network, Cache, Seed }

sealed interface MarketState {
    data object Loading : MarketState

    data class Ready(
        val registry: MarketRegistry,
        val source: MarketSource,
        val fetchedAt: Long,
    ) : MarketState

    data class Failed(val message: String) : MarketState
}

sealed interface MarketInstallState {
    data object NotInstalled : MarketInstallState

    data class Installed(val version: String) : MarketInstallState

    data class UpdateAvailable(val installedVersion: String) : MarketInstallState

    data class Installing(val progress: Int?) : MarketInstallState

    data class Failed(val message: String) : MarketInstallState
}

internal fun MarketMcpMeta.resolvedHeaders(apiKey: String?): Map<String, String> {
    val resolved = headers.toMutableMap()
    val auth = auth ?: return resolved
    if (auth.type == MarketMcpAuthType.ApiKey) {
        val token = apiKey?.trim().orEmpty()
        if (token.isEmpty()) error("需要填写访问令牌")
        val header = auth.header ?: error("连接器缺少授权头配置")
        resolved[header] = (auth.prefix.orEmpty()) + token
    }
    return resolved
}
