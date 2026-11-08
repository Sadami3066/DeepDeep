package io.github.whywood.deepdeep.ui.common.market

import io.github.whywood.deepdeep.market.MarketDefaults
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketInstallState
import java.util.Locale

internal enum class MarketEntryStatus { Install, Installed, Update, Installing, Failed }

internal fun filterMarketEntries(entries: List<MarketEntry>, query: String): List<MarketEntry> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return entries
    return entries.filter { entry ->
        entry.name.contains(keyword, ignoreCase = true) ||
            entry.summary.contains(keyword, ignoreCase = true) ||
            entry.tags.any { it.contains(keyword, ignoreCase = true) }
    }
}

internal fun marketEntryStatus(
    entry: MarketEntry,
    installState: MarketInstallState?,
    installedVersion: String?,
): MarketEntryStatus = when (installState) {
    is MarketInstallState.Installing -> MarketEntryStatus.Installing
    is MarketInstallState.Installed -> MarketEntryStatus.Installed
    is MarketInstallState.UpdateAvailable -> MarketEntryStatus.Update
    is MarketInstallState.Failed -> MarketEntryStatus.Failed
    MarketInstallState.NotInstalled, null -> when {
        installedVersion == null -> MarketEntryStatus.Install
        installedVersion == entry.version -> MarketEntryStatus.Installed
        else -> MarketEntryStatus.Update
    }
}

internal fun formatMarketSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val text = if (value >= 100.0) {
        String.format(Locale.ROOT, "%.0f", value)
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
    val trimmed = if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    return "$trimmed ${units[index]}"
}

internal fun marketIconUrl(icon: String?): String? {
    val path = icon?.trim().orEmpty()
    if (path.isEmpty()) return null
    return MarketDefaults.PAGE_BASE_URL + path.trimStart('/')
}
