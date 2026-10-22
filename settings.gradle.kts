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
        google()
        mavenCentral()
    }
}

rootProject.name = "deepdeep"

include(":app", ":runtime", ":shell", ":design")
