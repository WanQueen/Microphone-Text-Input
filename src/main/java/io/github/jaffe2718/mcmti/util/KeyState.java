package io.github.jaffe2718.mcmti.util;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import io.github.jaffe2718.mcmti.client.ClientProxy;

/**
 * Polls the physical state of the recognize key independently of the current
 * screen, so a recording is also stopped when a screen opens.
 */
public final class KeyState {

    private KeyState() {}

    public static boolean isRecognizeKeyDown() {
        KeyBinding binding = ClientProxy.RECOGNIZE_KEY;
        if (binding == null) {
            return false;
        }
        int keyCode = binding.getKeyCode();
        if (keyCode <= 0) {
            return false;
        }
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (Throwable t) {
            return binding.getIsKeyPressed();
        }
    }
}
