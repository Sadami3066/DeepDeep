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

val verifyPackageBoundaries = tasks.register("verifyPackageBoundaries") {
    group = "verification"
    description = "Checks package-level import rules that module boundaries cannot express."
    val sourceRoots = listOf("app", "runtime", "runtime-api", "shell", "design").associateWith { module ->
        layout.projectDirectory.dir("$module/src/main/kotlin")
    }
    val allowlistFile = layout.projectDirectory.file("gradle/package-boundaries.allow")
    val strictBoundaries = providers.gradleProperty("deepdeep.strictBoundaries").orNull == "true"
    inputs.files(sourceRoots.values)
    inputs.file(allowlistFile)
    inputs.property("strictBoundaries", strictBoundaries)
    doLast {
        val base = "io.github.whywood.deepdeep"
        val allowlist = allowlistFile.asFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
        val observed = mutableSetOf<String>()
        val introduced = mutableListOf<String>()
        fun check(coupling: String, subject: String) {
            if (observed.add(coupling)) {
                if (coupling !in allowlist) introduced += "$subject ($coupling)"
            }
        }
        for ((module, rootRef) in sourceRoots) {
            val root = rootRef.asFile
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val relative = file.relativeTo(root).path.replace('\\', '/')
                val packageName = relative.substringBeforeLast('/', "").replace('/', '.')
                val compositionLayer = packageName == "$base.ui.navigation" ||
                    packageName.startsWith("$base.ui.navigation.") ||
                    packageName == "$base.ui.shell" ||
                    packageName.startsWith("$base.ui.shell.")
                file.readLines().forEach { raw ->
                    val line = raw.trim()
                    if (!line.startsWith("import ")) return@forEach
                    val imported = line.removePrefix("import ").removePrefix("import\t").substringBefore(" as ").trim()
                    if (!imported.startsWith(base)) return@forEach
                    if (module != "app" && (imported == "$base.AppGraph" || imported == "$base.DeepDeepApplication" || imported.startsWith("$base.ui."))) {
                        check("$module/$relative|$imported", "module $module")
                    }
                    if (module == "app" && packageName.startsWith("$base.ui.")) {
                        if (imported == "$base.AppGraph" || imported == "$base.DeepDeepApplication") {
                            check("ui/$relative|$imported", "shell access from ui")
                        }
                        val parts = imported.split('.')
                        if (parts.size > 6 && parts[4] == "ui" && !compositionLayer) {
                            val mine = packageName.removePrefix("$base.ui.").substringBefore('.')
                            val target = parts[5]
                            if (target != mine && target != "common" && target != "navigation") {
                                check("ui/$relative|$imported", "cross-feature import")
                            }
                        }
                        if (imported.startsWith("$base.assistant.")) {
                            check("ui/$relative|$imported", "ui shell access to assistant")
                        }
                        if ((packageName.startsWith("$base.assistant") || packageName.startsWith("$base.floating")) &&
                            imported.startsWith("$base.ui.chat.")
                        ) {
                            check("app/$relative|$imported", "chat access from assistant surface")
                        }
                    }
                }
            }
        }
        logger.lifecycle("Package boundaries: composition layer (ui.navigation/ui.shell) may import features; ui may never import AppGraph/DeepDeepApplication or assistant; assistant/floating may not import ui.chat")
        val stale = allowlist - observed
        if (stale.isNotEmpty()) {
            val message = "Package boundary allowlist has ${stale.size} stale entries (remove them):\n" +
                stale.sorted().joinToString("\n")
            check(!strictBoundaries) { message }
            logger.lifecycle(message)
        }
        check(introduced.isEmpty()) {
            "New package boundary violations (fix the import, or add to gradle/package-boundaries.allow only if intentional):\n" +
                introduced.joinToString("\n")
        }
        logger.lifecycle("Package boundaries verified (${observed.size} tolerated couplings)")
    }
}

gradle.projectsEvaluated {
    val allowed = mapOf(
        ":app" to setOf(":runtime", ":shell", ":design", ":plugin-api"),
        ":runtime" to setOf(":runtime-api"),
        ":runtime-api" to emptySet(),
        ":shell" to emptySet(),
        ":design" to emptySet(),
        ":plugin-api" to setOf(":design"),
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
            logger.lifecycle("Module boundaries verified: app -> runtime/shell/design; runtime -> runtime-api")
        }
    }

    val unitTestTasks = subprojects.mapNotNull { it.tasks.findByName("testDebugUnitTest") ?: it.tasks.findByName("test") }
    val lintTasks = subprojects.mapNotNull { it.tasks.findByName("lintDebug") }
    tasks.named("check") {
        dependsOn(verifyModuleBoundaries)
        dependsOn(verifyPackageBoundaries)
        dependsOn(unitTestTasks)
        dependsOn(lintTasks)
    }
    subprojects {
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(verifyModuleBoundaries)
            dependsOn(verifyPackageBoundaries)
        }
    }
}
