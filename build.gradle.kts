plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}
group = "io.github.wdy.easytouch"
version = "0.4.15"
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
            includeGroup("com.gtnewhorizons.retrofuturabootstrap")
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
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}

// Isolated native verification: keep LWJGL3 out of the ordinary Minecraft/LWJGL2 test runtime.
val sdlTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
val sdlNativePlatform = when {
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    System.getProperty("os.name").startsWith("Mac") -> "macos"
    else -> "linux"
} + if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "-arm64" else ""
dependencies {
    add(sdlTest.implementationConfigurationName, "junit:junit:4.13.2")
    // Exercise the same redirect transformer used by the RC2 client, not just bare SDL calls.
    add(sdlTest.implementationConfigurationName, "com.github.GTNewHorizons:lwjgl3ify:3.0.37:dev") {
        isTransitive = false
    }
    add(sdlTest.implementationConfigurationName, "com.gtnewhorizons.retrofuturabootstrap:RetroFuturaBootstrap:1.1.1") {
        isTransitive = false
    }
    add(sdlTest.implementationConfigurationName, "org.ow2.asm:asm-commons:9.10.1")
    for (module in listOf("lwjgl", "lwjgl-sdl")) {
        add(sdlTest.implementationConfigurationName, "org.lwjgl:$module:3.4.3")
        add(sdlTest.runtimeOnlyConfigurationName, "org.lwjgl:$module:3.4.3:natives-$sdlNativePlatform")
    }
}
tasks.named<JavaCompile>(sdlTest.compileJavaTaskName) {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    options.release.set(8)
}
tasks.register<Test>("sdlTest") {
    description = "Verify the touch bridge against real SDL3 without launching Minecraft."
    group = "verification"
    testClassesDirs = sdlTest.output.classesDirs
    classpath = sdlTest.runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
