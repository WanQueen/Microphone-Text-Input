package io.github.jaffe2718.mcmti.util.vosk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;

/**
 * Locates and, on first use, extracts the Vosk models bundled with the mod.
 * <p>
 * The models are stored as zip archives inside the jar so the download is a
 * single file and the model is available without any manual configuration.
 * They are extracted to {@code config/mcmti/models/<model name>} once.
 */
public final class VoskModelManager {

    /** Bundled Chinese model (Vosk small Chinese, 0.22). */
    public static final String MODEL_ZH = "vosk-model-small-cn-0.22";
    /** Bundled English model (Vosk small US English, 0.15). */
    public static final String MODEL_EN = "vosk-model-small-en-us-0.15";

    private static final String RESOURCE_PREFIX = "/assets/mcmti/models/";
    private static final String MARKER = ".mcmti-model";

    private VoskModelManager() {}

    public static File getModelsDirectory() {
        return new File(McmtiConfig.getConfigDirectory(), "mcmti/models");
    }

    /**
     * Ensure that the model for the given language is available on disk and
     * return its directory.
     *
     * @param languageCode {@code zh} or {@code en}
     */
    public static File ensureModel(String languageCode) throws IOException {
        String customPath = McmtiConfig.modelPath;
        if (customPath != null && customPath.trim()
            .length() > 0) {
            File custom = new File(customPath.trim());
            if (isValidModel(custom)) {
                MicrophoneTextInput.LOGGER.info("Using custom Vosk model at {}", custom.getAbsolutePath());
                return custom;
            }
            MicrophoneTextInput.LOGGER.warn("Custom Vosk model at {} is not usable, falling back to the bundled model", custom.getAbsolutePath());
        }

        String modelName = "zh".equalsIgnoreCase(languageCode) ? MODEL_ZH : MODEL_EN;
        File target = new File(getModelsDirectory(), modelName);
        if (isValidModel(target) && new File(target, MARKER).isFile()) {
            return target;
        }
        extract(modelName, target);
        return target;
    }

    public static boolean isValidModel(File directory) {
        return directory != null && directory.isDirectory()
            && new File(directory, "conf/model.conf").isFile()
            && new File(directory, "am/final.mdl").isFile();
    }

    private static void extract(String modelName, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create model directory " + parent);
        }
        File temporary = new File(parent, modelName + ".tmp");
        deleteRecursively(temporary);

        String resource = RESOURCE_PREFIX + modelName + ".zip";
        InputStream raw = VoskModelManager.class.getResourceAsStream(resource);
        if (raw == null) {
            throw new IOException("Bundled speech model " + resource + " is missing from the mod jar");
        }

        MicrophoneTextInput.LOGGER.info("Extracting bundled speech model {} to {}", modelName, target.getAbsolutePath());
        long start = System.currentTimeMillis();
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!entryName.startsWith(modelName + "/")) {
                    continue;
                }
                String relative = entryName.substring(modelName.length() + 1);
                if (relative.isEmpty()) {
                    continue;
                }
                File destination = new File(temporary, relative);
                if (entry.isDirectory()) {
                    if (!destination.isDirectory() && !destination.mkdirs()) {
                        throw new IOException("Could not create " + destination);
                    }
                    continue;
                }
                File destinationParent = destination.getParentFile();
                if (destinationParent != null && !destinationParent.isDirectory() && !destinationParent.mkdirs()) {
                    throw new IOException("Could not create " + destinationParent);
                }
                try (OutputStream out = new FileOutputStream(destination)) {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                entries++;
            }
        } catch (IOException e) {
            deleteRecursively(temporary);
            throw e;
        }

        if (!new File(temporary, "conf/model.conf").isFile()) {
            deleteRecursively(temporary);
            throw new IOException("The bundled model archive did not contain a valid Vosk model");
        }
        writeMarker(temporary);

        deleteRecursively(target);
        if (!temporary.renameTo(target)) {
            deleteRecursively(target);
            throw new IOException("Could not move the extracted model to " + target);
        }
        MicrophoneTextInput.LOGGER.info(
            "Speech model {} extracted ({} files, {} ms)",
            modelName,
            entries,
            System.currentTimeMillis() - start);
    }

    private static void writeMarker(File directory) {
        try (OutputStream out = new FileOutputStream(new File(directory, MARKER))) {
            out.write(
                ("Microphone Text Input bundled model\n").getBytes("UTF-8"));
        } catch (IOException e) {
            MicrophoneTextInput.LOGGER.warn("Could not write the model marker file", e);
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
