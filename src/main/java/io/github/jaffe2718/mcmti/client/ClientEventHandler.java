package io.github.jaffe2718.mcmti.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.resources.I18n;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;
import io.github.jaffe2718.mcmti.util.AudioRecorder;
import io.github.jaffe2718.mcmti.util.KeyState;
import io.github.jaffe2718.mcmti.util.SpeechRecognizer;

/**
 * Drives the recording / recognition state machine from the client tick.
 */
public class ClientEventHandler {

    private volatile boolean recording;
    private long lastAutoCycle;
    private int unavailableNoticeTicks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) {
            return;
        }

        if (minecraft.currentScreen == null) {
            if (McmtiConfig.showStatus) {
                showStatus(minecraft);
            }
            if (!AudioRecorder.isAvailable()) {
                return;
            }
            if (McmtiConfig.mode == McmtiConfig.Mode.AUTO_SEND) {
                handleAutoSend(minecraft);
            } else {
                handlePushToTalk(minecraft);
            }
        }
    }

    private void handlePushToTalk(Minecraft minecraft) {
        boolean down = KeyState.isRecognizeKeyDown();
        if (down && !this.recording) {
            if (SpeechRecognizer.isLoading()) {
                notify(minecraft, I18n.format("message.mcmti.modelLoading"));
                return;
            }
            if (!SpeechRecognizer.instanceAvailable()) {
                if (this.unavailableNoticeTicks++ % 100 == 0) {
                    notify(minecraft, SpeechRecognizer.instanceUnavailableToast());
                }
                return;
            }
            this.recording = true;
            final boolean draft = McmtiConfig.mode == McmtiConfig.Mode.RELEASE_KEY_TO_INPUT;
            MicrophoneTextInput.RECORD_EXECUTOR.submit(() -> {
                float[] audio = new float[0];
                try {
                    audio = AudioRecorder.recordUntilReleased();
                } catch (Throwable t) {
                    MicrophoneTextInput.LOGGER.error("Failed to record audio", t);
                } finally {
                    this.recording = false;
                }
                if (audio.length < 1600) {
                    return;
                }
                final float[] samples = audio;
                MicrophoneTextInput.ASR_EXECUTOR.submit(() -> {
                    String text = SpeechRecognizer.recognize(samples);
                    if (text != null && text.length() > 0) {
                        deliver(minecraft, text, draft);
                    }
                });
            });
        } else if (down) {
            notify(minecraft, I18n.format("message.mcmti.recordingAudio"));
        }
    }

    private void handleAutoSend(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        if (this.recording || now - this.lastAutoCycle < McmtiConfig.recordCycleMs) {
            return;
        }
        this.lastAutoCycle = now;
        this.recording = true;
        MicrophoneTextInput.RECORD_EXECUTOR.submit(() -> {
            float[] audio = new float[0];
            try {
                audio = AudioRecorder.recordCycle();
            } catch (Throwable t) {
                MicrophoneTextInput.LOGGER.error("Failed to record audio", t);
            } finally {
                this.recording = false;
            }
            if (audio.length < 1600) {
                return;
            }
            final float[] samples = audio;
            MicrophoneTextInput.ASR_EXECUTOR.submit(() -> {
                String text = SpeechRecognizer.recognize(samples);
                if (text != null && text.length() > 0) {
                    deliver(minecraft, text, false);
                }
            });
        });
    }

    private void deliver(final Minecraft minecraft, final String text, final boolean draft) {
        minecraft.func_152344_a(() -> {
            if (draft) {
                String draftText = McmtiConfig.prefix + text;
                if (draftText.length() > chatLimit()) {
                    draftText = draftText.substring(0, chatLimit());
                }
                minecraft.displayGuiScreen(new GuiChat(draftText));
                return;
            }
            sendChat(minecraft, text);
            if (McmtiConfig.showStatus) {
                minecraft.ingameGUI.func_110326_a(I18n.format("message.mcmti.messageSent"), true);
            }
        });
    }

    /** Vanilla 1.7.10 disconnects players that send more than 100 characters. */
    private static int chatLimit() {
        return Math.max(8, Math.min(McmtiConfig.maxMessageLength, 100));
    }

    /** Split the recognized text into messages that fit into one chat line. */
    private static void sendChat(Minecraft minecraft, String text) {
        if (minecraft.thePlayer == null) {
            return;
        }
        String prefix = McmtiConfig.prefix == null ? "" : McmtiConfig.prefix;
        int max = Math.max(8, chatLimit() - prefix.length());
        String remaining = text;
        while (remaining.length() > max) {
            minecraft.thePlayer.sendChatMessage(prefix + remaining.substring(0, max));
            remaining = remaining.substring(max);
        }
        if (remaining.length() > 0) {
            minecraft.thePlayer.sendChatMessage(prefix + remaining);
        }
    }

    private void showStatus(Minecraft minecraft) {
        String message = null;
        if (this.recording || KeyState.isRecognizeKeyDown()) {
            message = I18n.format("message.mcmti.recordingAudio");
        } else if (SpeechRecognizer.isBusy()) {
            message = I18n.format("message.mcmti.recognizing");
        } else if (SpeechRecognizer.isLoading()) {
            message = I18n.format("message.mcmti.modelLoading");
        } else if (!SpeechRecognizer.instanceAvailable()) {
            message = SpeechRecognizer.instanceUnavailableToast();
        }
        if (message != null) {
            minecraft.ingameGUI.func_110326_a(message, true);
        }
    }

    private static void notify(Minecraft minecraft, String message) {
        minecraft.ingameGUI.func_110326_a(message, true);
    }
}
