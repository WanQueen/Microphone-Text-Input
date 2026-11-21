package io.github.jaffe2718.mcmti.util.vosk;

import java.io.File;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;
import io.github.jaffe2718.mcmti.util.SpeechRecognizer;

/**
 * The bundled Vosk speech recognizer.
 * <p>
 * Vosk itself runs in an isolated class loader (see {@link VoskEngineLoader}),
 * because JNA's reflective field access is broken by RetroFuturaBootstrap on
 * GTNH. This class only talks to the {@link VoskEngine} interface.
 */
public class VoskSpeechRecognizer extends SpeechRecognizer {

    private volatile VoskEngine engine;
    private volatile String languageCode = "en";

    public VoskSpeechRecognizer(ResourceLocation id) {
        super(id);
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    protected String availableToast() {
        return I18n.format("message.mcmti.modelLoaded");
    }

    @Override
    protected String unavailableToast() {
        return I18n.format("message.mcmti.modelLoadFailed");
    }

    @Override
    public boolean available() {
        return super.available() && this.engine != null;
    }

    @Override
    protected void activate() throws IOException {
        this.deactivate();
        String language = resolveLanguage();
        MicrophoneTextInput.LOGGER.info("Loading Vosk model for language '{}'", language);
        File directory = VoskModelManager.ensureModel(language);
        VoskEngine newEngine = null;
        try {
            newEngine = VoskEngineLoader.create();
            newEngine.open(directory.getAbsolutePath(), language);
        } catch (Throwable t) {
            if (newEngine != null) {
                newEngine.close();
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("Failed to load the Vosk model from " + directory, t);
        }
        this.engine = newEngine;
        this.languageCode = language;
        MicrophoneTextInput.LOGGER.info("Vosk model loaded from {}", directory.getAbsolutePath());
        super.activate();
    }

    @Override
    protected void deactivate() {
        VoskEngine oldEngine = this.engine;
        this.engine = null;
        if (oldEngine != null) {
            try {
                oldEngine.close();
            } catch (Throwable ignored) {
                // ignore
            }
        }
        super.deactivate();
    }

    @Override
    public String transcribe(float[] audio) {
        VoskEngine current = this.engine;
        if (current == null || audio == null || audio.length == 0) {
            return "";
        }
        try {
            String text = current.transcribe(audio);
            return text == null ? "" : text;
        } catch (Throwable t) {
            MicrophoneTextInput.LOGGER.error("Vosk transcription failed", t);
            return "";
        }
    }

    public String getLanguageCode() {
        return this.languageCode;
    }

    /**
     * Resolve the language used for recognition. The language selected in the
     * Minecraft options wins over the JVM locale, because GTNH and many
     * launchers force user.language=en even on a Chinese system.
     */
    private static String resolveLanguage() {
        switch (McmtiConfig.language) {
            case CHINESE:
                return "zh";
            case ENGLISH:
                return "en";
            case AUTO:
            default:
                break;
        }
        try {
            String gameLanguage = Minecraft.getMinecraft()
                .getLanguageManager()
                .getCurrentLanguage()
                .getLanguageCode();
            if (gameLanguage != null) {
                String lower = gameLanguage.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("zh")) {
                    return "zh";
                }
                if (lower.startsWith("en")) {
                    return "en";
                }
            }
        } catch (Throwable ignored) {
            // not on the client thread yet, fall back to the JVM locale
        }
        return "zh".equalsIgnoreCase(java.util.Locale.getDefault().getLanguage()) ? "zh" : "en";
    }
}
