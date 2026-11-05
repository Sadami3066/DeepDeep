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
                includeGroup("io.github.whywood.deepdeep")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "plugin-template"
