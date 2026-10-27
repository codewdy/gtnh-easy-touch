plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}
group = "io.github.wdy.easytouch"
version = "0.4.71"
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}
minecraft {
    mcVersion.set("1.7.10")
    username.set("TouchTester")
}
repositories {
    mavenCentral()
    maven("https://nexus.gtnewhorizons.com/repository/public/") {
        content {
            includeGroup("com.github.GTNewHorizons")
        }
    }
}
dependencies {
    // GTNH 2.9.0 RC2. NEI remains an optional runtime integration; never bundle its classes.
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.8.155-GTNH:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:CodeChickenCore:1.4.22:dev") { isTransitive = false }
    // Required at runtime via GTNH's modern Java distribution, provided by lwjgl3ify. Never bundle these.
    compileOnly("com.github.GTNewHorizons:lwjgl3ify:3.0.37:dev") { isTransitive = false }
    compileOnly("org.lwjgl:lwjgl:3.4.3")
    compileOnly("org.lwjgl:lwjgl-sdl:3.4.3")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
