package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.MIN_SUPPORTED_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class VerifiedPublisher(
    val keyId: String,
    val name: String? = null,
    val publicKey: String,
)

internal data class PackageDigests(
    val entryDigest: String,
    val bundleSha256: String,
    val size: Long,
)

internal enum class VerificationCode {
    SIGNATURE_MISSING,
    SIGNATURE_MALFORMED,
    ALGORITHM_UNSUPPORTED,
    KEY_ID_MISMATCH,
    SIGNATURE_INVALID,
    MANIFEST_MALFORMED,
    MANIFEST_INVALID,
    API_LEVEL_UNSUPPORTED,
}

internal sealed interface VerificationResult {
    data class Ok(
        val manifest: PluginManifest,
        val publisher: VerifiedPublisher,
        val digests: PackageDigests,
    ) : VerificationResult

    data class Rejected(
        val code: VerificationCode,
        val reason: String,
        val declaredApiLevel: Int? = null,
    ) : VerificationResult
}

internal object PluginPackageVerifier {
    fun verify(
        pkg: DdpPackage,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): VerificationResult {
        val signatureEntry = pkg.signatureEntry
            ?: return reject(VerificationCode.SIGNATURE_MISSING, "external plugin requires ${DdpSigning.SIGNATURE_ENTRY}")
        val signatureJson = try {
            Json.parseToJsonElement(pkg.readEntryBytes(signatureEntry.path).toString(Charsets.UTF_8)).jsonObject
        } catch (error: Exception) {
            return reject(VerificationCode.SIGNATURE_MALFORMED, "invalid plugin signature file: ${error.message}")
        }
        val algorithm = signatureJson.string("algorithm")
            ?: return reject(VerificationCode.SIGNATURE_MALFORMED, "plugin signature is missing algorithm")
        if (!algorithm.equals(DdpSigning.ALGORITHM, ignoreCase = true)) {
            return reject(VerificationCode.ALGORITHM_UNSUPPORTED, "unsupported signature algorithm: $algorithm")
        }
        val keyId = signatureJson.string("keyId")
            ?: return reject(VerificationCode.SIGNATURE_MALFORMED, "plugin signature is missing keyId")
        val publicKeyBase64 = signatureJson.string("publicKey")
            ?: return reject(VerificationCode.SIGNATURE_MALFORMED, "plugin signature is missing publicKey")
        val signatureBase64 = signatureJson.string("signature")
            ?: return reject(VerificationCode.SIGNATURE_MALFORMED, "plugin signature is missing signature")
        val publicKeyDer = DdpSigning.decodeBase64(publicKeyBase64)
            ?: return reject(VerificationCode.SIGNATURE_MALFORMED, "invalid Ed25519 public key")
        val signature = DdpSigning.decodeBase64(signatureBase64)
            ?: return reject(VerificationCode.SIGNATURE_MALFORMED, "invalid base64 signature")
        if (signature.size != ED25519_SIGNATURE_SIZE) {
            return reject(VerificationCode.SIGNATURE_MALFORMED, "invalid Ed25519 signature length: ${signature.size}")
        }
        val derivedKeyId = DdpSigning.keyId(publicKeyDer)
        if (!derivedKeyId.equals(keyId, ignoreCase = true)) {
            return reject(VerificationCode.KEY_ID_MISMATCH, "signature keyId does not match its public key")
        }
        val payload = DdpSigning.canonicalDigestBytes(pkg.entries)
        if (!DdpSigning.verifySignatureDer(publicKeyDer, payload, signature)) {
            return reject(VerificationCode.SIGNATURE_INVALID, "plugin signature does not match package contents")
        }
        val manifestJson = try {
            pkg.manifestJson()
        } catch (error: Exception) {
            return reject(VerificationCode.MANIFEST_MALFORMED, error.message ?: "invalid plugin manifest")
        }
        val manifest = when (
            val decoded = PluginManifestCodec.decode(
                manifestJson,
                hostApiLevel = hostApiLevel,
                hostVersion = hostVersion,
            )
        ) {
            is PluginManifestCodec.Result.Valid -> decoded.manifest
            is PluginManifestCodec.Result.Invalid -> {
                val apiLevelIssue = decoded.issues.firstOrNull { it.code == PluginValidation.Code.API_LEVEL_UNSUPPORTED }
                if (apiLevelIssue != null && decoded.manifest.apiLevel < MIN_SUPPORTED_API_LEVEL) {
                    return reject(
                        VerificationCode.API_LEVEL_UNSUPPORTED,
                        apiLevelIssue.message,
                        decoded.manifest.apiLevel,
                    )
                }
                return reject(
                    VerificationCode.MANIFEST_INVALID,
                    decoded.issues.joinToString("; ") { "${it.field}: ${it.message}" },
                )
            }
        }
        return VerificationResult.Ok(
            manifest = manifest,
            publisher = VerifiedPublisher(
                keyId = derivedKeyId,
                name = manifest.publisher?.name?.takeIf { it.isNotBlank() },
                publicKey = DdpSigning.encodeBase64(publicKeyDer),
            ),
            digests = PackageDigests(
                entryDigest = DdpSigning.sha256Hex(payload),
                bundleSha256 = pkg.bundleSha256,
                size = pkg.size,
            ),
        )
    }

    private fun reject(
        code: VerificationCode,
        reason: String,
        declaredApiLevel: Int? = null,
    ): VerificationResult.Rejected = VerificationResult.Rejected(code, reason, declaredApiLevel)

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private const val ED25519_SIGNATURE_SIZE = 64
}
