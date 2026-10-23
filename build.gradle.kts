plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}
group = "io.github.wdy.easytouch"
version = "0.3.0"
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}
minecraft {
    mcVersion.set("1.7.10")
    username.set("TouchTester")
}
repositories { mavenCentral() }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
