package io.github.whywood.superpi.data.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayConfigCodecTest {
    @Test
    fun roundTripsAccount() {
        val account = RelayAccount(
            origin = "https://203.0.113.10:8443",
            fingerprint = "ab".repeat(32),
            caPem = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n",
            deviceId = "dev-1",
            deviceName = "My phone",
            accountId = "acct-1",
            enrolledAt = 1795000000000,
        )
        assertEquals(account, RelayConfigCodec.decode(RelayConfigCodec.encode(account)))
    }

    @Test
    fun dropsInvalidFingerprintOnDecode() {
        val encoded = RelayConfigCodec.encode(
            RelayAccount(
                origin = "http://127.0.0.1:8443",
                fingerprint = null,
                caPem = null,
                deviceId = "dev-1",
                deviceName = "Phone",
                accountId = "acct-1",
                enrolledAt = 1,
            ),
        )
        val tampered = encoded.replace("\"origin\":\"http://127.0.0.1:8443\"", "\"origin\":\"http://127.0.0.1:8443\",\"fingerprint\":\"nope\"")
        assertNull(RelayConfigCodec.decode(tampered)?.fingerprint)
    }

    @Test
    fun rejectsMissingFieldsAndGarbage() {
        assertNull(RelayConfigCodec.decode(null))
        assertNull(RelayConfigCodec.decode(""))
        assertNull(RelayConfigCodec.decode("not json"))
        assertNull(RelayConfigCodec.decode("""{"origin":"https://relay.test"}"""))
    }
}
