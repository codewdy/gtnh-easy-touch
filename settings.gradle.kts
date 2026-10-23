pluginManagement {
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "gtnh-easy-touch"
