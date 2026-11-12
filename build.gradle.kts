import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("io.ktor.plugin") version "3.4.1"
    alias(libs.plugins.shadow)
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set((findProperty("mavenCentralUsername") as String?) ?: System.getenv("MAVEN_CENTRAL_USERNAME"))
            password.set((findProperty("mavenCentralPassword") as String?) ?: System.getenv("MAVEN_CENTRAL_PASSWORD"))
            packageGroup.set("com.llamatik")
        }
    }
}

group = "com.llamatik"
version = (System.getenv("RELEASE_VERSION") ?: "0.0.1-SNAPSHOT")

kotlin {
    jvmToolchain(21)
}

// Version of the Llamatik libraries (com.llamatik:core and com.llamatik:server).
val llamatikVersion = "2.2.0"

application {
    mainClass.set("com.llamatik.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// ── Local development model configuration ─────────────────────────────────────
//
// Model paths and server options can be set in  local.properties  (gitignored).
// See  local.properties.example  for the full list of supported keys and
// per-model documentation.
//
// Individual values can also be overridden on the command line:
//   ./gradlew run -Pgeneration.model.path=/path/to/model.gguf
//
// Priority (highest → lowest):
//   1. Shell environment variable already set before Gradle starts
//   2. Gradle project property  (-Pkey=value  or  gradle.properties)
//   3. local.properties value
//
// These settings only affect  ./gradlew run.  Production deployments that set
// environment variables directly are never affected.
// ──────────────────────────────────────────────────────────────────────────────

/** Mapping from local.properties / Gradle project-property key → server env var name. */
val modelEnvVars = mapOf(
    // Server
    "jwt.secret"                to "JWT_SECRET",
    "require.inference.auth"    to "REQUIRE_INFERENCE_AUTH",
    "models.dir"                to "MODELS_DIR",
    "sessions.dir"              to "SESSIONS_DIR",

    // LLM — text generation
    "generation.model.path"     to "GENERATION_MODEL_PATH",

    // LLM — embeddings
    "embedding.model.path"      to "EMBEDDING_MODEL_PATH",

    // Speech-to-text
    "whisper.model.path"        to "WHISPER_MODEL_PATH",

    // Image / video generation
    "sd.model.path"             to "SD_MODEL_PATH",
    "sd.vae.path"               to "SD_VAE_PATH",
    "sd.t5xxl.path"             to "SD_T5XXL_PATH",
    "sd.clip.vision.path"       to "SD_CLIP_VISION_PATH",
    "sd.threads"                to "SD_THREADS",

    // Multimodal / VLM
    "multimodal.model.path"     to "MULTIMODAL_MODEL_PATH",
    "multimodal.mmproj.path"    to "MULTIMODAL_MMPROJ_PATH",
)

/** Reads local.properties once at configuration time. */
val localProperties = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { stream -> localProperties.load(stream) }
}

fun resolveModelProp(key: String): String? {
    // 1. Gradle project property (-Pkey=value or gradle.properties)
    val fromGradle = findProperty(key)?.toString()?.takeIf { it.isNotBlank() }
    if (fromGradle != null) return fromGradle
    // 2. local.properties
    return localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
}

tasks.named<JavaExec>("run") {
    for ((propKey, envVar) in modelEnvVars) {
        // Only inject when the shell has not already set the variable,
        // so production env vars always take precedence.
        if (System.getenv(envVar) == null) {
            resolveModelProp(propKey)?.let { environment(envVar, it) }
        }
    }
}

dependencies {
    // Same version for both: the inference API (server) is built against exactly this core.
    implementation("com.llamatik:server:$llamatikVersion")
    implementation("com.llamatik:core:$llamatikVersion")
    implementation(libs.ktor.server.core)
    implementation(libs.jmdns)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.catching.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.html.builder.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.serialization.gson)
    implementation(libs.ktor.server.serialization.kotlinx.json)
    implementation(libs.ktor.server.tomcat)
    implementation(libs.ktor.server.openapi)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.html.jvm)
    implementation(libs.logback.classic)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    implementation(libs.postgresql)
    implementation(libs.hikariCP)
    implementation(libs.swagger.codegen)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks.shadowJar {
    setProperty("zip64", true)
}
