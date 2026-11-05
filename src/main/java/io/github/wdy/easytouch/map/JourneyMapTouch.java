package io.github.wdy.easytouch.map;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import io.github.wdy.easytouch.touch.TouchInput;
import io.github.wdy.easytouch.touch.TouchSample;
import org.apache.logging.log4j.LogManager;

public final class JourneyMapTouch {
    private Object screen;
    private Method zoomIn, zoomOut;
    private boolean failed;
    private TouchSample hold;
    private boolean moved;
    public void tick(long now) {
        Minecraft mc = Minecraft.getMinecraft();
        if (hold == null || moved || mc.currentScreen != screen || now - hold.timestamp < 250000000L) return;
        TouchSample point = hold;
        hold = null;
        TouchInput.cancelGuiTouch();
        try {
            java.lang.reflect.Field field = screen.getClass().getDeclaredField("gridRenderer");
            field.setAccessible(true);
            Object grid = field.get(null);
            Object coord = grid.getClass().getMethod("getBlockUnderMouse", double.class, double.class, int.class, int.class)
                .invoke(grid, (double) point.x * mc.displayWidth, (1.0 - point.y) * mc.displayHeight - 1, mc.displayWidth, mc.displayHeight);
            int x = coord.getClass().getField("x").getInt(coord);
            int z = coord.getClass().getField("z").getInt(coord);
            mc.displayGuiScreen(new MapActionMenu((net.minecraft.client.gui.GuiScreen) screen, x, z, point.x, point.y));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogManager.getLogger("GTNH Easy Touch").warn("JourneyMap long press failed", e);
        }
    }
    private final MapPinchGesture gesture = new MapPinchGesture(new MapPinchGesture.Port() {
        public void cancelMouse() { TouchInput.cancelGuiTouch(); }
        public void zoom(boolean in) {
            try { (in ? zoomIn : zoomOut).invoke(screen); }
            catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                LogManager.getLogger("GTNH Easy Touch").warn("JourneyMap pinch zoom failed", e);
            }
        }
    });
    public void reset() { gesture.reset(); hold = null; moved = false; screen = null; failed = false; }
    public boolean accept(TouchSample sample) {
        Minecraft mc = Minecraft.getMinecraft();
        Object current = mc.currentScreen;
        if (sample.kind == TouchSample.Kind.RESET) { reset(); return false; }
        if (current == null || !current.getClass().getName().equals("journeymap.client.ui.fullscreen.Fullscreen")) {
            reset(); return false;
        }
        if (screen != current) {
            reset(); screen = current;
            try {
                zoomIn = current.getClass().getDeclaredMethod("zoomIn");
                zoomOut = current.getClass().getDeclaredMethod("zoomOut");
                zoomIn.setAccessible(true); zoomOut.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                LogManager.getLogger("GTNH Easy Touch").warn("JourneyMap pinch zoom unavailable", e);
            }
        }
        boolean pinch = !failed && gesture.accept(sample, mc.displayWidth, mc.displayHeight);
        if (pinch) { hold = null; return true; }
        if (!sample.simulated() && sample.valid()) {
            if (sample.kind == TouchSample.Kind.DOWN) {
                hold = sample; moved = false;
                try {
                    Method over = screen.getClass().getSuperclass().getDeclaredMethod("isMouseOverButton", int.class, int.class);
                    over.setAccessible(true);
                    net.minecraft.client.gui.GuiScreen gui = (net.minecraft.client.gui.GuiScreen) screen;
                    moved = (Boolean) over.invoke(screen, (int) (sample.x * gui.width), (int) (sample.y * gui.height));
                } catch (ReflectiveOperationException e) { moved = true; }
            } else if (hold != null && sample.deviceId == hold.deviceId && sample.fingerId == hold.fingerId) {
                if (sample.kind == TouchSample.Kind.MOVE)
                    moved |= Math.hypot((sample.x-hold.x)*mc.displayWidth, (sample.y-hold.y)*mc.displayHeight) > 12;
                else hold = null;
            }
        }
        return false;
    }
}
