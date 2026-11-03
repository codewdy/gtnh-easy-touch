package io.github.wdy.easytouch.layout;

import io.github.wdy.easytouch.TouchConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Feature switches save immediately and return to the screen that opened them. */
public final class FeatureConfigScreen extends GuiScreen {
    private final TouchConfig config;
    private final GuiScreen parent;

    public FeatureConfigScreen(TouchConfig config, GuiScreen parent) {
        this.config = config;
        this.parent = parent;
    }

    @Override public void initGui() {
        buttonList.clear();
        int w = Math.min(240, width - 20);
        int x = (width - w) / 2;
        int y = Math.max(32, height / 2 - 72);
        buttonList.add(new TextControlButton(0, x, y, w, label(0)));
        buttonList.add(new TextControlButton(1, x, y + 28, w, label(1)));
        buttonList.add(new TextControlButton(2, x, y + 56, w, label(2)));
        buttonList.add(new TextControlButton(4, x, y + 84, w, label(4)));
        buttonList.add(new TextControlButton(3, x, y + 124, w, "返回"));
    }

    private String label(int id) {
        if (id == 0) return "自动上台阶：" + (config.autoStep ? "开启" : "关闭");
        if (id == 1) return "优化 tooltip：" + (config.optimizeTooltip ? "开启" : "关闭");
        if (id == 4) return "EZMiner 优化：" + (io.github.wdy.easytouch.mining.EzMinerAutoVein.enabled ? "开启" : "关闭");
        return "自动切换工具：" + (config.autoTool ? "开启" : "关闭");
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 3) { mc.displayGuiScreen(parent); return; }
        if (button.id == 0) config.toggleAutoStep();
        else if (button.id == 1) config.toggleOptimizeTooltip();
        else if (button.id == 2) config.toggleAutoTool();
        else if (button.id == 4) config.toggleEzMinerOptimization();
        button.displayString = label(button.id);
    }

    @Override protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else super.keyTyped(character, key);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int y = Math.max(32, height / 2 - 72);
        drawCenteredString(fontRendererObj, "功能配置", width / 2, y - 26, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "修改后立即保存", width / 2, y + 110, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
