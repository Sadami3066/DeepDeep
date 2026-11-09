package io.github.whywood.deepdeep.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteBrowserPairingTest {
    @Test
    fun mapsStatusToFailure() {
        assertEquals(RemoteGatewayFailure.Unauthorized, pairFailureFor(401))
        assertEquals(RemoteGatewayFailure.Forbidden, pairFailureFor(403))
        assertEquals(RemoteGatewayFailure.DeviceLimit, pairFailureFor(409))
        assertEquals(RemoteGatewayFailure.RateLimited, pairFailureFor(429))
        assertEquals(RemoteGatewayFailure.BadResponse, pairFailureFor(400))
        assertEquals(RemoteGatewayFailure.BadResponse, pairFailureFor(500))
    }

    @Test
    fun parsesPairedResponse() {
        val pairing = pairingFromResponse(
            201,
            """{"paired":true,"deviceId":"d1","sessionExpiresAt":1760000000000}""",
        )
        assertEquals("d1", pairing.deviceId)
        assertEquals(1760000000000L, pairing.sessionExpiresAt)
    }

    @Test
    fun mapsExpiredTokenToUnauthorized() {
        val error = assertThrows(RemoteGatewayException::class.java) {
            pairingFromResponse(401, """{"error":"authentication_failed"}""")
        }
        assertEquals(RemoteGatewayFailure.Unauthorized, error.failure)
    }

    @Test
    fun mapsRejectedPairingToForbidden() {
        val error = assertThrows(RemoteGatewayException::class.java) {
            pairingFromResponse(403, """{"error":"forbidden"}""")
        }
        assertEquals(RemoteGatewayFailure.Forbidden, error.failure)
    }

    @Test
    fun unpairedBodyIsBadResponse() {
        val error = assertThrows(RemoteGatewayException::class.java) {
            pairingFromResponse(200, """{"paired":false}""")
        }
        assertEquals(RemoteGatewayFailure.BadResponse, error.failure)
    }

    @Test
    fun nonJsonBodyIsBadResponse() {
        val error = assertThrows(RemoteGatewayException::class.java) {
            pairingFromResponse(200, "<html></html>")
        }
        assertEquals(RemoteGatewayFailure.BadResponse, error.failure)
    }
}
