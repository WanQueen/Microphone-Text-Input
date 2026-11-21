package io.github.jaffe2718.mcmti.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;

/**
 * Registry and lifecycle manager for speech recognizers.
 * <p>
 * Other mods may register their own {@link SpeechRecognizer} implementation
 * through {@link #register(int, ResourceLocation, Factory)}. The recognizer
 * with the smallest priority value that is {@link #enabled()} is the one that
 * gets activated and used.
 */
public abstract class SpeechRecognizer {

    /** Creates a recognizer instance for the given registry id. */
    public interface Factory {

        SpeechRecognizer create(ResourceLocation id);
    }

    private static final Map<Integer, SpeechRecognizer> REGISTRY = new TreeMap<Integer, SpeechRecognizer>();
    private static final Map<ResourceLocation, Integer> REGISTERED_IDS = new HashMap<ResourceLocation, Integer>();

    private static volatile int instancePriority = Integer.MAX_VALUE;
    private static volatile boolean loading = false;
    private static volatile boolean busy = false;

    /** The id this recognizer was registered with. */
    public final ResourceLocation id;

    private volatile boolean active;

    protected SpeechRecognizer(ResourceLocation id) {
        this.id = id;
    }

    /** Whether this recognizer should be used (config, availability of a model, ...). */
    public abstract boolean enabled();

    /** Transcribe the given mono 16 kHz samples into text. */
    public abstract String transcribe(float[] audio);

    /** Human readable status shown when the recognizer became available. */
    protected String availableToast() {
        return I18n.format("message.mcmti.recognizerAvailable");
    }

    /** Human readable status shown when the recognizer is not usable. */
    protected String unavailableToast() {
        return I18n.format("message.mcmti.noRecognizerAvailable");
    }

    /** Load the model / allocate the resources. */
    protected void activate() throws IOException {
        this.active = true;
        MicrophoneTextInput.LOGGER.info("Speech recognizer {} activated", this);
    }

