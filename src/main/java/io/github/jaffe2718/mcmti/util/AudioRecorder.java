package io.github.jaffe2718.mcmti.util;

import java.io.ByteArrayOutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;

/**
 * Records the default microphone as 16 kHz, 16 bit, mono, signed little endian
 * PCM, which is what Vosk expects.
 */
public final class AudioRecorder {

    private static final AudioFormat FORMAT = new AudioFormat(16000.0F, 16, 1, true, false);
    /** Hard upper limit for a single recording, to avoid filling the heap. */
    private static final int MAX_RECORDING_BYTES = 16000 * 2 * 120;

    private static volatile TargetDataLine line;

    private AudioRecorder() {}

    public static synchronized void init() {
        destroy();
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                MicrophoneTextInput.LOGGER.error("No microphone supporting 16 kHz mono PCM was found");
                return;
            }
            TargetDataLine newLine = (TargetDataLine) AudioSystem.getLine(info);
            newLine.open(FORMAT);
            line = newLine;
            MicrophoneTextInput.LOGGER.info("Microphone initialized: {}", newLine.getLineInfo());
        } catch (Exception e) {
            MicrophoneTextInput.LOGGER.error("Failed to initialize the microphone", e);
            line = null;
        }
    }

    public static synchronized void destroy() {
        TargetDataLine current = line;
        line = null;
        if (current != null) {
            try {
                current.stop();
                current.close();
            } catch (Exception ignored) {
                // closing the device must never crash the game
            }
        }
    }

    public static boolean isAvailable() {
        return line != null;
    }

    private static float[] toFloatArray(byte[] data, int length) {
        int samples = length / 2;
        float[] result = new float[samples];
        for (int i = 0; i < samples; i++) {
            int low = data[i * 2] & 0xFF;
            int high = data[i * 2 + 1];
            short value = (short) ((high << 8) | low);
            result[i] = Math.max(-1.0F, Math.min(value / 32768.0F, 1.0F));
        }
        return result;
    }

    /**
     * Record exactly one cycle ({@link McmtiConfig#recordCycleMs}) and return
     * the samples.
     */
    public static float[] recordCycle() {
        TargetDataLine current = line;
        if (current == null) {
            return new float[0];
        }
        int bytes = Math.max(3200, McmtiConfig.recordCycleMs * 32);
        byte[] buffer = new byte[bytes];
        synchronized (AudioRecorder.class) {
            current = line;
            if (current == null) {
                return new float[0];
            }
            try {
                current.start();
                int read = 0;
                while (read < buffer.length) {
                    int n = current.read(buffer, read, buffer.length - read);
                    if (n <= 0) {
                        break;
                    }
                    read += n;
                }
                return toFloatArray(buffer, read);
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.error("Failed to record audio", e);
                return new float[0];
            } finally {
                try {
                    current.stop();
                    current.flush();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * Record until the recognize key is released (or the hard limit is hit).
     */
    public static float[] recordUntilReleased() {
        TargetDataLine current = line;
        if (current == null) {
            return new float[0];
        }
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] chunk = new byte[Math.max(64, McmtiConfig.recordBufferSize)];
        synchronized (AudioRecorder.class) {
            current = line;
            if (current == null) {
                return new float[0];
            }
            try {
                current.start();
                while (KeyState.isRecognizeKeyDown() && collected.size() < MAX_RECORDING_BYTES) {
                    int n = current.read(chunk, 0, chunk.length);
                    if (n <= 0) {
                        break;
                    }
                    collected.write(chunk, 0, n);
                }
                return toFloatArray(collected.toByteArray(), collected.size());
            } catch (Exception e) {
                MicrophoneTextInput.LOGGER.error("Failed to record audio", e);
                return new float[0];
            } finally {
                try {
                    current.stop();
                    current.flush();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }
}
