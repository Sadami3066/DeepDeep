import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.whywood.deepdeep.pluginsample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.whywood.deepdeep.pluginsample"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(project(":plugin-api"))
    compileOnly(platform(libs.compose.bom))
    compileOnly(libs.compose.ui)
    compileOnly(libs.compose.foundation)
    compileOnly(libs.compose.material3)
    compileOnly(libs.coroutines.core)
    compileOnly(libs.kotlinx.serialization.json)
}

val assemblePlugin = tasks.register("assemblePlugin") {
    group = "plugin"
    description = "Repacks the debug APK into a .ddp plugin bundle."
    dependsOn("assembleDebug")
    val apk = layout.buildDirectory.file("outputs/apk/debug/plugin-sample-debug.apk")
    val assets = layout.projectDirectory.dir("src/main/assets")
    val output = layout.buildDirectory.dir("plugin")
    inputs.file(apk)
    inputs.dir(assets)
    outputs.dir(output)
    doLast {
        val source = apk.get().asFile
        val directory = output.get().asFile
        directory.mkdirs()
        val target = File(directory, "plugin-sample.ddp")
        ZipOutputStream(target.outputStream().buffered()).use { bundle ->
            ZipFile(source).use { apkFile ->
                apkFile.entries().asSequence()
                    .filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        bundle.putNextEntry(ZipEntry(entry.name))
                        apkFile.getInputStream(entry).use { it.copyTo(bundle) }
                        bundle.closeEntry()
                    }
            }
            val root = assets.asFile
            root.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.path }
                .forEach { file ->
                    val name = "assets/" + file.relativeTo(root).invariantSeparatorsPath
                    bundle.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(bundle) }
                    bundle.closeEntry()
                }
        }
    }
}

val signingDir = layout.projectDirectory.dir("signing")
val unsignedPlugin = layout.buildDirectory.file("plugin/plugin-sample.ddp")
val signedPlugin = layout.buildDirectory.file("plugin/plugin-sample-signed.ddp")

val signPlugin = tasks.register("signPlugin") {
    group = "plugin"
    description = "Signs the assembled .ddp with the committed dev Ed25519 test key; test bundles only."
    dependsOn(assemblePlugin)
    inputs.file(unsignedPlugin)
    inputs.file(signingDir.file("dev-ed25519.pem"))
    inputs.file(signingDir.file("dev-ed25519.pub"))
    outputs.file(signedPlugin)
    doLast {
        val source = unsignedPlugin.get().asFile
        check(source.isFile) { "missing ${source.absolutePath}; run assemblePlugin first" }
        ZipFile(source).use { zip ->
            check(zip.getEntry("assets/plugin.json") != null) { "plugin.json must be stored at assets/plugin.json" }
            check(zip.entries().asSequence().any { it.name.matches(Regex("classes[0-9]*\\.dex")) }) {
                "plugin bundle must contain at least one root classes*.dex"
            }
        }
        val keyPair = readEd25519KeyPair(
            signingDir.file("dev-ed25519.pem").asFile,
            signingDir.file("dev-ed25519.pub").asFile,
        )
        val keyId = ed25519KeyId(keyPair.public)
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update(canonicalEntryDigest(source))
            sign()
        }
        val target = signedPlugin.get().asFile
        target.parentFile.mkdirs()
        if (target.exists()) target.delete()
        copyZipAddingEntry(
            source,
            target,
            SIGNATURE_ENTRY,
            buildSignatureJson(
                keyId = keyId,
                publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
                signature = Base64.getEncoder().encodeToString(signature),
            ).toByteArray(StandardCharsets.UTF_8),
        )
        check(verifySignedBundle(target, keyPair.public, keyId)) { "signed bundle failed self-verification" }
        logger.lifecycle("Signed ${target.absolutePath} (keyId=$keyId, sha256=${target.sha256Hex()})")
        logger.lifecycle("signing/dev-ed25519.pem is committed test material; never use it for distributed plugins.")
    }
}

private val SIGNATURE_ENTRY = "META-INF/plugin-signature.json"
private val FIXED_ENTRY_TIME = 315532800000L

