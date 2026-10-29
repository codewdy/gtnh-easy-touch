package io.github.wdy.easytouch.nei;

import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchField;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.wdy.easytouch.input.ItemTouchGesture;
import io.github.wdy.easytouch.touch.GuiTouchMouse;
import io.github.wdy.easytouch.touch.TouchInput;
import io.github.wdy.easytouch.touch.TouchSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.lwjgl.opengl.Display;

/** Stationary search-field holds toggle NEI's own inventory search highlight. */
// Forge ASM event wrappers are loaded outside this package; the owner must be public.
public final class NeiSearchTouch {
    private final Minecraft mc = Minecraft.getMinecraft();
    private GuiContainer screen;
    private SearchField field;
    private TouchSample down;
    private final ItemTouchGesture gesture = new ItemTouchGesture(new ItemTouchGesture.Port() {
        public void hover(float x, float y) { new GuiTouchMouse(mc).move(x, y); }
        public void drag(TouchSample sample) { TouchInput.dragGuiTouch(sample); }
        public void cancelDrag() { TouchInput.cancelGuiTouch(); }
        public void tap(TouchSample first, TouchSample up) {
            TouchInput.dragGuiTouch(first);
            TouchInput.dragGuiTouch(up);
        }
        public void menu() {
            if (!current()) return;
            // Exactly the state changed by SearchField.handleClick's native double-click branch.
            // Do not synthesize clicks: they would move the caret and affect click timing.
            NEIClientConfig.world.nbt.setBoolean("searchinventories", !SearchField.searchInventories());
        }
    });

    private boolean available() {
        return Display.isActive() && mc.currentScreen instanceof GuiContainer
            && NEIClientConfig.isLoaded() && NEIClientConfig.isEnabled() && !NEIClientConfig.isHidden()
            && NEIClientConfig.world != null && LayoutManager.instance() != null
            && LayoutManager.searchField != null && LayoutManager.searchField.isVisible();
    }

    private boolean current() {
        return down != null && available() && mc.currentScreen == screen && LayoutManager.searchField == field
            && LayoutManager.instance().getWidgetUnderMouse((int)(down.x * screen.width),
                (int)(down.y * screen.height)) == field;
    }

    private void cancel() { gesture.cancel(); down = null; screen = null; field = null; }

    boolean accept(TouchSample sample) {
        if (sample.kind == TouchSample.Kind.RESET) { cancel(); return false; }
        if (sample.simulated() || !sample.valid()) return false;
        if (down != null) {
            boolean owner = sample.deviceId == down.deviceId && sample.fingerId == down.fingerId;
            if (!current()) { cancel(); return true; }
            if (owner) {
                gesture.accept(sample);
                if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) cancel();
            }
            return true;
        }
        if (sample.kind != TouchSample.Kind.DOWN || !available()
                || TouchInput.guiMouseHeld() || TouchInput.guiAwaitingSecond()) return false;
        GuiContainer gui = (GuiContainer) mc.currentScreen;
        if (LayoutManager.instance().getWidgetUnderMouse((int)(sample.x * gui.width),
                (int)(sample.y * gui.height)) != LayoutManager.searchField) return false;
        screen = gui; field = LayoutManager.searchField; down = sample;
        gesture.start(sample, mc.displayWidth, mc.displayHeight);
        return true;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || down == null) return;
        if (!current()) cancel(); else gesture.tick(TouchInput.nanoTime());
    }
    @SubscribeEvent public void gui(GuiOpenEvent event) { cancel(); }
}
