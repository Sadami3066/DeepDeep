package io.github.whywood.deepdeep.market

internal data class InstalledContent(
    val plugins: Map<String, String> = emptyMap(),
    val skills: Map<String, String?> = emptyMap(),
    val mcp: Map<String, String> = emptyMap(),
)

internal fun InstalledContent.versionOf(entry: MarketEntry): String? = when (entry.type) {
    MarketEntryType.Plugin -> plugins[entry.id]
    MarketEntryType.Skill -> if (entry.id in skills) skills[entry.id] ?: entry.version else null
    MarketEntryType.Mcp -> if (mcp.values.any { url -> url == entry.mcp?.url }) entry.version else null
}

internal fun uniqueMcpName(taken: Set<String>, id: String): String {
    if (id !in taken) return id
    var index = 2
    while ("$id-$index" in taken) index += 1
    return "$id-$index"
}

internal fun resolveInstallStates(
    entries: List<MarketEntry>,
    installed: InstalledContent,
    previous: Map<String, MarketInstallState>,
): Map<String, MarketInstallState> {
    val next = LinkedHashMap<String, MarketInstallState>(entries.size)
    for (entry in entries) {
        val existing = previous[entry.id]
        if (existing is MarketInstallState.Installing) {
            next[entry.id] = existing
            continue
        }
        val installedVersion = installed.versionOf(entry)
        next[entry.id] = when {
            installedVersion == null -> MarketInstallState.NotInstalled
            installedVersion == entry.version -> MarketInstallState.Installed(installedVersion)
            else -> MarketInstallState.UpdateAvailable(installedVersion)
        }
    }
    return next
}
