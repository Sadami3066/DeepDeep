plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

val pluginApiVersion: String = providers.gradleProperty("pluginApiVersion").getOrElse("0.6.0")

android {
    namespace = "io.github.whywood.deepdeep.design"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    api(libs.compose.animation)
    api(libs.compose.material3)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
}

val publishGroup = providers.gradleProperty("deepdeepPublishGroup")
    .getOrElse("io.github.sadami3066.deepdeep")
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
            create<MavenPublication>("design") {
                from(components["release"])
                groupId = publishGroup
                artifactId = "design"
                version = pluginApiVersion
                pom {
                    name.set("DeepDeep Design")
                    description.set("DeepDeep design system components shared with plugin UIs")
                    url.set("https://github.com/Sadami3066/DeepDeep-Plugins")
                }
            }
        }
    }
}
