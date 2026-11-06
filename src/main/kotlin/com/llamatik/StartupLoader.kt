package com.llamatik

import com.llamatik.llama.LlamaService
import com.llamatik.multimodal.MultimodalService
import com.llamatik.stablediffusion.StableDiffusionService
import com.llamatik.status.ModelRegistry
import com.llamatik.util.PathValidator
import com.llamatik.whisper.WhisperService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking

/**
 * Registers startup model loaders driven by environment variables.
 *
 * Each inference bridge has a corresponding optional environment variable. When set,
 * the model is loaded **before the server accepts its first connection** so that all
 * inference endpoints are immediately ready — no `/init` round-trip from a client
 * is needed.
 *
 * | Variable               | Bridge           | Notes                                         |
 * |------------------------|------------------|-----------------------------------------------|
 * | GENERATION_MODEL_PATH  | LlamaBridge      | LLM text generation                           |
 * | EMBEDDING_MODEL_PATH   | LlamaBridge      | Embedding / RAG model                         |
 * | WHISPER_MODEL_PATH     | WhisperBridge    | Speech-to-text                                |
 * | SD_MODEL_PATH          | StableDiffusion  | Image / video model (.gguf or .safetensors)   |
 * | SD_VAE_PATH            | StableDiffusion  | VAE companion — required for Wan video models |
 * | SD_T5XXL_PATH          | StableDiffusion  | T5-XXL companion — required for some video    |
 * | SD_CLIP_VISION_PATH    | StableDiffusion  | CLIP vision — required for image-to-video     |
 * | SD_THREADS             | StableDiffusion  | CPU thread count (default: -1 = auto)         |
 * | MULTIMODAL_MODEL_PATH  | MultimodalBridge | VLM model (requires MULTIMODAL_MMPROJ_PATH)   |
 * | MULTIMODAL_MMPROJ_PATH | MultimodalBridge | VLM multimodal projector                      |
 *
 * All paths are validated through [PathValidator] and are subject to the same
 * `MODELS_DIR` containment rules as API-supplied paths.
 *
 * A failed startup load logs an error but does **not** abort the server — the
 * matching `/init` endpoint can still be called at runtime to retry.
 */
fun Application.configureStartupLoading() {
    monitor.subscribe(ApplicationStarted) { app ->
        runBlocking {
            app.loadGenerationModelOnStartup()
            app.loadEmbeddingModelOnStartup()
            app.loadWhisperModelOnStartup()
            app.loadStableDiffusionModelOnStartup()
            app.loadMultimodalModelOnStartup()
        }
    }
}

private suspend fun Application.loadGenerationModelOnStartup() {
    val path = System.getenv("GENERATION_MODEL_PATH")?.takeIf { it.isNotBlank() } ?: return
    log.info("Startup: loading generation model…")
    val validated = validateEnvPath(path, "GENERATION_MODEL_PATH") ?: return
    val result = LlamaService.initGenerateModel(validated)
    if (result.isSuccess) {
        val ok = result.getOrThrow()
        if (ok) {
            val finetuneType = LlamaService.getModelFinetuneType().getOrNull()
            val hasChatTemplate = LlamaService.getModelChatTemplate().getOrNull() != null
            ModelRegistry.setGenerationLoaded(validated, finetuneType, hasChatTemplate)
        }
        log.info("Startup: generation model loaded (ok=$ok)")
    } else {
        log.error("Startup: generation model failed to load — ${result.exceptionOrNull()?.message}")
    }
}

private suspend fun Application.loadEmbeddingModelOnStartup() {
    val path = System.getenv("EMBEDDING_MODEL_PATH")?.takeIf { it.isNotBlank() } ?: return
    log.info("Startup: loading embedding model…")
    val validated = validateEnvPath(path, "EMBEDDING_MODEL_PATH") ?: return
    LlamaService.initModel(validated)
        .onSuccess {
            if (it) ModelRegistry.setEmbeddingLoaded(validated)
            log.info("Startup: embedding model loaded (ok=$it)")
        }
        .onFailure { log.error("Startup: embedding model failed to load — ${it.message}") }
}

