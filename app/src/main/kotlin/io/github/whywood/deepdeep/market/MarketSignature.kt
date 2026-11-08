package io.github.whywood.deepdeep.market

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal object MarketSignature {
    fun verify(registryBytes: ByteArray, signatureDer: ByteArray, publicKeyDerBase64: String): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey(publicKeyDerBase64))
        verifier.update(registryBytes)
        return verifier.verify(signatureDer)
    }

    fun decodeSignatureText(text: String): ByteArray = Base64.getMimeDecoder().decode(text.trim())

    fun publicKey(publicKeyDerBase64: String): PublicKey {
        val der = Base64.getDecoder().decode(publicKeyDerBase64)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }
}
