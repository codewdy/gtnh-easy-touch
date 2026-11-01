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
    public void reset() { gesture.reset(); screen = null; failed = false; }
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
        return !failed && gesture.accept(sample, mc.displayWidth, mc.displayHeight);
    }
}
