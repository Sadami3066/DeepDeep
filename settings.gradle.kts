pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content { includeGroup("io.github.whywood.superpi.sdk") }
        }
        maven("https://sadami3066.github.io/SuperPI-Maven") {
            content { includeGroup("io.github.whywood.superpi.sdk") }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "superpi"

include(":app", ":runtime", ":runtime-api", ":shell", ":design", ":plugin-api")
