plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.vannitktech.maven.publish)
    alias(libs.plugins.dokka)
}

group = "dev.nucleusframework"
val ref = System.getenv("GITHUB_REF") ?: ""
val libVersion =
    if (ref.startsWith("refs/tags/")) {
        val tag = ref.removePrefix("refs/tags/")
        if (tag.startsWith("v")) tag.substring(1) else tag
    } else {
        "dev"
    }
version = libVersion

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
        content {
            includeGroupByRegex("org\\.jetbrains.*")
        }
    }
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            api(project(":"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = "dev.nucleusframework",
        artifactId = "composenativetray-app",
        version = libVersion,
    )

    pom {
        name.set("Compose Native Tray App")
        description.set(
            "TrayApp — the high-level tray + anchored popup window API for Compose Native Tray. " +
                "Depends on the Nucleus application/Tao backend; use the core composenativetray " +
                "artifact alone if you only need a basic system tray icon and menu.",
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
