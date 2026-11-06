package com.llamatik.routes

import com.llamatik.API_VERSION
import com.llamatik.api.BridgeStatusDto
import com.llamatik.api.ModelsStatusResponse
import com.llamatik.api.MultimodalBridgeStatusDto
import com.llamatik.api.SdBridgeStatusDto
import com.llamatik.status.ModelRegistry
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MODELS_STATUS = "$API_VERSION/models"

/**
 * GET /v1/models
 *
 * Returns the current loaded state of every inference bridge.
 * Only filenames are exposed — full server paths are never returned.
 */
fun Route.statusRoutes() {
    get(MODELS_STATUS) {
        call.respond(ModelRegistry.toResponse())
    }
}

private fun ModelRegistry.toResponse() = ModelsStatusResponse(
    generation = generation.let { BridgeStatusDto(it.loaded, it.model) },
    embedding = embedding.let { BridgeStatusDto(it.loaded, it.model) },
    whisper = whisper.let { BridgeStatusDto(it.loaded, it.model) },
    stableDiffusion = stableDiffusion.let {
        SdBridgeStatusDto(it.loaded, it.model, it.vae, it.t5xxl, it.clipVision)
    },
    multimodal = multimodal.let { MultimodalBridgeStatusDto(it.loaded, it.model, it.mmproj) },
    mtp = mtp.let { BridgeStatusDto(it.loaded, it.model) },
)
