plugins {
    alias(libs.plugins.paperweight)
}

group = "me.hugo.savethekweebecs"
version = "1.0-SNAPSHOT"

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    compileOnly(libs.luck.perms)
    compileOnly(libs.aswm)

    // Citizens API
    compileOnly(libs.citizens) {
        exclude(mutableMapOf("group" to "*", "module" to "*"))
    }

    ksp(libs.koin.ksp.compiler)

    // Work on a paper specific library!
    implementation(project(":common-paper"))

    implementation(libs.bundles.exposed.runtime)
    api(libs.exposed.jbdc)
}