package io.github.jaffe2718.mcmti.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;
import io.github.jaffe2718.mcmti.config.McmtiConfig;
import io.github.jaffe2718.mcmti.util.SpeechRecognizer;

/**
 * In-game configuration screen of the mod.
 */
public class GuiMcmtiConfig extends GuiScreen {

    private static final int BUTTON_MODE = 0;
    private static final int BUTTON_LANGUAGE = 1;
    private static final int BUTTON_RELOAD = 2;
    private static final int BUTTON_DONE = 3;

    private final GuiScreen parent;
    private GuiTextField prefixField;
    private GuiButton modeButton;
    private GuiButton languageButton;
    private boolean modelDirty;
    private boolean closed;

    public GuiMcmtiConfig(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int center = this.width / 2;
        int left = center - 155;
        int right = center + 5;
        this.modeButton = new GuiButton(BUTTON_MODE, right, 44, 160, 20, modeLabel());
        this.languageButton = new GuiButton(BUTTON_LANGUAGE, right, 74, 160, 20, languageLabel());
        this.buttonList.add(this.modeButton);
        this.buttonList.add(this.languageButton);
        this.buttonList.add(new GuiButton(BUTTON_RELOAD, left, this.height - 52, 160, 20, I18n.format("mcmti.config.reload")));
        this.buttonList.add(new GuiButton(BUTTON_DONE, right, this.height - 52, 160, 20, I18n.format("gui.done")));

        this.prefixField = new GuiTextField(this.fontRendererObj, right, 104, 160, 20);
        this.prefixField.setMaxStringLength(32);
        this.prefixField.setText(McmtiConfig.prefix == null ? "" : McmtiConfig.prefix);
        this.prefixField.setFocused(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_MODE:
                McmtiConfig.mode = McmtiConfig.mode.next();
                this.modeButton.displayString = modeLabel();
                break;
            case BUTTON_LANGUAGE:
                McmtiConfig.language = McmtiConfig.language.next();
                this.languageButton.displayString = languageLabel();
                this.modelDirty = true;
                break;
            case BUTTON_RELOAD:
                this.modelDirty = true;
                saveAndClose();
                return;
            case BUTTON_DONE:
                saveAndClose();
                return;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int center = this.width / 2;
        int left = center - 155;
        this.drawCenteredString(this.fontRendererObj, I18n.format("mcmti.config.title"), center, 14, 0xFFFFFF);
        this.drawString(this.fontRendererObj, I18n.format("mcmti.config.mode"), left, 50, 0xA0A0A0);
        this.drawString(this.fontRendererObj, I18n.format("mcmti.config.language"), left, 80, 0xA0A0A0);
        this.drawString(this.fontRendererObj, I18n.format("mcmti.config.prefix"), left, 110, 0xA0A0A0);
        this.prefixField.drawTextBox();

        String status = I18n.format("mcmti.config.status." + SpeechRecognizer.instanceStatusKey());
        this.drawString(this.fontRendererObj, I18n.format("mcmti.config.status", status), left, this.height - 26, 0x909090);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            saveAndClose();
            return;
        }
        if (this.prefixField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.prefixField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void saveAndClose() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        McmtiConfig.applyGuiValues(
            McmtiConfig.mode,
            McmtiConfig.language,
            this.prefixField == null ? McmtiConfig.prefix : this.prefixField.getText());
        if (this.modelDirty) {
            SpeechRecognizer.reloadAsync();
        }
        MicrophoneTextInput.LOGGER.info("Configuration saved");
        this.mc.displayGuiScreen(this.parent);
    }

    private static String modeLabel() {
        return I18n.format("mcmti.config.modeValue", I18n.format("mcmti.mode." + McmtiConfig.mode.name()));
    }

    private static String languageLabel() {
        return I18n.format("mcmti.config.languageValue", I18n.format("mcmti.language." + McmtiConfig.language.getCode()));
    }
}
