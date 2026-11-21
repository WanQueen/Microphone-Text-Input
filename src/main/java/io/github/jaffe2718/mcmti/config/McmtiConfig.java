package io.github.jaffe2718.mcmti.config;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;

/**
 * Configuration of the mod.
 * <p>
 * The configuration is stored in {@code config/mcmti.cfg} (the usual Forge
 * configuration file) and can be edited either in game through the config GUI
 * (Mods list -&gt; Microphone Text Input -&gt; Config) or by hand.
 * <p>
 * Every value has a sensible default: the mod works out of the box without any
 * manual setup, including the speech model which is bundled with the jar.
 */
public final class McmtiConfig {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_ADVANCED = "advanced";

    /** How the recognized text is delivered. */
    public enum Mode {
        /** Record continuously in cycles and send every result automatically. */
        AUTO_SEND,
        /** Hold the key while speaking, release it to send the text immediately. */
        RELEASE_KEY_TO_SEND,
        /** Hold the key while speaking, release it to put the text into the chat draft. */
        RELEASE_KEY_TO_INPUT;

        public Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Mode fromString(String value) {
            if (value != null) {
                for (Mode mode : values()) {
                    if (mode.name().equalsIgnoreCase(value.trim())) {
                        return mode;
                    }
                }
            }
            return RELEASE_KEY_TO_SEND;
        }
    }

    /** Language used for recognition. {@code auto} follows the system locale. */
    public enum Language {
        AUTO("auto"),
        CHINESE("zh"),
        ENGLISH("en");

        private final String code;

