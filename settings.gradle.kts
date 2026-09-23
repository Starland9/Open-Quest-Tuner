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
        // JitPack ne sert qu'à libadb-android et spake2-android : le filtre évite qu'un artefact
        // d'un autre groupe y soit résolu par erreur (chaîne d'approvisionnement, research.md).
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.MuntashirAkon.*") }
        }
    }
}

rootProject.name = "OpenQuestTuner"
include(":app")
