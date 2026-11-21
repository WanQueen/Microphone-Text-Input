package io.github.jaffe2718.mcmti.client.gui;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;

/**
 * Adds the "Config" button of the mod to the Forge mod list.
 */
public class McmtiGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // nothing to initialize
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return GuiMcmtiConfig.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}
