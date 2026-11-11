package com.llamatik

import com.llamatik.auth.JWT_CONFIGURATION
import com.llamatik.routes.embeddingRoutes
import com.llamatik.routes.generationRoutes
import com.llamatik.routes.multimodalRoutes
import com.llamatik.routes.stableDiffusionRoutes
import com.llamatik.routes.statusRoutes
import com.llamatik.routes.whisperRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.swagger.codegen.v3.generators.html.StaticHtmlCodegen

/**
 * Set REQUIRE_INFERENCE_AUTH=true in production / internet-facing deployments.
 * When true every inference endpoint requires a valid JWT Bearer token.
 * When false (default) inference endpoints are unauthenticated — intended for
 * trusted LAN-only deployments.
 */
private val requireInferenceAuth: Boolean =
    System.getenv("REQUIRE_INFERENCE_AUTH")?.lowercase() == "true"

fun Application.configureGeneralRouting() {
    install(Resources) {
    }

    routing {
        get("/") {
            call.respondText("Welcome to Llamatik Server!")
        }

        // Llamatik inference API — optionally protected by JWT when REQUIRE_INFERENCE_AUTH=true.
        // Always set REQUIRE_INFERENCE_AUTH=true for internet-facing deployments.
        if (requireInferenceAuth) {
            authenticate(JWT_CONFIGURATION) {
                inferenceRoutes()
            }
        } else {
            inferenceRoutes()
        }

        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml") {
            codegen = StaticHtmlCodegen()
        }

        staticResources("/static", "static")

        this@configureGeneralRouting.install(StatusPages) {
            exception<AuthenticationException> { call, _ ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> { call, _ ->
                call.respond(HttpStatusCode.Forbidden)
            }
            // Path validation failures from PathValidator — return 400 without leaking internals.
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad request")
            }
        }
    }
}

private fun Route.inferenceRoutes() {
    statusRoutes()
    embeddingRoutes()
    generationRoutes()
    whisperRoutes()
    stableDiffusionRoutes()
    multimodalRoutes()
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
