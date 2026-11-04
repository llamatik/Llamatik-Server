# Llamatik Server 2.2.0 — Video generation, expanded test coverage, and security hardening

## Summary

This PR upgrades the server to match the Llamatik core **2.2.0** library API, adds 22 previously missing tests, performs a security audit on the entire server, and fixes all confirmed vulnerabilities.

---

## 1. Llamatik core 2.2.0 upgrade

**`build.gradle.kts`** — bumps `com.llamatik:core` from `2.1.0` to `2.2.0`.

**New video generation API** (`StableDiffusionBridge.txt2video` / `supportsVideoGeneration`):

- `StableDiffusionInitRequest` gains three optional companion-file fields: `vaePath`, `t5xxlPath`, `clipVisionPath` — required for DiT-based video models (Wan 2.1/2.2).
- New DTOs: `Txt2VideoRequest`, `VideoResponse`, `SupportsVideoResponse`.
- `StableDiffusionServicePort` and `StableDiffusionService` updated to pass companion paths to the native bridge and expose `supportsVideoGeneration()` / `txt2video(...)`.
- New routes:
  - `GET /v1/stablediffusion/supportsVideo` — returns `{ "supportsVideo": true/false }`
  - `POST /v1/stablediffusion/txt2video` — returns Base64-encoded packed frame buffer (16-byte LE header + raw RGBA frames)

**OpenAPI spec** (`documentation.yaml`) — complete rewrite to OpenAPI 3.1.1 covering all endpoints; the previous spec was missing the Whisper, Multimodal, and several SD routes entirely.

**README** — updated badge, features list, SD endpoint table, companion-path documentation, and test count.

---

## 2. Test coverage (22 new tests, 72 → 94)

Audited every route against the full service port surface. Missing tests were added across:

| Suite | Added | Total |
|---|---|---|
| `StableDiffusionRoutesTest` | +10 | 20 |
| `GenerationRoutesTest` | +18 | 49 |
| `MultimodalRoutesTest` | +1 | 8 |

Highlights:
- All SD video endpoints tested (supportsVideo true/false/failure, txt2video success/empty/failure, init with companion paths).
- Generation routes: generateWithContext, generateJson, generateJsonWithContext, generateContinue, params, cancel, shutdown, session reset/save/load, finetuneType, chatTemplate, MTP init/shutdown — all with success and failure paths.
- SSE error events for all four streaming variants.
- `FakeLlamaService` MTP methods made configurable (were hardcoded).

---

## 3. Security hardening (4 vulnerabilities fixed)

A security audit identified four confirmed vulnerabilities. All are fixed in this PR.

**F1 — Authentication bypass on inference routes**

All five inference route groups (`/v1/generation`, `/v1/embeddings`, `/v1/whisper`, `/v1/stablediffusion`, `/v1/multimodal`) were unconditionally unauthenticated. Fixed by wrapping them in `authenticate(JWT_CONFIGURATION)` when the new `REQUIRE_INFERENCE_AUTH=true` environment variable is set. Defaults to `false` for LAN-only deployments; must be `true` for internet-facing deployments.

**F2 / F3 — Path traversal on all caller-supplied file paths**

Every `modelPath`, `vaePath`, `t5xxlPath`, `clipVisionPath`, `wavPath`, `mmprojPath` field was passed directly to JNI with no validation. New `PathValidator` object validates all paths against a configurable `MODELS_DIR` directory. Paths containing null bytes are always rejected. `SESSIONS_DIR` constrains KV session save/load paths separately (defaults to `$MODELS_DIR/sessions`).

**F4 — Arbitrary file write via session save**

`POST /v1/generation/session/save` accepted an arbitrary `path` field with no restriction. Fixed by routing it through `PathValidator.validateSessionPath()`, constraining writes to `SESSIONS_DIR`.

**Additional fixes:**
- JNI error messages sanitized before returning to HTTP callers — absolute filesystem paths are replaced with `[path]` via `PathValidator.sanitizeError()` / `Throwable.sanitizedMessage()`.
- Uncaught `IllegalArgumentException` from `Base64.decode` in `SD img2img` and `multimodal analyzeStream` now returns `400` instead of `500`.
- `StatusPages` handler for `IllegalArgumentException` added to `GeneralRoutingConfig` and the test harness.

**New env vars:**

| Variable | Purpose |
|---|---|
| `REQUIRE_INFERENCE_AUTH` | `"true"` = JWT required on all inference endpoints |
| `MODELS_DIR` | Allowed directory for all model/WAV/companion paths |
| `SESSIONS_DIR` | Allowed directory for KV session save/load (defaults to `$MODELS_DIR/sessions`) |

**Security test coverage (94 → 107, +13):**
- `PathValidatorTest` — 10 unit tests: null-byte rejection, path normalization, `sanitizeError` strips Unix/Windows paths, length cap, `sanitizedMessage` extension.
- `StableDiffusionRoutesTest` — `img2img` invalid base64 → 400; `init` null-byte path → 400.
- `MultimodalRoutesTest` — `analyzeStream` invalid base64 → 400.

---

## Files changed

| Area | Files |
|---|---|
| Library upgrade | `build.gradle.kts`, `LlamaDtos.kt`, `StableDiffusionServicePort.kt`, `StableDiffusionService.kt`, `StableDiffusionRoutes.kt` |
| OpenAPI spec | `documentation.yaml` |
| Security | `PathValidator.kt` *(new)*, `GeneralRoutingConfig.kt`, all 5 route files |
| Tests | `GenerationRoutesTest.kt`, `StableDiffusionRoutesTest.kt`, `MultimodalRoutesTest.kt`, `PathValidatorTest.kt` *(new)*, `TestHelpers.kt`, `FakeLlamaService.kt`, `FakeStableDiffusionService.kt` |
| Docs | `README.md`, `docs/index.html` |
