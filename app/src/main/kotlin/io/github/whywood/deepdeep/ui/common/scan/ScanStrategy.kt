package io.github.whywood.deepdeep.ui.common.scan

import java.net.URI

internal sealed interface ScanExtra {
    data class Text(val value: String) : ScanExtra
    data class Flag(val value: Boolean) : ScanExtra
    data class Number(val value: Int) : ScanExtra
}

internal sealed interface ScanLaunch {
    data class Uri(val uri: String) : ScanLaunch

    data class Component(
        val activity: String,
        val action: String?,
        val extras: Map<String, ScanExtra>,
        val flags: List<String>,
    ) : ScanLaunch
}

internal data class ScanStrategy(
    val packageName: String,
    val hosts: List<String>,
    val schemes: List<String>,
    val contains: List<String>,
    val regexes: List<Regex>,
    val priority: Int,
    val fallbackMain: Boolean,
    val launch: ScanLaunch,
    val note: String?,
)

internal sealed interface ScanPrimary {
    data class AppScan(val strategy: ScanStrategy) : ScanPrimary
    data class Browse(val uri: String) : ScanPrimary
    data object Copy : ScanPrimary
}

internal data class ScanDecision(
    val raw: String,
    val primary: ScanPrimary,
)

internal object ScanMatcher {
    private val httpSchemes = setOf("http", "https")

    fun resolve(raw: String, strategies: List<ScanStrategy>, installed: (String) -> Boolean): ScanDecision {
        val text = raw.trim()
        val match = bestMatch(text, strategies, installed)
        return ScanDecision(
            raw = text,
            primary = when {
                match != null -> ScanPrimary.AppScan(match)
                isHttp(text) -> ScanPrimary.Browse(text)
                else -> ScanPrimary.Copy
            },
        )
    }

    fun bestMatch(text: String, strategies: List<ScanStrategy>, installed: (String) -> Boolean): ScanStrategy? {
        if (text.isEmpty()) return null
        val uri = parse(text)
        var best: ScanStrategy? = null
        var bestScore = 0
        var bestPriority = Int.MIN_VALUE
        strategies.forEach { strategy ->
            if (!installed(strategy.packageName)) return@forEach
            val score = score(strategy, uri, text)
            if (score == 0) return@forEach
            if (score > bestScore || (score == bestScore && strategy.priority > bestPriority)) {
                best = strategy
                bestScore = score
                bestPriority = strategy.priority
            }
        }
        return best
    }

    fun isHttp(raw: String): Boolean {
        val scheme = parse(raw)?.scheme
        if (scheme != null) return scheme in httpSchemes
        val lowered = raw.lowercase()
        return lowered.startsWith("http://") || lowered.startsWith("https://")
    }

    private fun score(strategy: ScanStrategy, uri: ParsedUri?, text: String): Int {
        val scheme = uri?.scheme
        if (scheme != null && strategy.schemes.contains(scheme)) return 100
        val host = uri?.host
        if (host != null) {
            val suffix = strategy.hosts
                .filter { host == it || host.endsWith(".$it") }
                .maxByOrNull { it.length }
            if (suffix != null) return 90 + suffix.length.coerceAtMost(9)
        }
        if (strategy.regexes.any { it.containsMatchIn(text) }) return 80
        val lowered = text.lowercase()
        if (strategy.contains.any { it.isNotEmpty() && lowered.contains(it) }) return 70
        return 0
    }

    private fun parse(text: String): ParsedUri? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        return ParsedUri(scheme, uri.host?.lowercase())
    }

    private data class ParsedUri(val scheme: String, val host: String?)
}
