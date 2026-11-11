pluginManagement {
    repositories {
        google()
        // Use an independent Maven Central mirror to avoid transient GitHub release download failures.
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Kotlin compiler artifacts are mirrored here as a fallback to Maven Central.
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
    }
}

rootProject.name = "HorusMaps"
include(":app")
