package io.github.whywood.superpi.data.relay

import kotlinx.coroutines.flow.Flow

interface RelayAccountStore {
    val account: Flow<RelayAccount?>

    suspend fun read(): RelayAccount?

    suspend fun token(): String?

    suspend fun save(account: RelayAccount, token: String)

    suspend fun clear()
}

interface RelayEnroller {
    suspend fun fetchCa(origin: String): String?

    suspend fun enroll(
        origin: String,
        caPem: String?,
        deviceName: String,
        pairCode: String?,
        loginSecret: String?,
    ): RelayEnrollment

    fun fingerprintOf(caPem: String): String?
}
