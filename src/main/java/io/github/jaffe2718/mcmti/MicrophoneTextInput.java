package io.github.jaffe2718.mcmti;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.jaffe2718.mcmti.config.McmtiConfig;

/**
 * Microphone Text Input - speech recognition input for Minecraft chat.
 * <p>
 * Press the recognize key (default {@code V}), speak, and release the key: the
 * recorded audio is transcribed locally by Vosk and either sent to chat or put
 * into the chat draft, depending on the configured mode.
 * <p>
 * This class is side neutral; all client-only wiring lives behind the
 * {@link SidedProxy} so that a dedicated server does not load client classes.
 */
@Mod(
    modid = MicrophoneTextInput.MOD_ID,
    name = "Microphone Text Input",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    guiFactory = "io.github.jaffe2718.mcmti.client.gui.McmtiGuiFactory")
public class MicrophoneTextInput {

    public static final String MOD_ID = "mcmti";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @SidedProxy(
        clientSide = "io.github.jaffe2718.mcmti.client.ClientProxy",
        serverSide = "io.github.jaffe2718.mcmti.CommonProxy")
    public static CommonProxy proxy;

    /** Worker that reads the microphone. */
    public static final ExecutorService RECORD_EXECUTOR = Executors.newSingleThreadExecutor(daemonFactory("mcmti-recorder"));
    /** Worker that loads models and runs speech recognition. */
    public static final ExecutorService ASR_EXECUTOR = Executors.newSingleThreadExecutor(daemonFactory("mcmti-asr"));

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        McmtiConfig.load(event.getSuggestedConfigurationFile());
        proxy.init(event);
        Runtime.getRuntime()
            .addShutdownHook(new Thread(() -> {
                try {
                    proxy.shutdown();
                } catch (Throwable ignored) {
                    // ignore
                }
            }, "mcmti-shutdown"));
        LOGGER.info("Microphone Text Input initialized");
    }

    private static ThreadFactory daemonFactory(final String name) {
        return new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
