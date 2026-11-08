package io.github.whywood.deepdeep.market

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketSignatureTest {
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keys.public.encoded)

    @Test
    fun verifiesSignatureOverRawBytes() {
        val registry = """{"schemaVersion":2,"entries":[]}""".encodeToByteArray()
        val signature = sign(registry)

        assertTrue(MarketSignature.verify(registry, signature, publicKeyBase64))
    }

    @Test
    fun rejectsTamperedBytes() {
        val registry = """{"schemaVersion":2,"entries":[]}""".encodeToByteArray()
        val signature = sign(registry)
        val tampered = """{"schemaVersion":2,"entries":[{}]}""".encodeToByteArray()

        assertFalse(MarketSignature.verify(tampered, signature, publicKeyBase64))
    }

    @Test
    fun rejectsSignatureFromAnotherKey() {
        val registry = """{"schemaVersion":2,"entries":[]}""".encodeToByteArray()
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(other.private)
            update(registry)
            sign()
        }

        assertFalse(MarketSignature.verify(registry, signature, publicKeyBase64))
    }

    private fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(keys.private)
        update(bytes)
        sign()
    }
}
