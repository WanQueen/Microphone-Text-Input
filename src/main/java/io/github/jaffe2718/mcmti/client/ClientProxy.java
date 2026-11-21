package io.github.jaffe2718.mcmti.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import io.github.jaffe2718.mcmti.CommonProxy;
import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.util.AudioRecorder;
import io.github.jaffe2718.mcmti.util.SpeechRecognizer;
import io.github.jaffe2718.mcmti.util.vosk.VoskSpeechRecognizer;

/**
 * Client-only setup: keybinding, tick handler, microphone and recognizers.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public static final KeyBinding RECOGNIZE_KEY = new KeyBinding("key.mcmti.recognize", Keyboard.KEY_V, "key.categories.mcmti");

    @Override
    public void init(FMLPreInitializationEvent event) {
        ClientRegistry.registerKeyBinding(RECOGNIZE_KEY);
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientEventHandler());
        AudioRecorder.init();
        SpeechRecognizer.register(0, new ResourceLocation(MicrophoneTextInput.MOD_ID, "vosk"), VoskSpeechRecognizer::new);
        SpeechRecognizer.initAsync();
    }

    @Override
    public void shutdown() {
        SpeechRecognizer.deregisterAll();
        AudioRecorder.destroy();
    }
}
