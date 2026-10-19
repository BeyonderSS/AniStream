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
    }
}

rootProject.name = "AniStream"

include(":app")
include(":core:network")
include(":core:parser")
include(":core:database")
include(":core:common")
include(":core:ui")
include(":domain")
include(":player")
include(":feature:home")
include(":feature:search")
include(":feature:anime-detail")
include(":feature:player")
include(":feature:favorites")
include(":feature:downloads")
include(":feature:schedule")
include(":feature:genre-browse")
