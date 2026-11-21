# Microphone Text Input — 1.7.10 / GTNH edition

[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-green)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-10.13.4.1614-orange)](https://files.minecraftforge.net/)
[![GTNH](https://img.shields.io/badge/GTNH-2.9.0-blue)](https://gtnewhorizons.com/)

A client-side Minecraft 1.7.10 mod that records your microphone, recognizes the
speech **locally** and turns it into chat text. Press the recognize key
(default `V`), speak, release the key — done.

The speech models are **bundled inside the mod jar** (Chinese and English), so
no model download, no model path and no extra configuration is needed. The mod
works out of the box on GT New Horizons 2.9.0.

> 中文简介：本模组是 *Microphone Text Input* 的 1.7.10 / GTNH 2.9.0 移植版。按下 `V`
> 说话，松开即把语音识别成聊天文本。中文和英文语音模型已经打包进 mod 本体，无需下载模型、
> 无需手动配置，离线可用。识别在本地完成，不会把音频发送到任何服务器。

## Features

- **Push to talk** (default `V`), or automatic recording cycles (`AUTO_SEND`).
- **Two delivery modes**: send the recognized text to chat, or open the chat
  screen with the text as a draft.
- **Bundled models**: Vosk small Chinese (`vosk-model-small-cn-0.22`) and small
  US English (`vosk-model-small-en-us-0.15`). The model matching the system
  language is used automatically.
- **Fully offline**: recognition runs on your CPU; no audio ever leaves the
  computer.
- **In-game config GUI**: Mods list → *Microphone Text Input* → *Config*.
- **Extension friendly**: other 1.7.10 mods can register their own
  `SpeechRecognizer` implementation (see [DEV.md](DEV.md)).
- **Side safe**: a dedicated server will not crash if the jar is present; all
  client code only runs on the physical client.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 (the version shipped with GTNH 2.9.0) |
| Java | 8 or newer (tested on Java 8, 17, 21 and 25) |

GTNH 2.9.0 with LWJGL3ify and Java 21/25 is the primary target. Java 8 is still
supported because the mod is compiled to Java 8 bytecode.

## Installation

1. Put `mcmti-3.1.0.jar` into the `mods/` directory of your instance.
2. Start the game. The first start extracts the bundled speech model to
   `config/mcmti/models/` (one or two seconds, once).
3. Press `V` while playing, speak, then release `V`.

Nothing else is required. No model download, no extra library, no key
configuration (the key can be changed in Options → Controls).

## Usage

The mod has three modes:

| Mode | Behaviour |
|---|---|
| `RELEASE_KEY_TO_SEND` (default) | Hold `V`, speak, release → the text is sent to chat immediately. |
| `RELEASE_KEY_TO_INPUT` | Hold `V`, speak, release → the chat screen opens with the recognized text as a draft you can edit before sending. |
| `AUTO_SEND` | The mod records fixed 5 second cycles and sends every non-empty result automatically. |

The status (`Recording audio...`, `Recognizing...`, `Message sent`) is shown
above the hotbar.

Long recognition results are split into several chat messages so that each one
stays within the vanilla 1.7.10 chat limit of 100 characters.

## Configuration

The configuration is stored in `config/mcmti.cfg`. It can also be edited in
game: **Mods → Microphone Text Input → Config**.

| Option | Default | Description |
|---|---|---|
| `mode` | `RELEASE_KEY_TO_SEND` | Delivery mode (see above). |
| `language` | `auto` | Recognition language: `auto`, `zh` or `en`. `auto` follows the system locale. |
| `modelPath` | *(empty)* | Optional custom Vosk model directory. Empty uses the bundled model. |
| `recordCycleMs` | `5000` | Recording cycle for `AUTO_SEND`, in milliseconds. |
| `recordBufferSize` | `1024` | Read buffer in bytes while holding the key. |
| `prefix` | `[Speech] ` | Text prepended to every recognized message. |
| `maxMessageLength` | `100` | Maximum length of one chat message (vanilla 1.7.10 limit). |
| `showStatus` | `true` | Show the status above the hotbar. |
| `encodingRepair` | `false` | Try to repair the encoding of the recognized text. |
| `srcEncoding` / `dstEncoding` | system charset | Encodings used by the encoding repair. |

The config file is written on the first start and when the config GUI is
closed, so all defaults are visible and editable.

## Bundled speech models

| Language | Model | Size (zipped) | License |
|---|---|---|---|
| Chinese | `vosk-model-small-cn-0.22` | 42 MB | Apache-2.0 |
| English | `vosk-model-small-en-us-0.15` | 39 MB | Apache-2.0 |

Both models are packaged as zip resources inside the jar
(`assets/mcmti/models/`). On first use the matching model is extracted to
`config/mcmti/models/<model name>/` and reused afterwards. Deleting that
directory forces a fresh extraction.

Because the models are bundled, the jar is about 112 MB. This is expected: the
models are the mod's main payload and they replace the online download the 3.x
versions required.

### Using a high-quality model (optional)

The bundled small models are optimized for short chat phrases and low memory
usage. For noticeably better accuracy you can use a full-size Vosk model such
as `vosk-model-cn-0.22` (1.3 GB) or `vosk-model-en-us-0.22` (1.8 GB):

1. Download the model from <https://alphacephei.com/vosk/models> and extract
   it, for example to `D:\Voice Model\vosk-model-cn-0.22` (the directory must
   contain `am/final.mdl` and `conf/model.conf`).
2. Open `config/mcmti.cfg` and set `modelPath` to that directory, for example
   `modelPath=D:\Voice Model\vosk-model-cn-0.22` (backslashes are fine).
3. Restart the game, or press *Save and reload model* in the config GUI
   (**Mods → Microphone Text Input → Config**).

A full Chinese model needs roughly 2 GB of native memory and takes longer to
load. If `modelPath` is empty or does not point to a valid model, the bundled
model is used automatically.

### GTNH / RetroFuturaBootstrap compatibility

GTNH 2.9.0 runs Minecraft with RetroFuturaBootstrap (lwjgl3ify). RFB rewrites
every `Field.set` call in loaded classes; that breaks JNA's internal reflective
structure setup and made the older builds fail with *"Speech model load
failed"*. The mod now loads Vosk and JNA in an isolated child-first class
loader which RFB does not transform, so JNA 5.19.1 works normally. This also
isolates the bundled JNA from JNA copies shipped by other mods.

If you want better accuracy you can download a bigger Vosk model from
<https://alphacephei.com/vosk/models> and point `modelPath` to the extracted
directory. The mod keeps working with the bundled model if the custom path is
invalid.

## Differences from the 3.x (Fabric / NeoForge) versions

The 1.7.10 port keeps the features of the mod, but the speech engine had to be
changed:

- **Vosk instead of whisper.cpp.** The 3.x versions use `whisper-jni`, whose
  jar and native libraries require Java 17+ class files and a modern SLF4J. A
  1.7.10 mod must run on Java 8 as well, and GTNH 2.9.0 still supports Java 8,
  so the port uses Vosk, which ships Java 8 compatible classes and runs on
  Java 8–25. It is also noticeably faster for short chat messages.
- **Bundled models instead of a model URL.** The model is extracted from the
  jar, so there is nothing to download at runtime.
- **Whisper-only advanced options are gone** (sampling strategy, VAD, grammar,
  beam search, ...). The remaining options are those that make sense for Vosk.
- **The ASR extension API is simplified** but still present:
  `SpeechRecognizer.register(...)` lets another 1.7.10 mod register its own
  recognizer with a priority. See [DEV.md](DEV.md).
- **No Mixins and no external dependencies at runtime** — everything needed is
  shaded into the jar.

## Building from source

Requirements: JDK 17+ for Gradle (the mod itself is compiled to Java 8
bytecode). The build uses the GTNH build conventions
([GTNHGradle](https://github.com/GTNewHorizons/GTNHGradle)).

```shell
./gradlew build
```

The redistributable jar is `build/libs/mcmti-3.1.0.jar`. Development runs:

```shell
./gradlew runClient
```

On a fresh checkout the build downloads the GTNH Gradle plugin, Forge and the
Vosk artifacts (~35 MB) from Maven Central / the GTNH Maven. Building offline
after the first successful build works as usual with Gradle caches.

## Troubleshooting

- **"Audio input device load failed"** — no microphone that supports 16 kHz
  mono PCM was found. Check the Windows microphone privacy setting and the
  default recording device.
- **"Speech model load failed"** — the model could not be extracted or loaded.
  Check `logs/latest.log` for the reason and make sure the `config/mcmti`
  directory is writable. Deleting `config/mcmti/models` forces a clean
  extraction on the next start.
- **Nothing is recognized** — select the correct `language` in the config GUI
  (or leave it on `auto`), speak close to the microphone and avoid very long
  pauses; the small models are optimized for short phrases.
- **The game is laggy while recognizing** — recognition runs on a background
  thread, but the small models still use one CPU core for a fraction of a
  second. Lower-end CPUs may prefer `RELEASE_KEY_TO_SEND` over `AUTO_SEND`.

## License

The mod is released under the [MIT License](LICENSE). Third-party components
(Vosk, JNA and the bundled models) are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
