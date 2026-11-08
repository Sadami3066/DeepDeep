package io.github.whywood.deepdeep.market

internal class MarketAutoUpdatePlanner(
    private val retryIntervalMs: Long = MarketDefaults.AUTO_UPDATE_RETRY_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val attemptedAt = mutableMapOf<String, Long>()

    fun plan(
        entries: List<MarketEntry>,
        installStates: Map<String, MarketInstallState>,
    ): List<MarketEntry> = entries.filter { entry ->
        entry.type != MarketEntryType.Mcp &&
            installStates[entry.id] is MarketInstallState.UpdateAvailable &&
            canAttempt(entry.id)
    }

    fun markAttempt(id: String) {
        attemptedAt[id] = clock()
    }

    fun markInstalled(id: String) {
        attemptedAt.remove(id)
    }

    private fun canAttempt(id: String): Boolean {
        val last = attemptedAt[id] ?: return true
        return clock() - last >= retryIntervalMs
    }
}