private fun readEd25519KeyPair(privateFile: File, publicFile: File): KeyPair {
    val factory = KeyFactory.getInstance("Ed25519")
    val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(decodePem(privateFile.readText())))
    val publicKey = factory.generatePublic(X509EncodedKeySpec(decodePem(publicFile.readText())))
    return KeyPair(publicKey, privateKey)
}

private fun decodePem(text: String): ByteArray {
    val body = text.lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
    return Base64.getMimeDecoder().decode(body)
}

private fun ed25519KeyId(publicKey: PublicKey): String =
    "ed25519:" + MessageDigest.getInstance("SHA-256").digest(publicKey.encoded).joinToString("") { "%02x".format(it) }

private fun canonicalEntryDigest(archive: File): ByteArray {
    val entries = ZipFile(archive).use { zip ->
        val names = mutableSetOf<String>()
        zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .filterNot { it.name.startsWith("META-INF/") }
            .map { entry ->
                validateZipEntryName(entry.name)
                check(names.add(entry.name)) { "duplicate zip entry: ${entry.name}" }
                Triple(entry.name, entry.size, zip.getInputStream(entry).use(::sha256Hex))
            }
            .sortedWith(Comparator { left, right -> compareUtf8(left.first, right.first) })
            .toList()
    }
    val text = buildString {
        entries.forEach { (name, size, sha256) -> append(sha256).append(' ').append(size).append(' ').append(name).append('\n') }
    }
    return text.toByteArray(StandardCharsets.UTF_8)
}

private fun validateZipEntryName(name: String) {
    check(name.isNotBlank()) { "blank zip entry name" }
    check(!name.startsWith("/")) { "absolute zip entry name: $name" }
    check(!name.contains("..")) { "zip entry name must not contain '..': $name" }
    check('\\' !in name) { "zip entry name must use forward slashes: $name" }
    check('\u0000' !in name) { "zip entry name must not contain NUL: $name" }
    check(':' !in name) { "zip entry name must not contain ':': $name" }
}

private fun compareUtf8(left: String, right: String): Int {
    val a = left.toByteArray(StandardCharsets.UTF_8)
    val b = right.toByteArray(StandardCharsets.UTF_8)
    val limit = minOf(a.size, b.size)
    for (index in 0 until limit) {
        val difference = (a[index].toInt() and 0xFF) - (b[index].toInt() and 0xFF)
        if (difference != 0) return difference
    }
    return a.size - b.size
}

private fun copyZipAddingEntry(source: File, target: File, entryName: String, payload: ByteArray) {
    ZipOutputStream(target.outputStream().buffered()).use { output ->
        ZipFile(source).use { zip ->
            zip.entries().asSequence().sortedBy { it.name }.forEach { entry ->
                val copy = ZipEntry(entry.name)
                copy.time = FIXED_ENTRY_TIME
                output.putNextEntry(copy)
                if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(output) }
                output.closeEntry()
            }
        }
        val signature = ZipEntry(entryName)
        signature.time = FIXED_ENTRY_TIME
        output.putNextEntry(signature)
        output.write(payload)
        output.closeEntry()
    }
}

private fun verifySignedBundle(archive: File, publicKey: PublicKey, expectedKeyId: String): Boolean {
    val json = ZipFile(archive).use { zip ->
        val entry = zip.getEntry(SIGNATURE_ENTRY) ?: return false
        zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }
    if (jsonField(json, "algorithm") != "Ed25519") return false
    if (jsonField(json, "keyId") != expectedKeyId) return false
    val signature = runCatching {
        Base64.getDecoder().decode(jsonField(json, "signature").orEmpty())
    }.getOrNull() ?: return false
    return Signature.getInstance("Ed25519").run {
        initVerify(publicKey)
        update(canonicalEntryDigest(archive))
        verify(signature)
    }
}

private fun buildSignatureJson(keyId: String, publicKey: String, signature: String): String = buildString {
    append("{\n")
    append("  \"algorithm\": \"Ed25519\",\n")
    append("  \"keyId\": \"").append(keyId).append("\",\n")
    append("  \"publicKey\": \"").append(publicKey).append("\",\n")
    append("  \"signature\": \"").append(signature).append("\"\n")
    append("}\n")
}

private fun jsonField(json: String, name: String): String? =
    Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

private fun sha256Hex(input: java.io.InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun File.sha256Hex(): String = inputStream().use(::sha256Hex)
