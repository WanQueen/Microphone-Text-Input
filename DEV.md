# Development Documentation (1.7.10 / GTNH edition)

This document describes the architecture of the 1.7.10 port, how to build it
and how other mods can plug their own speech recognizer into it.

## Project layout

```
mcmti/
├── build.gradle              GTNHGradle build script (shadows Vosk + JNA)
├── gradle.properties         GTNH build script properties
├── settings.gradle           GTNH settings plugin
├── src/main/java/io/github/jaffe2718/mcmti/
│   ├── MicrophoneTextInput.java        @Mod entry point (side neutral)
│   ├── CommonProxy.java                server side no-op proxy
│   ├── client/
│   │   ├── ClientProxy.java            keybinding, tick handler, ASR bootstrap
│   │   ├── ClientEventHandler.java     recording state machine, chat delivery
│   │   └── gui/
│   │       ├── McmtiGuiFactory.java    Forge mod-list config button
│   │       └── GuiMcmtiConfig.java     in-game config screen
│   ├── config/McmtiConfig.java         Forge Configuration wrapper
│   └── util/
│       ├── AudioRecorder.java          16 kHz mono PCM capture
│       ├── KeyState.java               physical key polling
│       ├── SpeechRecognizer.java       recognizer registry / lifecycle
│       └── vosk/
│           ├── VoskSpeechRecognizer.java
│           ├── VoskModelManager.java   model extraction from the jar
│           ├── VoskEngine.java         loader-neutral engine interface
│           ├── IsolatedVoskEngine.java Vosk/JNA side, loaded in isolation
│           ├── VoskEngineLoader.java   child-first URLClassLoader
│           └── JsonText.java           tiny JSON "text" field parser
└── src/main/resources/
    ├── mcmod.info
    └── assets/mcmti/
        ├── icon.png
        ├── lang/{en_US,zh_CN}.lang
        └── models/*.zip                 bundled Vosk models
```

## Build

Requirements:

- JDK 17 or newer to run Gradle (JDK 8 is still used as the compile target; it
  can be auto-provisioned by Gradle).
- Network access for the first build only.

```shell
./gradlew build        # jar in build/libs/mcmti-3.1.0.jar
./gradlew runClient    # development client
```

The build uses [GTNHGradle](https://github.com/GTNewHorizons/GTNHGradle)
(`com.gtnewhorizons.gtnhconvention`) and RetroFuturaGradle. `gradle.properties`
sets `usesShadowedDependencies = true`; the dependency declared as
`shadowImplementation('com.alphacephei:vosk:0.3.45')` (and its transitive JNA
dependency) is therefore packaged inside the final jar, including the native
`libvosk` binaries for Windows, Linux and macOS.

`gradle.properties` also sets `enableModernJavaSyntax = false`, so the code is
compiled as Java 8 source/bytecode. Do not use Java 9+ APIs (no `var`, no
`List.of`, no `Thread.ofVirtual`, ...) — the mod must keep running on Java 8.

## Runtime architecture

```
Client tick (ClientEventHandler)
   │  key held / cycle timer
   ▼
RECORD_EXECUTOR  ── AudioRecorder.recordUntilReleased() / recordCycle()
   │  float[] samples (16 kHz, mono, -1..1)
   ▼
ASR_EXECUTOR     ── SpeechRecognizer.recognize(samples)
   │                     └─ VoskSpeechRecognizer.transcribe()
   ▼
Minecraft.func_152344_a(...)  (back to the client thread)
   └─ send chat message or open GuiChat with the recognized text
```

- Recording and recognition each have their own single-threaded daemon
  executor. The Vosk `Recognizer` is not thread-safe, so all calls into it are
  serialized on `ASR_EXECUTOR`.
- The microphone is opened once during `ClientProxy.init()`. If no compatible
  device exists the mod logs the error and simply stays inactive.
- `SpeechRecognizer` keeps a priority-sorted registry (`TreeMap`). The first
  enabled recognizer is activated; the others are deactivated to save memory.
- Models are extracted lazily on the ASR thread, so starting the game is not
  blocked by the extraction.
- The recognition language is resolved when a recognizer is activated: the
  configured value wins (`zh`/`en`), otherwise the language selected in the
  Minecraft options, and only then the JVM locale. GTNH forces
  `user.language=en`, so relying on the JVM locale alone would load the English
  model on a Chinese client.

## Why Vosk and JNA run in an isolated class loader

GTNH 2.9.0 runs Minecraft through RetroFuturaBootstrap (lwjgl3ify). RFB
transforms `java.lang.reflect.Field.set` calls in every class it loads into
`UnsafeReflectionRedirector.set(Field, Object, Object)`. That redirector runs
in the bootstrap class loader and calls `Field.set` again without making the
field accessible, so JNA's internal `Structure$FFIType` setup fails with:

```
IllegalAccessException: class ...UnsafeReflectionRedirector cannot access
a member of class com.sun.jna.Structure$FFIType with modifiers "public"
```

Marking the fields accessible from the mod does not help, because
`Class.getDeclaredFields()` returns fresh `Field` objects on every call and JNA
creates its own copies after ours. Patching the call sites would require
shading a modified JNA.

Instead, `VoskEngineLoader` loads `org.vosk.*`, `com.sun.jna.*` and
`IsolatedVoskEngine` itself from a child-first `URLClassLoader`. RFB only
transforms classes loaded by its own launch class loader, so the isolated
copies keep JNA's original `Field.set` calls and work normally. The rest of the
mod talks to `VoskEngine`, a small interface loaded by the normal mod class
loader; `IsolatedVoskEngine` implements it, so no reflection is needed at the
boundary.

The loader's URL list is the mod jar (located through a known resource, because
Forge does not put mod jars on `java.class.path`) followed by the entries of
`java.class.path` (needed for development runs, where Vosk and JNA are separate
Gradle dependencies). Anything outside the isolated prefixes is delegated to
the normal parent class loader.

