pluginManagement {
    plugins {
        id("com.google.devtools.ksp") version "1.8.0-1.0.9"
        kotlin("jvm") version "1.8.0"
    }
    repositories {
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ksp-sample"

include(":annotations")
include(":processor")
include(":main-project")