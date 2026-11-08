package io.github.whywood.deepdeep.market

import kotlinx.coroutines.flow.StateFlow

interface MarketRepository {
    val state: StateFlow<MarketState>

    val installStates: StateFlow<Map<String, MarketInstallState>>

    fun entries(type: MarketEntryType): List<MarketEntry>

    suspend fun refresh(force: Boolean = false): Boolean

    suspend fun install(entry: MarketEntry): Result<Unit>

    fun installedVersion(entry: MarketEntry): String?
}
