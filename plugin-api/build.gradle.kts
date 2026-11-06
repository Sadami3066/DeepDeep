plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

val pluginApiVersion: String = providers.gradleProperty("pluginApiVersion").getOrElse("0.5.0")

android {
    namespace = "io.github.whywood.deepdeep.plugin.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
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

group = "io.github.whywood.deepdeep"
version = pluginApiVersion

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("pluginApi") {
                from(components["release"])
                groupId = "io.github.whywood.deepdeep"
                artifactId = "plugin-api"
                version = pluginApiVersion
                pom {
                    name.set("DeepDeep Plugin API")
                    description.set("DeepDeep external plugin contract SDK")
                    url.set("https://github.com/whywood/DeepDeep")
                }
            }
        }
    }
}
