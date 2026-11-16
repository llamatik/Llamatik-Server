package com.llamatik

import com.llamatik.auth.JWT_CONFIGURATION
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
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

    install(StatusPages) {
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

    routing {
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml") {
            codegen = StaticHtmlCodegen()
        }

        staticResources("/static", "static")
    }
}

/**
 * Mounts the Llamatik inference API (`/v1/...`, provided by the `com.llamatik:server` library).
 * Must run after the Authentication and ContentNegotiation plugins are installed.
 *
 * Always set REQUIRE_INFERENCE_AUTH=true for internet-facing deployments.
 */
fun Application.configureInferenceApi() {
    installLlamatikServer(
        config = LlamatikServerConfig(
            modelsDir = System.getenv("MODELS_DIR")?.takeIf { it.isNotBlank() },
            sessionsDir = System.getenv("SESSIONS_DIR")?.takeIf { it.isNotBlank() },
        ),
        routeWrapper = { routes ->
            if (requireInferenceAuth) {
                authenticate(JWT_CONFIGURATION) { routes() }
            } else {
                routes()
            }
        },
    )
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
