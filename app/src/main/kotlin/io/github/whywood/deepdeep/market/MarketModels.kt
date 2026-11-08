package io.github.whywood.deepdeep.market

enum class MarketEntryType { Plugin, Skill, Mcp }

data class MarketArtifact(
    val url: String,
    val sha256: String,
    val size: Long,
)

data class MarketPluginMeta(
    val apiLevel: Int,
    val minHostVersion: String?,
    val publisherKeyId: String?,
)

data class MarketSkillMeta(
    val license: String?,
    val source: String?,
)

data class MarketMcpMeta(
    val url: String,
    val transport: String,
    val headers: Map<String, String>,
    val authHint: String?,
)

data class MarketEntry(
    val type: MarketEntryType,
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val icon: String?,
    val tags: List<String>,
    val version: String,
    val artifact: MarketArtifact?,
    val plugin: MarketPluginMeta? = null,
    val skill: MarketSkillMeta? = null,
    val mcp: MarketMcpMeta? = null,
)

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
