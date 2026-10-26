package io.github.wdy.easytouch.touch;

import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** Full-window diagnostic surface: it observes touch and does not synthesize gameplay actions. */
final class TouchDebugScreen extends GuiScreen {
    private final TouchInput input;
    private final GuiScreen parent;

    TouchDebugScreen(TouchInput input, GuiScreen parent) {
        this.input = input;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 104, height - 26, 100, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(1, width / 2 + 4, height - 26, 100, 20,
            I18n.format("easytouch.touch.reset")));
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("easytouch.touch.title"), width / 2, 10, 0xFFFFFF);
        int y = 28;
        y = line(I18n.format("easytouch.touch.status." + input.status), y, 0xFFFFFF);
        TouchTracker state = input.tracker;
        y = line(I18n.format("easytouch.touch.count", state.nativeCount(), state.peakNative), y, 0x66FFAA);
        y = line(I18n.format("easytouch.touch.events", state.nativeEvents, state.simulatedEvents,
            input.source == null ? 0 : input.source.mouseEvents(), input.queue.dropped()), y, 0xDDDDDD);
        if (input.source != null) y = line(input.source.enabledTypes, y, 0xAAAAAA);
        y = line(I18n.format("easytouch.touch.hint"), y + 4, 0xFFFFFF);
        if (state.nativeEvents == 0) y = line(I18n.format("easytouch.touch.waiting"), y, 0xFFCC66);
        if (state.last != null) {
            TouchSample last = state.last;
            line(String.format(Locale.ROOT, "%s D:%s F:%s x=%.3f y=%.3f p=%.2f%s", last.kind,
                Long.toUnsignedString(last.deviceId), Long.toUnsignedString(last.fingerId),
                last.x, last.y, last.pressure, last.simulated() ? " [MOUSE]" : ""), y + 4, 0xBBBBBB);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        int index = 0;
        for (TouchSample contact : state.contacts()) {
            int px = Math.max(4, Math.min(width - 5, Math.round(contact.x * width)));
            int py = Math.max(4, Math.min(height - 5, Math.round(contact.y * height)));
            int color = contact.simulated() ? 0xFF999999 : 0xFF66FFAA;
            drawRect(px - 8, py - 1, px + 9, py + 2, color);
            drawRect(px - 1, py - 8, px + 2, py + 9, color);
            String label = "#" + (++index) + " F:" + Long.toUnsignedString(contact.fingerId)
                + (contact.simulated() ? " [MOUSE]" : "");
            int tx = Math.max(2, Math.min(width - fontRendererObj.getStringWidth(label) - 2, px + 10));
            fontRendererObj.drawStringWithShadow(label, tx, Math.max(2, Math.min(height - 10, py - 12)), color);
        }
    }

    private int line(String text, int y, int color) {
        fontRendererObj.drawSplitString(text, 12, y, Math.max(1, width - 24), color);
        return y + fontRendererObj.splitStringWidth(text, Math.max(1, width - 24)) + 3;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id == 1) input.tracker.resetStats();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
        else super.keyTyped(character, key);
    }
}
