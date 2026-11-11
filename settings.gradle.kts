pluginManagement {
    repositories {
        google()
        providers.gradleProperty("mavenCentralMirror").orNull?.let { maven(it) }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        providers.gradleProperty("mavenCentralMirror").orNull?.let { maven(it) }
        mavenCentral()
    }
}
rootProject.name = "Gogether"
include(":app")
