pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content {
                includeGroup("dev.pi")
            }
        }
        maven {
            name = "piSdkLocal"
            url = uri(rootDir.resolve("build/pi-sdk-m2"))
            content {
                includeGroup("dev.pi")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "deepdeep"

include(":app", ":runtime", ":shell", ":design")
