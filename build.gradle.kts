import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val verifyModuleBoundaries = tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Checks app -> runtime/shell/design and core-only dependencies."
}

val piSdkVersion: String = libs.versions.pi.get()
val piSdkChecksum: String = layout.projectDirectory.file("gradle/pi-sdk.sha256").asFile.readText().trim()
val piSdkReleaseUrl = "https://github.com/Sadami3066/pi2kotlin/releases/download/v$piSdkVersion/pi2kotlin-$piSdkVersion-m2.zip"

abstract class SyncPiSdk : DefaultTask() {
    @get:Input abstract val sdkVersion: Property<String>
    @get:Input abstract val checksum: Property<String>
    @get:Input abstract val archiveUrl: Property<String>
    @get:Internal abstract val cacheDirectory: DirectoryProperty
    @get:OutputDirectory abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val version = sdkVersion.get()
        val expected = checksum.get()
        val cacheDir = cacheDirectory.get().asFile
        cacheDir.mkdirs()
        val archive = java.io.File(cacheDir, "pi2kotlin-$version-m2.zip")
        if (!archive.isFile || archive.sha256() != expected) {
            val source = archiveUrl.get()
            logger.lifecycle("Fetching $source")
            if (source.startsWith("http://") || source.startsWith("https://")) {
                java.net.URI(source).toURL().openStream().use { input ->
                    archive.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                java.io.File(source).copyTo(archive, overwrite = true)
            }
        }
        check(archive.sha256() == expected) {
            "pi2kotlin $version m2 zip checksum mismatch: expected $expected, got ${archive.sha256()}"
        }
        val output = repositoryDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = java.io.File(output, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { fileOutput -> zip.copyTo(fileOutput) }
                }
            }
        }
        check(java.io.File(output, "dev/pi/pi-coding-agent-android/$version/pi-coding-agent-android-$version.pom").isFile) {
            "pi2kotlin $version m2 zip does not contain the expected dev.pi artifacts"
        }
    }

    private fun java.io.File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

val syncPiSdk = tasks.register<SyncPiSdk>("syncPiSdk") {
    group = "build setup"
    description = "Fetches the pi2kotlin $piSdkVersion Maven repository into build/pi-sdk-m2."
    sdkVersion.set(piSdkVersion)
    checksum.set(piSdkChecksum)
    archiveUrl.set(
        providers.gradleProperty("piSdkLocalZip")
            .map { java.io.File(it).absolutePath }
            .orElse(piSdkReleaseUrl)
    )
    cacheDirectory.set(layout.buildDirectory.dir("pi-sdk-cache"))
    repositoryDirectory.set(layout.buildDirectory.dir("pi-sdk-m2"))
}

allprojects {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("syncPiSdk"))
    }
}

gradle.projectsEvaluated {
    val allowed = mapOf(
        ":app" to setOf(":runtime", ":shell", ":design"),
        ":runtime" to emptySet(),
        ":shell" to emptySet(),
        ":design" to emptySet(),
    )
    val undeclared = subprojects.map { it.path }.filterNot { it in allowed }
    check(undeclared.isEmpty()) { "Modules missing from the boundary table: ${undeclared.joinToString()}" }
    val violations = subprojects.flatMap { module ->
        val targets = allowed.getValue(module.path)
        module.configurations.flatMap { configuration ->
            configuration.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>().mapNotNull { dependency ->
                val target = dependency.path
                when {
                    target == module.path -> null
                    target in targets -> null
                    else -> "${module.path} -> $target"
                }
            }
        }
    }.distinct()
    verifyModuleBoundaries.configure {
        inputs.property("violations", violations)
        doLast {
            check(violations.isEmpty()) { "Invalid module dependencies: ${violations.joinToString()}" }
            logger.lifecycle("Module boundaries verified: app -> runtime/shell/design")
        }
    }

    val unitTestTasks = subprojects.map { it.tasks.named("testDebugUnitTest") }
    val lintTasks = subprojects.mapNotNull { it.tasks.findByName("lintDebug") }
    tasks.named("check") {
        dependsOn(verifyModuleBoundaries)
        dependsOn(unitTestTasks)
        dependsOn(lintTasks)
    }
    subprojects {
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(verifyModuleBoundaries)
        }
    }
}
