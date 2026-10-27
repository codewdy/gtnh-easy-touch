package io.github.wdy.easytouch.touch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Level;
import org.lwjgl.input.Mouse;

/** Keeps ModularUI 2's frame-cached hit targets in step with queued GUI mouse events. */
public final class ModularUiTouchInput {
    private static final String PREFIX = "com.cleanroommc.modularui.";
    private final Class<?> mousePre;
    private final Field eventGui;
    private final Method current, wrapper, guiScreen, context, panels, openPanels;
    private final Method updateState, partialTicks, findHovered, frameUpdate;
    private boolean failed;

    private ModularUiTouchInput() throws ReflectiveOperationException {
        mousePre = Class.forName(PREFIX + "api.event.MouseInputEvent$Pre");
        eventGui = mousePre.getField("gui");
        Class<?> screen = Class.forName(PREFIX + "screen.ModularScreen");
        Class<?> ctx = Class.forName(PREFIX + "screen.viewport.ModularGuiContext");
        current = Class.forName(PREFIX + "screen.ClientScreenHandler").getMethod("getMuiScreen");
        wrapper = screen.getMethod("getScreenWrapper");
        guiScreen = Class.forName(PREFIX + "api.IMuiScreen").getMethod("getGuiScreen");
        context = screen.getMethod("getContext");
        panels = screen.getMethod("getPanelManager");
        openPanels = Class.forName(PREFIX + "screen.PanelManager").getMethod("getOpenPanels");
        updateState = ctx.getMethod("updateState", int.class, int.class, float.class);
        partialTicks = ctx.getMethod("getPartialTicks");
        frameUpdate = ctx.getMethod("onFrameUpdate");
        // MUI 2.3.92 only refreshes this private hit-test list at frame cadence.
        findHovered = Class.forName(PREFIX + "screen.ModularPanel").getDeclaredMethod("findHoveredWidgets");
        findHovered.setAccessible(true);
    }

    public static void install() {
        try {
            MinecraftForge.EVENT_BUS.register(new ModularUiTouchInput());
        } catch (ClassNotFoundException absent) {
            // Optional integration; MUI 1 and vanilla containers keep their normal input path.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] ModularUI 2 pointer synchronization unavailable.");
        }
    }

    // Use the base event type to avoid a hard dependency on MUI 2's modern-Java classes.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void mouse(Event event) {
        if (failed || !mousePre.isInstance(event)) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen gui = mc.currentScreen;
        if (gui == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) return;
        try {
            if (eventGui.get(event) != gui) return;
            Object screen = current.invoke(null);
            if (screen == null || guiScreen.invoke(wrapper.invoke(screen)) != gui) return;
            Object ctx = context.invoke(screen);
            // Read this queued event, not Mouse.getX/Y (which may already be at a later touch).
            int x = Mouse.getEventX() * gui.width / mc.displayWidth;
            int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
            updateState.invoke(ctx, x, y, partialTicks.invoke(ctx));
            for (Object panel : (Iterable<?>) openPanels.invoke(panels.invoke(screen))) {
                findHovered.invoke(panel);
            }
            frameUpdate.invoke(ctx);
            // Do not dispatch or cancel clicks: MUI's original handler processes the event once.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Disabling incompatible ModularUI 2 pointer synchronization.");
        }
    }
}