    /** Release the model / free the resources. */
    protected void deactivate() {
        this.active = false;
        MicrophoneTextInput.LOGGER.info("Speech recognizer {} deactivated", this);
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean available() {
        return this.enabled() && this.active;
    }

    @Override
    public String toString() {
        return String.format(
            "%s[hash=0x%X,id=%s,active=%s]",
            this.getClass()
                .getSimpleName(),
            System.identityHashCode(this),
            this.id,
            this.active);
    }

    public static void register(int priority, ResourceLocation id, Factory factory) throws IllegalStateException {
        if (id == null || factory == null) {
            throw new IllegalArgumentException("Recognizer id and factory must not be null");
        }
        if (REGISTERED_IDS.containsKey(id)) {
            throw new IllegalStateException(String.format("A speech recognizer with id \"%s\" is already registered", id));
        }
        SpeechRecognizer recognizer = factory.create(id);
        if (recognizer == null) {
            throw new IllegalStateException("Recognizer factory returned null for " + id);
        }
        int actualPriority = priority;
        while (REGISTRY.containsKey(actualPriority)) {
            actualPriority++;
        }
        REGISTRY.put(actualPriority, recognizer);
        REGISTERED_IDS.put(id, actualPriority);
        if (actualPriority < instancePriority && recognizer.enabled()) {
            instancePriority = actualPriority;
        }
        MicrophoneTextInput.LOGGER.info("Speech recognizer {} registered with priority {}", recognizer, actualPriority);
    }

    /** Activate the first enabled recognizer and deactivate all others. */
    public static synchronized void init() {
        loading = true;
        try {
            boolean firstActivated = false;
            for (Map.Entry<Integer, SpeechRecognizer> entry : REGISTRY.entrySet()) {
                SpeechRecognizer recognizer = entry.getValue();
                if (!firstActivated && recognizer.enabled()) {
                    instancePriority = entry.getKey();
                    firstActivated = true;
                    try {
                        recognizer.activate();
                    } catch (Exception e) {
                        MicrophoneTextInput.LOGGER.error("Failed to activate speech recognizer {}", recognizer, e);
                    }
                } else {
                    try {
                        recognizer.deactivate();
                    } catch (Exception e) {
                        MicrophoneTextInput.LOGGER.error("Failed to deactivate speech recognizer {}", recognizer, e);
                    }
                }
            }
        } finally {
            loading = false;
        }
    }

    /** Initialize the recognizers on the speech recognition worker thread. */
    public static void initAsync() {
        MicrophoneTextInput.ASR_EXECUTOR.submit(SpeechRecognizer::init);
    }

    /** Reload the active recognizer (for example after the config changed). */
    public static void reloadAsync() {
        MicrophoneTextInput.ASR_EXECUTOR.submit(() -> {
            MicrophoneTextInput.LOGGER.info("Reloading speech recognizer");
            init();
        });
    }

    public static void deregisterAll() {
        synchronized (SpeechRecognizer.class) {
            for (SpeechRecognizer recognizer : REGISTRY.values()) {
                try {
                    recognizer.deactivate();
                } catch (Exception ignored) {
                    // ignore
                }
            }
            REGISTRY.clear();
            REGISTERED_IDS.clear();
            instancePriority = Integer.MAX_VALUE;
        }
    }

    /** Recognize the given samples with the active recognizer. */
    public static String recognize(float[] audio) {
        SpeechRecognizer recognizer = REGISTRY.get(instancePriority);
        if (recognizer == null || !recognizer.enabled()) {
            MicrophoneTextInput.LOGGER.warn("No enabled speech recognizer found");
            return "";
        }
        if (!recognizer.available()) {
            try {
                recognizer.activate();
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.error("Failed to activate speech recognizer", e);
                return "";
            }
        }
        busy = true;
        try {
            String transcription = recognizer.transcribe(audio);
            if (transcription == null) {
                return "";
            }
            transcription = transcription.trim();
            if (McmtiConfig.encodingRepair) {
                transcription = repairEncoding(transcription, McmtiConfig.srcEncoding, McmtiConfig.dstEncoding);
            }
            return transcription;
        } catch (Throwable t) {
            MicrophoneTextInput.LOGGER.error("Speech recognition failed", t);
            return "";
        } finally {
            busy = false;
        }
    }

    public static boolean instanceAvailable() {
        SpeechRecognizer recognizer = REGISTRY.get(instancePriority);
        return recognizer != null && recognizer.available();
    }

    public static boolean isLoading() {
        return loading;
    }

    public static boolean isBusy() {
        return busy;
    }

    public static String instanceUnavailableToast() {
        SpeechRecognizer recognizer = REGISTRY.get(instancePriority);
        return recognizer != null ? recognizer.unavailableToast() : I18n.format("message.mcmti.noRecognizerAvailable");
    }

    public static String instanceAvailableToast() {
        SpeechRecognizer recognizer = REGISTRY.get(instancePriority);
        return recognizer != null ? recognizer.availableToast() : I18n.format("message.mcmti.recognizerAvailable");
    }

    /**
     * Status key used by the config GUI: {@code loading}, {@code ready},
     * {@code unavailable} or {@code none}.
     */
    public static String instanceStatusKey() {
        if (loading) {
            return "loading";
        }
        SpeechRecognizer recognizer = REGISTRY.get(instancePriority);
        if (recognizer == null) {
            return "none";
        }
        if (recognizer.available()) {
            return "ready";
        }
        return "unavailable";
    }

    public static ResourceLocation getInstanceId() {
        SpeechRecognizer recognizer = REGISTRY.get(instancePriority);
        return recognizer == null ? null : recognizer.id;
    }

    public static int queryPriority(ResourceLocation id) {
        Integer priority = REGISTERED_IDS.get(id);
        if (priority == null) {
            throw new NoSuchElementException(String.format("Recognizer \"%s\" is not registered", id));
        }
        return priority;
    }

    private static String repairEncoding(String text, String source, String destination) {
        try {
            return new String(text.getBytes(source), destination);
        } catch (UnsupportedEncodingException e) {
            MicrophoneTextInput.LOGGER.error("Unknown encoding, keeping the original text", e);
            return text;
        }
    }
}
