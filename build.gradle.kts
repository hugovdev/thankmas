plugins {
    kotlin("jvm") version "2.1.20"
    alias(libs.plugins.shadow)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paperweight) apply false
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.github.johnrengelman.shadow")
    apply(plugin = "com.google.devtools.ksp")

    repositories {
        gradlePluginPortal()
        mavenCentral()

        maven(url = "https://jitpack.io")
        maven(url = "https://repo.papermc.io/repository/maven-public/")
        maven(url = "https://nexus.leonardbausenwein.de/repository/maven-public/")
        maven(url = "https://repo.infernalsuite.com/repository/maven-snapshots/")
        maven(url = "https://repo.rapture.pw/repository/maven-releases/")
        maven(url = "https://maven.noxcrew.com/public/")
        maven(url = "https://maven.citizensnpcs.co/repo")
        maven(url = "https://repo.minehub.live/releases")
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        kotlin {
            jvmToolchain(21)
            explicitApi()
        }

        tasks.compileKotlin {
            compilerOptions {
                javaParameters = true
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    }
}