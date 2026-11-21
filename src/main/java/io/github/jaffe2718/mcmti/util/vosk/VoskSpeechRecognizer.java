package io.github.jaffe2718.mcmti.util.vosk;

import java.io.File;
import java.io.IOException;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.vosk.Model;
import org.vosk.Recognizer;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;
import io.github.jaffe2718.mcmti.util.SpeechRecognizer;

/**
 * The bundled Vosk speech recognizer.
 * <p>
 * It uses the model that matches {@link McmtiConfig#resolvedLanguageCode()},
 * both models ship inside the mod jar and are extracted on first use.
 */
public class VoskSpeechRecognizer extends SpeechRecognizer {

    private volatile Model model;
    private volatile Recognizer recognizer;
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
        return super.available() && this.model != null && this.recognizer != null;
    }

    @Override
    protected void activate() throws IOException {
        this.deactivate();
        String language = McmtiConfig.resolvedLanguageCode();
        MicrophoneTextInput.LOGGER.info("Loading Vosk model for language '{}'", language);
        File directory = VoskModelManager.ensureModel(language);
        Model newModel = null;
        Recognizer newRecognizer = null;
        try {
            newModel = new Model(directory.getAbsolutePath());
            newRecognizer = new Recognizer(newModel, 16000.0F);
            newRecognizer.setWords(false);
        } catch (Throwable t) {
            closeQuietly(newRecognizer, newModel);
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("Failed to load the Vosk model from " + directory, t);
        }
        this.model = newModel;
        this.recognizer = newRecognizer;
        this.languageCode = language;
        MicrophoneTextInput.LOGGER.info("Vosk model loaded from {}", directory.getAbsolutePath());
        super.activate();
    }

    @Override
    protected void deactivate() {
        Recognizer oldRecognizer = this.recognizer;
        Model oldModel = this.model;
        this.recognizer = null;
        this.model = null;
        closeQuietly(oldRecognizer, oldModel);
        super.deactivate();
    }


    @Override
    public String transcribe(float[] audio) {
        Recognizer current = this.recognizer;
        if (current == null || audio == null || audio.length == 0) {
            return "";
        }
        byte[] pcm = toPcm16(audio);
        try {
            current.acceptWaveForm(pcm, pcm.length);
            String json = current.getFinalResult();
            String text = JsonText.extractText(json);
            current.reset();
            if ("zh".equals(this.languageCode)) {
                text = text.replaceAll("\\s+", "");
            }
            return text == null ? "" : text.trim();
        } catch (Throwable t) {
            MicrophoneTextInput.LOGGER.error("Vosk transcription failed", t);
            return "";
        }
    }

    private static byte[] toPcm16(float[] audio) {
        byte[] pcm = new byte[audio.length * 2];
        for (int i = 0; i < audio.length; i++) {
            float sample = Math.max(-1.0F, Math.min(1.0F, audio[i]));
            int value = Math.round(sample * 32767.0F);
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return pcm;
    }

    private static void closeQuietly(Recognizer recognizer, Model model) {
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Throwable ignored) {
                // ignore
            }
        }
        if (model != null) {
            try {
                model.close();
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }
}
