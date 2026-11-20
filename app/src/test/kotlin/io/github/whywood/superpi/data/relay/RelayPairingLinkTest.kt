package io.github.whywood.superpi.data.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayPairingLinkTest {
    @Test
    fun parsesFullPairingLink() {
        val fingerprint = "ab".repeat(32).chunked(2).joinToString(":").uppercase()
        val parsed = RelayPairingLink.parse("https://203.0.113.10:8443/pair#fp=$fingerprint&code=abcd2345")
        assertEquals("https://203.0.113.10:8443", parsed?.origin)
        assertEquals("ab".repeat(32), parsed?.fingerprint)
        assertEquals("ABCD2345", parsed?.pairCode)
    }

    @Test
    fun acceptsBareOrigin() {
        val parsed = RelayPairingLink.parse("relay.example.com:8443")
        assertEquals("https://relay.example.com:8443", parsed?.origin)
        assertNull(parsed?.fingerprint)
        assertNull(parsed?.pairCode)
    }

    @Test
    fun keepsPlainHttpForDevelopmentRelays() {
        val parsed = RelayPairingLink.parse("http://127.0.0.1:8443")
        assertEquals("http://127.0.0.1:8443", parsed?.origin)
    }

    @Test
    fun readsParamsFromQueryString() {
        val parsed = RelayPairingLink.parse("https://relay.test/pair?code=zzzz9999&fp=${"DE".repeat(32)}")
        assertEquals("ZZZZ9999", parsed?.pairCode)
        assertEquals("de".repeat(32), parsed?.fingerprint)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(RelayPairingLink.parse(""))
        assertNull(RelayPairingLink.parse("   "))
        assertNull(RelayPairingLink.parse("ftp://relay.test"))
        assertNull(RelayPairingLink.parse("https://"))
    }

    @Test
    fun normalizesFingerprints() {
        val hex = "ab".repeat(32)
        assertEquals(hex, RelayPairingLink.normalizeFingerprint(hex))
        assertEquals(hex, RelayPairingLink.normalizeFingerprint(hex.uppercase()))
        assertEquals(hex, RelayPairingLink.normalizeFingerprint(hex.chunked(2).joinToString(":")))
        assertNull(RelayPairingLink.normalizeFingerprint(null))
        assertNull(RelayPairingLink.normalizeFingerprint(""))
        assertNull(RelayPairingLink.normalizeFingerprint("not-a-fingerprint"))
        assertNull(RelayPairingLink.normalizeFingerprint("z".repeat(64)))
        assertNull(RelayPairingLink.normalizeFingerprint("ab".repeat(31)))
    }
}
