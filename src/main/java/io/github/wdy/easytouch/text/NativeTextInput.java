package io.github.wdy.easytouch.text;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.wdy.easytouch.TouchConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.apache.logging.log4j.Level;
import org.lwjgl.system.Platform;
import org.lwjglx.opengl.Display;

/** Windows callbacks never read or mutate Minecraft objects. */
@Lwjgl3Aware
public final class NativeTextInput {
    private final Minecraft mc = Minecraft.getMinecraft();
    private WindowsUiaBridge bridge;
    private Object identity;
    private GuiScreen screen;
    private int token;
    private boolean disabled;

    public static void install(TouchConfig config) {
        if (config.nativeTextInput && Platform.get() == Platform.WINDOWS) {
            FMLCommonHandler.instance().bus().register(new NativeTextInput());
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        // Geometry (especially NEI/MUI alignment) is current after drawing the GUI.
        if (event.phase != TickEvent.Phase.END || disabled || !Display.isCreated()) return;
        try {
            if (bridge == null) bridge = new WindowsUiaBridge();
            TextTargets.Target target = Display.isActive() ? TextTargets.find(mc.currentScreen) : null;
            TextGeometry geometry = target == null ? null : target.geometry();
            if (geometry != null && !geometry.visible()) target = null;
            Object next = target == null ? null : target.identity();
            if (next != identity || mc.currentScreen != screen) {
                boolean focusChanged = next != identity;
                identity = next;
                screen = mc.currentScreen;
                token = token == Integer.MAX_VALUE ? 1 : token + 1;
                if (focusChanged) FMLLog.log(Level.INFO, "[GTNH Easy Touch] [UIA text] %s token=%d",
                    next == null ? "released" : "focused " + next.getClass().getSimpleName(), token);
            }
            // UIA ValuePattern writes are queued and validated on the client thread.
            WindowsUiaBridge.Edit edit = bridge.poll();
            if (target != null && edit != null && edit.token == token) {
                target.write(new TextState(edit.text, edit.text.length(), edit.text.length()));
                geometry = target.geometry();
            }
            ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            bridge.publish(target == null ? 0 : token, target == null ? null : target.read(), geometry,
                target == null ? 0 : target.limit(), resolution.getScaledWidth_double(), resolution.getScaledHeight_double());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disabled = true;
            if (bridge != null) {
                try { bridge.close(); }
                catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
            }
            FMLLog.log(Level.ERROR, error, "[GTNH Easy Touch] UIA text bridge disabled; ordinary Minecraft input remains available.");
        }
    }
}
