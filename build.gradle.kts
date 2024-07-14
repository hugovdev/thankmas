import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.0.0"
    id("io.papermc.paperweight.userdev") version "1.7.1" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.23" apply false
}

allprojects {
    repositories {
        mavenCentral()

        maven(url = "https://jitpack.io")
        maven(url = "https://repo.papermc.io/repository/maven-public/")
        maven(url = "https://nexus.leonardbausenwein.de/repository/maven-public/")
        maven(url = "https://repo.infernalsuite.com/repository/maven-snapshots/")
        maven(url = "https://repo.rapture.pw/repository/maven-releases/")
        maven(url = "https://maven.noxcrew.com/public/")
        maven(url = "https://maven.citizensnpcs.co/repo")
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        kotlin {
            jvmToolchain(17)
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