package io.github.jaffe2718.mcmti.util.vosk;

import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Vosk engine implementation. This class is loaded by the isolated class
 * loader created in {@link VoskEngineLoader}, never by the mod class loader,
 * so that RetroFuturaBootstrap does not rewrite JNA's reflective field access.
 */
public final class IsolatedVoskEngine implements VoskEngine {

    private Model model;
    private Recognizer recognizer;
    private boolean chinese;

    @Override
    public void open(String modelDirectory, String languageCode) throws Exception {
        close();
        // Vosk returns UTF-8 JSON. JNA converts the native char* to String
        // with the platform encoding (GBK on Chinese Windows), which turns
        // Chinese results into mojibake. JNA reads the encoding once when its
        // Native class is initialised, so temporarily force UTF-8 here.
        String previousEncoding = System.getProperty("jna.encoding");
        System.setProperty("jna.encoding", "UTF-8");
        try {
            this.model = new Model(modelDirectory);
            this.recognizer = new Recognizer(this.model, 16000.0F);
            this.recognizer.setWords(false);
        } finally {
            if (previousEncoding == null) {
                System.clearProperty("jna.encoding");
            } else {
                System.setProperty("jna.encoding", previousEncoding);
            }
        }
        this.chinese = "zh".equalsIgnoreCase(languageCode);
    }

    @Override
    public String transcribe(float[] samples) {
        Recognizer current = this.recognizer;
        if (current == null || samples == null || samples.length == 0) {
            return "";
        }
        byte[] pcm = toPcm16(samples);
        try {
            current.acceptWaveForm(pcm, pcm.length);
            String result = JsonText.extractText(current.getFinalResult());
            current.reset();
            if (this.chinese) {
                result = result.replaceAll("\\s+", "");
            }
            return result == null ? "" : result.trim();
        } catch (Throwable t) {
            throw new RuntimeException("Vosk transcription failed", t);
        }
    }

    @Override
    public void close() {
        Recognizer oldRecognizer = this.recognizer;
        Model oldModel = this.model;
        this.recognizer = null;
        this.model = null;
        if (oldRecognizer != null) {
            try {
                oldRecognizer.close();
            } catch (Throwable ignored) {
                // ignore
            }
        }
        if (oldModel != null) {
            try {
                oldModel.close();
            } catch (Throwable ignored) {
                // ignore
            }
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
}
