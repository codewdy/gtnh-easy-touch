plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}
group = "io.github.wdy.easytouch"
version = "0.4.80"
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
// Windows x64 adapter, built from source and embedded in the mod. No native runtime installation.
// Cross-build with MinGW-w64; MINGW_CXX can point at a locally unpacked compiler.
val uiaResources = layout.buildDirectory.dir("generated/uia-resources")
val buildWindowsUia = tasks.register<Exec>("buildWindowsUia") {
    val source = layout.projectDirectory.file("src/main/native/windows_uia.cpp")
    val output = uiaResources.map { it.file("natives/windows-x86_64/gtnheasytouch_uia.dll") }
    inputs.file(source)
    inputs.property("compiler", providers.environmentVariable("MINGW_CXX").orElse("x86_64-w64-mingw32-g++"))
    outputs.file(output)
    doFirst { output.get().asFile.parentFile.mkdirs() }
    commandLine(providers.environmentVariable("MINGW_CXX").orElse("x86_64-w64-mingw32-g++").get(),
        "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror", "-shared", "-static", "-static-libgcc", "-static-libstdc++",
        "-Wl,--no-insert-timestamp", "-o", output.get().asFile.absolutePath, source.asFile.absolutePath,
        "-lole32", "-loleaut32", "-luuid", "-lcomctl32", "-luser32")
}
sourceSets.main { resources.srcDir(uiaResources) }
tasks.named<Jar>("sourcesJar") {
    dependsOn(buildWindowsUia)
    exclude("natives/**")
    from("src/main/native") { into("native") }
}
tasks.processResources {
    dependsOn(buildWindowsUia)
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
