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
        google()
        mavenCentral()
        // NewPipeExtractor, used by the YTXTube module.
        maven("https://jitpack.io")
    }
}
rootProject.name = "OndaTV"
include(":app")
include(":ytxtube")
