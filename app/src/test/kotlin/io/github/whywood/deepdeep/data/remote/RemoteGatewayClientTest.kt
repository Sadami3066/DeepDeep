package io.github.whywood.deepdeep.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteGatewayClientTest {
    @Test
    fun buildsPairRequestBodyWithEscaping() {
        val body = buildPairRequestBody("to\"ken\\", "手机\"X")
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("to\"ken\\", json["token"]?.jsonPrimitive?.content)
        assertEquals("手机\"X", json["label"]?.jsonPrimitive?.content)
    }

    @Test
    fun buildsDeviceTokenRequestBody() {
        val body = buildDeviceTokenRequestBody("token-1")
        assertEquals("token-1", Json.parseToJsonElement(body).jsonObject["deviceToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesPairingPayloadWithIsoTimestamps() {
        val payload = parsePairingPayload(
            """
            {"instanceId":"i1","installationId":"m1","deviceId":"d1","deviceToken":"dt","deviceExpiresAt":"2026-11-08T10:00:00Z",
             "sessionToken":"st","csrfToken":"ct","sessionExpiresAt":1760000000000}
            """.trimIndent(),
        )
        assertEquals("i1", payload?.instanceId)
        assertEquals("m1", payload?.installationId)
        assertEquals("d1", payload?.deviceId)
        assertEquals("dt", payload?.deviceToken)
        assertEquals(java.time.Instant.parse("2026-11-08T10:00:00Z").toEpochMilli(), payload?.deviceExpiresAt)
        assertEquals(1760000000000L, payload?.sessionExpiresAt)
    }

    @Test
    fun rejectsIncompletePairingPayload() {
        assertNull(parsePairingPayload("""{"instanceId":"i1"}"""))
        assertNull(parsePairingPayload("not json"))
        assertNull(parsePairingPayload(""))
    }

    @Test
    fun parsesRenewalAndProbePayloads() {
        val renewal = parseRenewalPayload(
            """{"instanceId":"i1","installationId":"m1","deviceId":"d1","sessionToken":"st","csrfToken":"ct","sessionExpiresAt":5}""",
        )
        assertEquals("m1", renewal?.installationId)
        assertEquals("st", renewal?.sessionToken)
        assertEquals(5L, renewal?.sessionExpiresAt)

        val probe = parseProbePayload(
            """{"instanceId":"i1","installationId":"m1","deviceId":"d1","deviceExpiresAt":"2027-01-01T00:00:00Z"}""",
        )
        assertEquals("m1", probe?.installationId)
        assertEquals("d1", probe?.deviceId)
        assertEquals(java.time.Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(), probe?.deviceExpiresAt)
    }

    @Test
    fun mapsHttpFailures() {
        assertEquals(RemoteGatewayFailure.Unauthorized, mapFailure(401, """{"error":"authentication_failed"}"""))
        assertEquals(RemoteGatewayFailure.Unauthorized, mapFailure(401, """{"error":"device_revoked"}"""))
        assertEquals(RemoteGatewayFailure.Forbidden, mapFailure(403, """{"error":"forbidden"}"""))
        assertEquals(RemoteGatewayFailure.DeviceLimit, mapFailure(409, """{"error":"device_limit"}"""))
        assertEquals(RemoteGatewayFailure.RateLimited, mapFailure(429, """{"error":"rate_limited"}"""))
        assertEquals(RemoteGatewayFailure.BadResponse, mapFailure(500, "oops"))
        assertEquals(RemoteGatewayFailure.BadResponse, mapFailure(418, """{"error":"teapot"}"""))
    }

    @Test
    fun parsesTimestamps() {
        assertEquals(1200L, parseTimestamp("1200"))
        assertEquals(java.time.Instant.parse("2026-11-08T10:00:00Z").toEpochMilli(), parseTimestamp("2026-11-08T10:00:00Z"))
        assertNull(parseTimestamp(null))
        assertNull(parseTimestamp(""))
        assertNull(parseTimestamp("yesterday"))
    }
}
