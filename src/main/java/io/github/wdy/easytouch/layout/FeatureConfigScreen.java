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
        int y = Math.max(28, (height - 228) / 2);
        buttonList.add(new TextControlButton(0, x, y, w, label(0)));
        buttonList.add(new TextControlButton(1, x, y + 24, w, label(1)));
        buttonList.add(new TextControlButton(2, x, y + 48, w, label(2)));
        buttonList.add(new TextControlButton(4, x, y + 72, w, label(4)));
        for (int axis = 0; axis < 2; axis++) {
            int row = y + 96 + axis * 24;
            buttonList.add(new TextControlButton(10 + axis * 2, x, row, 30, "−"));
            buttonList.add(new TextControlButton(11 + axis * 2, x + w - 30, row, 30, "+"));
        }
        buttonList.add(new TextControlButton(14, x, y + 144, w, "重置视野灵敏度"));
        buttonList.add(new TextControlButton(3, x, y + 184, w, "返回"));
    }

    private String label(int id) {
        if (id == 0) return "自动上台阶：" + (config.autoStep ? "开启" : "关闭");
        if (id == 1) return "优化 tooltip：" + (config.optimizeTooltip ? "开启" : "关闭");
        if (id == 4) return "EZMiner 优化：" + (io.github.wdy.easytouch.mining.EzMinerAutoVein.enabled ? "开启" : "关闭");
        return "自动切换工具：" + (config.autoTool ? "开启" : "关闭");
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 3) { mc.displayGuiScreen(parent); return; }
        if (button.id >= 10 && button.id <= 13) {
            boolean horizontal = button.id < 12;
            float current = horizontal ? config.touchDragSensitivityX : config.touchDragSensitivityY;
            config.setDragSensitivity(horizontal, current + (button.id % 2 == 0 ? -0.1F : 0.1F));
            return;
        }
        if (button.id == 14) {
            config.setDragSensitivity(true, 1.0F);
            config.setDragSensitivity(false, 1.0F);
            return;
        }
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
        int y = Math.max(28, (height - 228) / 2);
        drawCenteredString(fontRendererObj, "功能配置", width / 2, y - 26, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "修改后立即保存", width / 2, y + 170, 0xAAAAAA);
        drawCenteredString(fontRendererObj, String.format(java.util.Locale.ROOT,
            "X 轴灵敏度：%.1f×", config.touchDragSensitivityX), width / 2, y + 102, 0xFFFFFF);
        drawCenteredString(fontRendererObj, String.format(java.util.Locale.ROOT,
            "Y 轴灵敏度：%.1f×", config.touchDragSensitivityY), width / 2, y + 126, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
