import java.io.File
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