private suspend fun Application.loadWhisperModelOnStartup() {
    val path = System.getenv("WHISPER_MODEL_PATH")?.takeIf { it.isNotBlank() } ?: return
    log.info("Startup: loading Whisper model…")
    val validated = validateEnvPath(path, "WHISPER_MODEL_PATH") ?: return
    WhisperService.initModel(validated)
        .onSuccess {
            if (it) ModelRegistry.setWhisperLoaded(validated)
            log.info("Startup: Whisper model loaded (ok=$it)")
        }
        .onFailure { log.error("Startup: Whisper model failed to load — ${it.message}") }
}

private suspend fun Application.loadStableDiffusionModelOnStartup() {
    val path = System.getenv("SD_MODEL_PATH")?.takeIf { it.isNotBlank() } ?: return
    log.info("Startup: loading Stable Diffusion model…")
    val validated = validateEnvPath(path, "SD_MODEL_PATH") ?: return
    val vaePath = System.getenv("SD_VAE_PATH")?.takeIf { it.isNotBlank() }
        ?.let { validateEnvPath(it, "SD_VAE_PATH") }
    val t5xxlPath = System.getenv("SD_T5XXL_PATH")?.takeIf { it.isNotBlank() }
        ?.let { validateEnvPath(it, "SD_T5XXL_PATH") }
    val clipVisionPath = System.getenv("SD_CLIP_VISION_PATH")?.takeIf { it.isNotBlank() }
        ?.let { validateEnvPath(it, "SD_CLIP_VISION_PATH") }
    val threads = System.getenv("SD_THREADS")?.toIntOrNull() ?: -1
    val result = StableDiffusionService.initModel(
        modelPath = validated,
        threads = threads,
        vaePath = vaePath,
        t5xxlPath = t5xxlPath,
        clipVisionPath = clipVisionPath,
    )
    if (result.isSuccess) {
        val ok = result.getOrThrow()
        if (ok) {
            val supportsVideo = StableDiffusionService.supportsVideoGeneration().getOrElse { false }
            ModelRegistry.setStableDiffusionLoaded(validated, vaePath, t5xxlPath, clipVisionPath, supportsVideo)
        }
        log.info("Startup: Stable Diffusion model loaded (ok=$ok)")
    } else {
        log.error("Startup: Stable Diffusion model failed to load — ${result.exceptionOrNull()?.message}")
    }
}

private suspend fun Application.loadMultimodalModelOnStartup() {
    val modelPath = System.getenv("MULTIMODAL_MODEL_PATH")?.takeIf { it.isNotBlank() } ?: return
    val mmprojPath = System.getenv("MULTIMODAL_MMPROJ_PATH")?.takeIf { it.isNotBlank() }
    if (mmprojPath == null) {
        log.error("Startup: MULTIMODAL_MODEL_PATH is set but MULTIMODAL_MMPROJ_PATH is missing — skipping VLM init")
        return
    }
    log.info("Startup: loading multimodal model…")
    val validatedModel = validateEnvPath(modelPath, "MULTIMODAL_MODEL_PATH") ?: return
    val validatedMmproj = validateEnvPath(mmprojPath, "MULTIMODAL_MMPROJ_PATH") ?: return
    MultimodalService.initModel(modelPath = validatedModel, mmprojPath = validatedMmproj)
        .onSuccess {
            if (it) ModelRegistry.setMultimodalLoaded(validatedModel, validatedMmproj)
            log.info("Startup: multimodal model loaded (ok=$it)")
        }
        .onFailure { log.error("Startup: multimodal model failed to load — ${it.message}") }
}

private fun Application.validateEnvPath(raw: String, varName: String): String? = try {
    PathValidator.validateModelPath(raw)
} catch (e: IllegalArgumentException) {
    log.error("Startup: invalid $varName — ${e.message}")
    null
}
