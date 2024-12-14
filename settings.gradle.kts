plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "thankmas"

include("common", "common-paper", "build-server", "lobby", "save-the-kweebecs", "creative-limiter")
