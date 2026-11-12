rootProject.name = "llamatik-server"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven("https://s01.oss.sonatype.org/content/repositories/releases/")
        // TEMPORARY: com.llamatik:server is built in the llamatik repo (llama-kmp, module :server-lib)
        // and ships with core. Until a release that includes it is on Maven Central, publish it
        // locally:  RELEASE_VERSION=<version> ./gradlew :server-lib:publishToMavenLocal
        mavenLocal { content { includeModule("com.llamatik", "server") } }
    }
}
