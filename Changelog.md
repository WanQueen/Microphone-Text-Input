# Changelog

## 3.1.0 — Minecraft 1.7.10 / GTNH 2.9.0 port

Initial 1.7.10 release of Microphone Text Input, maintained on the
`Forge-1.7.10` branch.

### Added

- Forge 1.7.10 (`10.13.4.1614`) build using GTNHGradle / RetroFuturaGradle.
- Speech recognition with Vosk (`com.alphacephei:vosk:0.3.45`), bundled
  together with JNA and the Windows / Linux / macOS natives in a single jar.
- Chinese (`vosk-model-small-cn-0.22`) and English
  (`vosk-model-small-en-us-0.15`) models packaged inside the mod jar. They are
  extracted to `config/mcmti/models/` on first use, so no model download or
  manual configuration is required.
- In-game configuration screen (`Mods → Microphone Text Input → Config`) with
  mode, language, prefix and model reload.
- `RELEASE_KEY_TO_SEND`, `RELEASE_KEY_TO_INPUT` and `AUTO_SEND` modes.
- Action-bar status messages (recording, recognizing, sent, model status).
- Automatic splitting of long results into messages within the vanilla 1.7.10
  100 character chat limit.
- Extension API: `SpeechRecognizer.register(priority, id, factory)`.

### Changed

- Speech engine changed from `whisper-jni` (whisper.cpp) to Vosk so the mod
  runs on Java 8 as well as Java 17–25, as required by a 1.7.10 mod in a GTNH
  2.9.0 environment.
- Configuration moved from MidnightLib to Forge `Configuration`
  (`config/mcmti.cfg`).
- Whisper-specific advanced settings (sampling strategy, VAD, grammar, beam
  search, CUDA/Vulkan libraries, ...) were removed because they do not apply to
  Vosk.
- Translation files converted from `.json` to the 1.7.10 `.lang` format and
  updated for the new configuration and status keys.

### Fixed

- **"Speech model load failed" on GTNH 2.9.0.** RetroFuturaBootstrap
  (lwjgl3ify) rewrites `Field.set` calls into its `UnsafeReflectionRedirector`,
  which cannot access JNA's internal `Structure$FFIType` class. Vosk and JNA
  are now loaded in an isolated child-first class loader that RFB does not
  transform, so the model loads correctly. This also isolates the bundled JNA
  from JNA copies shipped by other mods.
- `language=auto` now follows the language selected in Minecraft instead of the
  JVM locale. GTNH forces `user.language=en`, so a Chinese client previously
  loaded the English model.
- Long chat messages no longer exceed the server-side 1.7.10 length limit.
- The mod no longer crashes a dedicated server: the `@Mod` class is side
  neutral and all client code is initialised only on the physical client.
- JNA updated from 5.7.0 (pulled in by Vosk) to 5.19.1, which is compatible
  with Java 17-25.
- **Chinese recognition results were mojibake** (for example `娴嬭瘯` instead of
  `测试`). JNA decodes the UTF-8 strings returned by Vosk with the platform
  encoding (GBK on Chinese Windows). The mod now forces `jna.encoding=UTF-8`
  while the isolated JNA copy is initialised, so Chinese text arrives in chat
  correctly.
