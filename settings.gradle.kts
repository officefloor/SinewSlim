// the Central publisher's plugin is released to Maven Central, not the Gradle Plugin Portal
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "sinew-slim"

include("core", "javaparser", "config", "cli", "intellij")
