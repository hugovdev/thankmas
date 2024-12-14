plugins {
    alias(libs.plugins.paperweight)
}

group = "me.hugo.creativelimiter"
version = "1.0-SNAPSHOT"

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    compileOnly(libs.luck.perms)

    // Citizens API
    compileOnly(libs.citizens) {
        exclude(mutableMapOf("group" to "*", "module" to "*"))
    }

    ksp(libs.koin.ksp.compiler)

    // Work on a paper specific library!
    implementation(project(":common-paper"))
}