package io.github.jaffe2718.mcmti;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Server side / common proxy. The mod is client only, so this is a no-op; it
 * exists so that the client-only classes are never loaded on a dedicated
 * server.
 */
public class CommonProxy {

    public void init(FMLPreInitializationEvent event) {
        // nothing to do on the server
    }

    public void shutdown() {
        // nothing to do on the server
    }
}
