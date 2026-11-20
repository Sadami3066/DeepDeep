package io.github.whywood.superpi.data.relay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayProtocolTest {
    @Test
    fun buildsEnrollRequestWithEscaping() {
        val body = RelayProtocol.enrollRequest(
            deviceName = "My \"phone\"",
            platform = "android",
            appVersion = "2.0.1",
            pairCode = "ABCD2345",
            loginSecret = null,
        )
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("My \"phone\"", json["deviceName"]?.jsonPrimitive?.content)
        assertEquals("android", json["platform"]?.jsonPrimitive?.content)
        assertEquals("ABCD2345", json["pairCode"]?.jsonPrimitive?.content)
        assertNull(json["loginSecret"])
    }

    @Test
    fun omitsBlankCredentials() {
        val body = RelayProtocol.enrollRequest("Phone", "android", "2.0.1", pairCode = " ", loginSecret = "")
        val json = Json.parseToJsonElement(body).jsonObject
        assertNull(json["pairCode"])
        assertNull(json["loginSecret"])
    }

    @Test
    fun parsesEnrollmentWithEntries() {
        val enrollment = RelayProtocol.parseEnrollment(
            """
            {"deviceId":"dev-1","token":"tok","accountId":"acct","fingerprint":"${"AB".repeat(32)}",
             "entries":[{"kind":"lan","origin":"https://192.168.1.5:8443"},{"kind":"public","origin":"https://203.0.113.10:8443"}]}
            """.trimIndent(),
        )
        assertEquals("dev-1", enrollment?.deviceId)
        assertEquals("tok", enrollment?.token)
        assertEquals("acct", enrollment?.accountId)
        assertEquals("ab".repeat(32), enrollment?.fingerprint)
        assertEquals(2, enrollment?.entries?.size)
        assertEquals(RelayEntryKind.Lan, enrollment?.entries?.get(0)?.kind)
        assertEquals("https://203.0.113.10:8443", enrollment?.entries?.get(1)?.origin)
    }

    @Test
    fun rejectsIncompleteEnrollment() {
        assertNull(RelayProtocol.parseEnrollment("""{"deviceId":"dev-1"}"""))
        assertNull(RelayProtocol.parseEnrollment("not json"))
        assertNull(RelayProtocol.parseEnrollment(""))
    }

    @Test
    fun parsesPairCodeResponse() {
        val pairCode = RelayProtocol.parsePairCode(
            """{"code":"FELLJ4E5","expiresAt":1795000000000,"entries":[],"fingerprint":null}""",
        )
        assertEquals("FELLJ4E5", pairCode?.code)
        assertEquals(1795000000000L, pairCode?.expiresAt)
        assertTrue(pairCode?.entries?.isEmpty() == true)
        assertNull(pairCode?.fingerprint)
    }

    @Test
    fun readsApiErrorMessages() {
        assertEquals("invalid login secret", RelayProtocol.parseErrorMessage("""{"code":"unauthorized","message":"invalid login secret"}"""))
        assertEquals("unauthorized", RelayProtocol.parseErrorMessage("""{"code":"unauthorized"}"""))
        assertNull(RelayProtocol.parseErrorMessage("oops"))
    }
}
