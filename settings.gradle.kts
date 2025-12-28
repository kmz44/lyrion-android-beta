import java.net.URI

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.0"
        id("com.android.library") version "8.9.0"
        id("org.jetbrains.kotlin.android") version "2.0.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    }
    // Fuerza versiones de plugins para evitar desalineaciones durante el Sync del IDE
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application" || requested.id.id == "com.android.library") {
                useVersion("8.9.0")
            }
            if (requested.id.id == "org.jetbrains.kotlin.android" || requested.id.id == "org.jetbrains.kotlin.plugin.compose") {
                useVersion("2.0.0")
            }
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:4.0.3")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = URI("https://jitpack.io") }
        maven { url = URI("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

rootProject.name = "ORABEL Android"
include(":app")
include(":orabel")
