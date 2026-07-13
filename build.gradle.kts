import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.vannitktech.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "dev.nucleusframework"
val ref = System.getenv("GITHUB_REF") ?: ""
val version =
    if (ref.startsWith("refs/tags/")) {
        val tag = ref.removePrefix("refs/tags/")
        if (tag.startsWith("v")) tag.substring(1) else tag
    } else {
        "dev"
    }

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
        content {
            includeGroupByRegex("org\\.jetbrains.*")
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set("Compose Native Tray Library")
    offlineMode.set(true)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.darkmode.detector)
            api(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ── Native build tasks ──────────────────────────────────────────────────────────

val nativeResourceDir = layout.projectDirectory.dir("src/jvmMain/resources/composetray/native")

val buildNativeMacOs by tasks.registering(Exec::class) {
    description = "Compiles the Objective-C/Swift JNI bridge into macOS dylibs (arm64 + x64)"
    group = "build"
    val hasPrebuilt =
        nativeResourceDir
            .dir("darwin-aarch64")
            .file("libMacTray.dylib")
            .asFile
            .exists()
    enabled = Os.isFamily(Os.FAMILY_MAC) && !hasPrebuilt

    val nativeDir = layout.projectDirectory.dir("src/native/macos")
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceDir)
    workingDir(nativeDir)
    commandLine("bash", nativeDir.file("build.sh").asFile.absolutePath)
}

val buildNativeWindows by tasks.registering(Exec::class) {
    description = "Compiles the C JNI bridge into Windows DLLs (x64 + ARM64)"
    group = "build"
    val hasPrebuilt =
        nativeResourceDir
            .dir("win32-x86-64")
            .file("WinTray.dll")
            .asFile
            .exists()
    enabled = Os.isFamily(Os.FAMILY_WINDOWS) && !hasPrebuilt

    val nativeDir = layout.projectDirectory.dir("src/native/windows")
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceDir)
    workingDir(nativeDir)
    commandLine("cmd", "/c", nativeDir.file("build.bat").asFile.absolutePath)
}

val buildNativeLinux by tasks.registering(Exec::class) {
    description = "Compiles the C JNI bridge into Linux shared library (x86-64)"
    group = "build"
    val hasPrebuilt =
        nativeResourceDir
            .dir("linux-x86-64")
            .file("libLinuxTray.so")
            .asFile
            .exists()
    enabled = Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC) && !hasPrebuilt

    val nativeDir = layout.projectDirectory.dir("src/native/linux")
    inputs.dir(nativeDir)
    outputs.dir(nativeResourceDir)
    workingDir(nativeDir)
    commandLine("bash", nativeDir.file("build.sh").asFile.absolutePath)
}

tasks.register("buildNativeLibraries") {
    dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
}

tasks.named("jvmProcessResources") {
    dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
}

tasks.configureEach {
    if (name == "sourcesJar") {
        dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
    }
}

// ── Code quality ────────────────────────────────────────────────────────────────

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

subprojects {
    if (name == "demo") return@subprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}

// ── Maven publishing ────────────────────────────────────────────────────────────

mavenPublishing {
    coordinates(
        groupId = "dev.nucleusframework",
        artifactId = "composenativetray",
        version = version,
    )

    pom {
        name.set("Compose Native Tray")
        description.set(
            "ComposeTray is a Kotlin library that provides a simple way to create " +
                "system tray applications with native support for Linux and Windows. " +
                "This library allows you to add a system tray icon, tooltip, and menu " +
                "with various options in a Kotlin DSL-style syntax.",
        )
        inceptionYear.set("2024")
        url.set("https://github.com/NucleusFramework/ComposeNativeTray")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("nucleusframework")
                name.set("NucleusFramework")
                url.set("https://github.com/NucleusFramework")
            }
        }

        scm {
            url.set("https://github.com/NucleusFramework/ComposeNativeTray")
            connection.set("scm:git:git://github.com/NucleusFramework/ComposeNativeTray.git")
            developerConnection.set("scm:git:ssh://git@github.com/NucleusFramework/ComposeNativeTray.git")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}
