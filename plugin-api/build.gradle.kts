plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

val pluginApiVersion: String = providers.gradleProperty("pluginApiVersion").getOrElse("2.0.0")

android {
    namespace = "io.github.whywood.superpi.plugin.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(project(":design"))

    testImplementation(libs.junit)
}

val publishGroup = providers.gradleProperty("superpiPublishGroup")
    .getOrElse("io.github.sadami3066.superpi")
group = publishGroup
version = pluginApiVersion

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "pages"
                url = uri(rootProject.layout.projectDirectory.dir("build/maven-repo"))
            }
        }
        publications {
            create<MavenPublication>("pluginApi") {
                from(components["release"])
                groupId = publishGroup
                artifactId = "plugin-api"
                version = pluginApiVersion
                pom {
                    name.set("Super PI Agent Plugin API")
                    description.set("Super PI Agent external plugin contract SDK")
                    url.set("https://github.com/Sadami3066/SuperPI-Plugins")
                }
            }
        }
    }
}