        Language(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public Language next() {
            Language[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Language fromString(String value) {
            if (value != null) {
                for (Language language : values()) {
                    if (language.getCode().equalsIgnoreCase(value.trim())) {
                        return language;
                    }
                }
            }
            return AUTO;
        }
    }

    // ------------------------------------------------------------------
    // General settings
    // ------------------------------------------------------------------

    public static Mode mode = Mode.RELEASE_KEY_TO_SEND;
    public static Language language = Language.AUTO;
    /** Custom Vosk model directory. Empty means "use the bundled model". */
    public static String modelPath = "";
    /** Recording cycle for AUTO_SEND, in milliseconds. */
    public static int recordCycleMs = 5000;
    /** Read buffer used while holding the key, in bytes. */
    public static int recordBufferSize = 1024;
    /** Text prepended to every recognized message. */
    public static String prefix = "[Speech] ";
    /** Maximum length of a single chat message (vanilla 1.7.10 limit is 100). */
    public static int maxMessageLength = 100;
    /** Show the recording / recognition status above the hotbar. */
    public static boolean showStatus = true;

    // ------------------------------------------------------------------
    // Advanced settings
    // ------------------------------------------------------------------

    public static boolean encodingRepair = false;
    public static String srcEncoding = Charset.defaultCharset().displayName();
    public static String dstEncoding = Charset.defaultCharset().displayName();

    private static Configuration configuration;

    private McmtiConfig() {}

    public static void load(File file) {
        if (configuration == null) {
            configuration = new Configuration(file);
        }
        configuration.load();
        configuration.getCategory(CATEGORY_GENERAL)
            .setLanguageKey("mcmti.config.category.general");
        configuration.getCategory(CATEGORY_ADVANCED)
            .setLanguageKey("mcmti.config.category.advanced");

        mode = Mode.fromString(
            getString(
                CATEGORY_GENERAL,
                "mode",
                Mode.RELEASE_KEY_TO_SEND.name(),
                "How the recognized text is delivered: AUTO_SEND, RELEASE_KEY_TO_SEND or RELEASE_KEY_TO_INPUT."));
        language = Language.fromString(
            getString(
                CATEGORY_GENERAL,
                "language",
                Language.AUTO.getCode(),
                "Recognition language: auto, zh or en. 'auto' follows the system language."));
        modelPath = getString(
            CATEGORY_GENERAL,
            "modelPath",
            "",
            "Custom Vosk model directory. Leave empty to use the model bundled with the mod.");
        recordCycleMs = getInt(
            CATEGORY_GENERAL,
            "recordCycleMs",
            5000,
            500,
            60000,
            "Recording cycle for AUTO_SEND mode, in milliseconds.");
        recordBufferSize = getInt(
            CATEGORY_GENERAL,
            "recordBufferSize",
            1024,
            64,
            8192,
            "Read buffer size used while recording, in bytes.");
        prefix = getString(
            CATEGORY_GENERAL,
            "prefix",
            "[Speech] ",
            "Text prepended to every recognized chat message.");
        maxMessageLength = getInt(
            CATEGORY_GENERAL,
            "maxMessageLength",
            100,
            16,
            256,
            "Maximum length of a single chat message. Vanilla 1.7.10 truncates at 100 characters.");
        showStatus = getBoolean(
            CATEGORY_GENERAL,
            "showStatus",
            true,
            "Show recording and recognition status above the hotbar.");

        encodingRepair = getBoolean(
            CATEGORY_ADVANCED,
            "encodingRepair",
            false,
            "Try to repair the encoding of the recognized text.");
        srcEncoding = getString(
            CATEGORY_ADVANCED,
            "srcEncoding",
            Charset.defaultCharset().displayName(),
            "Source encoding used by the encoding repair.");
        dstEncoding = getString(
            CATEGORY_ADVANCED,
            "dstEncoding",
            Charset.defaultCharset().displayName(),
            "Destination encoding used by the encoding repair.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
        MicrophoneTextInput.LOGGER.info("Configuration loaded from {}", file.getAbsolutePath());
    }

    /** Persist the current values (called by the config GUI). */
    public static void save() {
        if (configuration == null) {
            return;
        }
        Property modeProperty = configuration.getCategory(CATEGORY_GENERAL).get("mode");
        if (modeProperty != null) {
            modeProperty.set(mode.name());
        }
        Property languageProperty = configuration.getCategory(CATEGORY_GENERAL).get("language");
        if (languageProperty != null) {
            languageProperty.set(language.getCode());
        }
        Property prefixProperty = configuration.getCategory(CATEGORY_GENERAL).get("prefix");
        if (prefixProperty != null) {
            prefixProperty.set(prefix);
        }
        configuration.save();
    }

    /**
     * Apply the values edited in the config GUI.
     */
    public static void applyGuiValues(Mode newMode, Language newLanguage, String newPrefix) {
        mode = newMode == null ? mode : newMode;
        language = newLanguage == null ? language : newLanguage;
        if (newPrefix != null) {
            prefix = newPrefix;
        }
        save();
    }

    /** The directory that holds the bundled model extraction cache. */
    public static File getConfigDirectory() {
        if (configuration != null && configuration.getConfigFile() != null) {
            return configuration.getConfigFile()
                .getParentFile();
        }
        return new File("config");
    }

    /** Resolve the effective two letter recognition language code. */
    public static String resolvedLanguageCode() {
        switch (language) {
            case CHINESE:
                return "zh";
            case ENGLISH:
                return "en";
            case AUTO:
            default:
                String systemLanguage = Locale.getDefault()
                    .getLanguage();
                return "zh".equalsIgnoreCase(systemLanguage) ? "zh" : "en";
        }
    }

    public static Configuration getConfiguration() {
        return configuration;
    }

    private static String getString(String category, String key, String defaultValue, String comment) {
        return configuration.get(category, key, defaultValue, comment)
            .getString();
    }

    private static int getInt(String category, String key, int defaultValue, int min, int max, String comment) {
        return configuration.getInt(key, category, defaultValue, min, max, comment);
    }

    private static boolean getBoolean(String category, String key, boolean defaultValue, String comment) {
        return configuration.get(category, key, defaultValue, comment)
            .getBoolean(defaultValue);
    }
}
