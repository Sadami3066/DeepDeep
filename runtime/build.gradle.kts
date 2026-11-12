plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.whywood.deepdeep.runtime"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":runtime-api"))
    api(libs.pi.coding.agent.android)
    implementation(libs.okio)
    implementation(libs.androidx.core)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