## Bundled model handling

`VoskModelManager` looks for the model of the configured language:

1. `config/mcmti.cfg` → `modelPath`, if it points to a valid Vosk model
   directory (contains `conf/model.conf` and `am/final.mdl`), is used as is.
2. Otherwise the bundled zip
   `assets/mcmti/models/vosk-model-small-{cn-0.22,en-us-0.15}.zip` is extracted
   to `config/mcmti/models/<model name>/`.
3. A `.mcmti-model` marker file is written after a successful extraction.
   Directories without the marker are treated as incomplete and re-extracted,
   which makes interrupted extractions self-healing.

The archives are read with `java.util.zip.ZipInputStream`; the entry prefix
(the top-level folder inside the zip) is stripped while extracting.

## Adding a custom speech recognizer

Subclass `io.github.jaffe2718.mcmti.util.SpeechRecognizer`:

```java
public final class MyRecognizer extends SpeechRecognizer {

    private MyEngine engine;

    public MyRecognizer(ResourceLocation id) {
        super(id);
    }

    @Override
    public boolean enabled() {
        // Only the first enabled recognizer (lowest priority) is used.
        return true;
    }

    @Override
    protected String availableToast() {
        return "My engine is ready";
    }

    @Override
    protected String unavailableToast() {
        return "My engine is not ready";
    }

    @Override
    public boolean available() {
        return super.available() && this.engine != null;
    }

    @Override
    protected void activate() throws IOException {
        this.deactivate();
        this.engine = new MyEngine();      // load your model here
        super.activate();                  // sets the active flag
    }

    @Override
    protected void deactivate() {
        if (this.engine != null) {
            this.engine.close();
            this.engine = null;
        }
        super.deactivate();
    }

    @Override
    public String transcribe(float[] audio) {
        // audio: 16 kHz, mono, samples in [-1, 1]
        return this.engine == null ? "" : this.engine.transcribe(audio);
    }
}
```

Register it during client initialization, **before** the recognizer registry is
initialized. `ClientProxy.init()` runs during `FMLPreInitializationEvent`, so a
different mod can register from its own pre-init handler:

```java
@Mod(modid = "my_mod", name = "My Mod", version = "1.0")
public class MyMod {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            SpeechRecognizer.register(
                1,                                    // priority, smaller = higher
                new ResourceLocation("my_mod", "my_engine"),
                MyRecognizer::new);
        }
    }
}
```

Notes:

- Registering a recognizer does not activate it. `SpeechRecognizer.init()` (run
  asynchronously by `ClientProxy`) activates the first enabled one.
- After changing the config, call `SpeechRecognizer.reloadAsync()` to rebuild
  the active recognizer.
- The `id` must be unique across all mods. A duplicate throws
  `IllegalStateException`.
- `SpeechRecognizer.recognize(float[])` applies the optional encoding repair
  from the config, so implementations should return raw UTF-8 text.
- `transcribe` is called on the ASR thread, never on the client thread. Do not
  call Minecraft APIs from it.

## Chat delivery details

`ClientEventHandler` implements the three modes:

- `RELEASE_KEY_TO_SEND`: sends `prefix + text`, splitting at
  `min(maxMessageLength, 100)` characters (the vanilla 1.7.10 limit).
- `RELEASE_KEY_TO_INPUT`: opens `new GuiChat(prefix + text)` on the client
  thread; the text is a draft and is not sent automatically.
- `AUTO_SEND`: records `recordCycleMs` cycles and sends every non-empty result.

Status messages use `GuiIngame.func_110326_a(text, true)`, the action-bar
overlay used by 1.7.10 for "Now playing" messages.

## Localization notes (1.7.10)

1.7.10 reads language files as **UTF-8** (`Locale.loadLocaleData` uses
`Charsets.UTF_8`), but they must be plain text `.lang` files, not JSON. Keep the
files UTF-8 encoded without a BOM and use `key=value` lines. Windows
PowerShell 5.1 `Set-Content` writes ANSI (GBK on a Chinese system) by default
and will corrupt non-ASCII translations, so edit these files with a UTF-8
capable editor (or `Set-Content -Encoding UTF8` followed by removing the BOM).

## Testing checklist

1. `./gradlew build` produces `build/libs/mcmti-3.1.0.jar` (~112 MB, contains
   `org/vosk`, `com/sun/jna`, `win32-x86-64/libvosk.dll` and the two model
   zips).
2. Start a dev client (`./gradlew runClient`, or add the jar to a GTNH 2.9.0
   instance). The log must show:
   ```
   [mcmti-asr/INFO] Speech model vosk-model-small-... extracted (...)
   [mcmti-asr/INFO] Vosk model loaded from .../config/mcmti/models/...
   ```
3. Enter a world, press `V`, speak and release. The text appears in chat (or in
   the chat draft in `RELEASE_KEY_TO_INPUT` mode).
4. Open **Mods → Microphone Text Input → Config**, change the language, press
   *Save and reload model*; the log should show the other model being loaded.
5. Remove `config/mcmti/models` and verify the model is extracted again on the
   next start.
6. Switch the game language to Chinese and check Options → Controls: the
   category must read 麦克风语音识别文本输入 and the key 录音并识别 (no
   replacement characters).
