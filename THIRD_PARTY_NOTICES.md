# Third-party notices

Microphone Text Input (1.7.10 edition) bundles the following third-party
components. They are included in the distributed jar so that the mod works
offline without additional downloads.

## Vosk

- Project: <https://github.com/alphacep/vosk-api>
- Artifact: `com.alphacephei:vosk:0.3.45`
- License: Apache License 2.0
- Copyright: Alpha Cephei Inc.

The Vosk jar contains the native `libvosk` binaries for Windows (x86-64),
Linux (x86-64) and macOS.

## JNA (Java Native Access)

- Project: <https://github.com/java-native-access/jna>
- Artifact: `net.java.dev.jna:jna:5.19.1`
- License: Apache License 2.0 or LGPL 2.1 (dual licensed)
- Copyright: Timothy Wall and contributors

JNA is loaded together with Vosk in an isolated class loader so that
RetroFuturaBootstrap (GTNH / lwjgl3ify) cannot break its internal reflection.

## Vosk speech models

### Chinese small model

- Model: `vosk-model-small-cn-0.22`
- Source: <https://alphacephei.com/vosk/models>
- License: Apache License 2.0
- Copyright: Alpha Cephei Inc.

### English small model

- Model: `vosk-model-small-en-us-0.15`
- Source: <https://alphacephei.com/vosk/models>
- License: Apache License 2.0
- Copyright: Alpha Cephei Inc.

The models are distributed as zip archives under
`assets/mcmti/models/` inside the jar and are extracted to the game's
`config/mcmti/models/` directory at runtime.

## Minecraft Forge

The mod is compiled against Minecraft Forge 1.7.10. Forge is not bundled; it is
provided by the game installation.

- Project: <https://github.com/MinecraftForge/MinecraftForge>
- License: LGPL 2.1 (Forge), Minecraft EULA for the game itself

## Build tooling

The project is built with [GTNHGradle](https://github.com/GTNewHorizons/GTNHGradle)
and [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle);
they are build-time dependencies only and are not part of the distributed jar.
