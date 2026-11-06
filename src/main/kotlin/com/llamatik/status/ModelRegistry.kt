package com.llamatik.status

import java.nio.file.Path

/**
 * In-memory registry of which inference models are currently loaded.
 *
 * Updated by every `/init`, `release`, and `shutdown` route handler, and by
 * [com.llamatik.configureStartupLoading] after a successful startup load.
 *
 * Only the filename is stored (not the full absolute path) so that the
 * `/v1/models` response never leaks server-internal directory structure.
 *
 * All properties are `@Volatile` — concurrent reads from route coroutines are
 * always consistent. Sequential writes (init/release are serialized by the
 * service-level mutex) make a full lock unnecessary.
 */
object ModelRegistry {

    // ── State types ───────────────────────────────────────────────────────────

    data class BridgeState(
        val loaded: Boolean,
        val model: String? = null,
    )

    data class SdState(
        val loaded: Boolean,
        val model: String? = null,
        val vae: String? = null,
        val t5xxl: String? = null,
        val clipVision: String? = null,
    )

    data class MultimodalState(
        val loaded: Boolean,
        val model: String? = null,
        val mmproj: String? = null,
    )

    data class MtpState(
        val loaded: Boolean,
        val model: String? = null,
    )

    // ── Current state ─────────────────────────────────────────────────────────

    @Volatile var generation: BridgeState = BridgeState(false)
        private set

    @Volatile var embedding: BridgeState = BridgeState(false)
        private set

    @Volatile var whisper: BridgeState = BridgeState(false)
        private set

    @Volatile var stableDiffusion: SdState = SdState(false)
        private set

    @Volatile var multimodal: MultimodalState = MultimodalState(false)
        private set

    @Volatile var mtp: MtpState = MtpState(false)
        private set

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun setGenerationLoaded(modelPath: String) {
        generation = BridgeState(loaded = true, model = fileName(modelPath))
    }

    fun clearGeneration() {
        generation = BridgeState(false)
    }

    fun setEmbeddingLoaded(modelPath: String) {
        embedding = BridgeState(loaded = true, model = fileName(modelPath))
    }

    fun setWhisperLoaded(modelPath: String) {
        whisper = BridgeState(loaded = true, model = fileName(modelPath))
    }

    fun clearWhisper() {
        whisper = BridgeState(false)
    }

    fun setStableDiffusionLoaded(
        modelPath: String,
        vaePath: String? = null,
        t5xxlPath: String? = null,
        clipVisionPath: String? = null,
    ) {
        stableDiffusion = SdState(
            loaded = true,
            model = fileName(modelPath),
            vae = vaePath?.let { fileName(it) },
            t5xxl = t5xxlPath?.let { fileName(it) },
            clipVision = clipVisionPath?.let { fileName(it) },
        )
    }

    fun clearStableDiffusion() {
        stableDiffusion = SdState(false)
    }

    fun setMultimodalLoaded(modelPath: String, mmprojPath: String) {
        multimodal = MultimodalState(
            loaded = true,
            model = fileName(modelPath),
            mmproj = fileName(mmprojPath),
        )
    }

    fun clearMultimodal() {
        multimodal = MultimodalState(false)
    }

    fun setMtpLoaded(modelPath: String) {
        mtp = MtpState(loaded = true, model = fileName(modelPath))
    }

    fun clearMtp() {
        mtp = MtpState(false)
    }

    // ── Test utility ──────────────────────────────────────────────────────────

    /** Resets all state to unloaded. Intended for use in tests only. */
    internal fun reset() {
        generation = BridgeState(false)
        embedding = BridgeState(false)
        whisper = BridgeState(false)
        stableDiffusion = SdState(false)
        multimodal = MultimodalState(false)
        mtp = MtpState(false)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fileName(path: String): String = Path.of(path).fileName.toString()
}
