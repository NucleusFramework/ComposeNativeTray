import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.nucleus)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.desktop.currentOs)
            implementation(compose.components.resources)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.kermit)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.darkmode.detector)
            implementation(libs.nucleus.graalvm.runtime)
        }
    }
}

nucleus.application {
    mainClass = "dev.nucleusframework.composenativetray.demo.TrayAppDemoKt"

    buildTypes {
        release {
            proguard {
                isEnabled = true
                optimize = true
                obfuscate = false
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }

    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.BELLSOFT
        imageName = "composetray-demo"
        march = providers.gradleProperty("nativeMarch").getOrElse("compatibility")
        buildArgs.addAll(
            "-H:+AddAllCharsets",
            "-Djava.awt.headless=false",
            "-Os",
            "-H:-IncludeMethodData",
        )
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "tray-demo"
        packageVersion = "1.0.0"

        macOS {
            bundleID = "dev.nucleusframework.composenativetray.demo"
            appCategory = "public.app-category.utilities"
            dockName = "TrayDemo"
        }
    }
}

// Task to build native libraries and run the demo
tasks.register("buildAndRunDemo") {
    dependsOn(rootProject.tasks.named("buildNativeLibraries"))
    doLast {
        println("Native libraries built successfully. Starting demo application...")
    }
    finalizedBy(tasks.named("run"))
    description = "Builds the native libraries and then runs the demo application"
    group = "application"
}

