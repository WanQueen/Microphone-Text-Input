package io.github.jaffe2718.mcmti.util.vosk;

/**
 * Narrow interface between the mod code and Vosk.
 * <p>
 * The implementation lives in an isolated class loader together with Vosk and
 * JNA (see {@link VoskEngineLoader}); this interface itself must stay free of
 * any Vosk/JNA types so that it can be loaded by the normal mod class loader.
 */
public interface VoskEngine {

    void open(String modelDirectory, String languageCode) throws Exception;

    String transcribe(float[] samples);

    void close();
}
