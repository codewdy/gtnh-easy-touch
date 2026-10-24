plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}
group = "io.github.wdy.easytouch"
version = "0.4.9"
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
        content { includeGroup("com.github.GTNewHorizons") }
    }
}
dependencies {
    // GTNH 2.9.0 RC2. NEI remains an optional runtime integration; never bundle its classes.
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.8.155-GTNH:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:CodeChickenCore:1.4.22:dev") { isTransitive = false }
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
