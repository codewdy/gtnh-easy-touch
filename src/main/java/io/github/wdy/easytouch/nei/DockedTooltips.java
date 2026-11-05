package io.github.wdy.easytouch.nei;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.wdy.easytouch.TouchConfig;
import io.github.wdy.easytouch.touch.TouchInput;
import io.github.wdy.easytouch.touch.TouchSample;
import io.github.wdy.easytouch.touch.TooltipRenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/** Position-only presentation: the original GUI still decides when and what to show. */
public final class DockedTooltips {
    private static DockedTooltips instance;
    private static TouchConfig config;
    private static boolean hidden() { return config != null && config.hideTooltips; }
    static boolean enabled() { return config != null && config.optimizeTooltip; }
    private static Predicate<TouchSample> menuPriority = sample -> false;
    static void setMenuPriority(Predicate<TouchSample> priority) { menuPriority = priority; }
    private final NativeTooltipViewport viewport = new NativeTooltipViewport();
    private final ChromaticTooltipBridge chromatic = new ChromaticTooltipBridge();
    private final Set<String> captured = new HashSet<>();
    private final NativeTooltipViewport.Layout tooltipLayout = new NativeTooltipViewport.Layout() {
        public boolean place(int width, int height) { return DockedTooltips.this.place(width, height); }
        public int maxWidth() { return contentWidthLimit(); }
    };
    private GuiScreen screen;
    private String owner;
    private float lastX, lastY;

    public static void install(TouchConfig settings) {
        config = settings;
        if (instance != null) return;
        instance = new DockedTooltips();
        MinecraftForge.EVENT_BUS.register(instance);
        TooltipRenderPhase.setRenderer(instance::prepareChromatic);
    }

    public static boolean acceptTouch(TouchSample sample) {
        return instance != null && instance.accept(sample);
    }

    private boolean accept(TouchSample sample) {
        if (sample.kind == TouchSample.Kind.RESET) {
            owner = null; captured.clear(); viewport.reset(); return false;
        }
        if (sample.simulated() || !sample.valid()) return false;
        String id = sample.deviceId + ":" + sample.fingerId;
        if (captured.contains(id)) {
            if (enabled() && id.equals(owner) && screen == Minecraft.getMinecraft().currentScreen && Display.isActive()
                    && sample.kind == TouchSample.Kind.MOVE) {
                int dx = Math.round((lastX - sample.x) * screen.width);
                int dy = Math.round((lastY - sample.y) * screen.height);
                viewport.scroll(dx, dy);
                // Retain subpixel movement so a slow drag still scrolls.
                lastX -= dx / (float) screen.width;
                lastY -= dy / (float) screen.height;
            }
            if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) {
                captured.remove(id);
                if (id.equals(owner)) owner = null;
            }
            return true;
        }
        if (hidden() || !enabled() || sample.kind != TouchSample.Kind.DOWN || screen == null
                || screen != Minecraft.getMinecraft().currentScreen || !Display.isActive()
                || TouchInput.guiMouseHeld() || TouchInput.guiAwaitingSecond() || menuPriority.test(sample)
                || !viewport.contains((int) (sample.x * screen.width), (int) (sample.y * screen.height))) return false;
        captured.add(id);
        if (owner == null) { owner = id; lastX = sample.x; lastY = sample.y; }
        return true;
    }

    @SubscribeEvent
    public void open(GuiOpenEvent event) {
        chromatic.finishFrame(); viewport.reset(); screen = null; owner = null;
        // Keep captured releases consumed when the GUI changes during a drag.
    }

    @SubscribeEvent
    public void before(GuiScreenEvent.DrawScreenEvent.Pre event) {
        chromatic.finishFrame();
        LeftTooltipLayout.prepare();
        if (screen != event.gui) { viewport.reset(); owner = null; }
        screen = event.gui;
        viewport.hide();
        if (!enabled() || hidden()) viewport.reset();
    }

    private int contentWidthLimit() {
        if (!enabled() || !(screen instanceof GuiContainer)) return 0;
        Rectangle4i area = LeftTooltipLayout.tooltipArea((GuiContainer) screen);
        return area == null ? 0 : Math.max(0,
            Math.min(screen.width, area.x + area.w) - Math.max(0, area.x) - NativeTooltipViewport.HORIZONTAL_PADDING);
    }

    private boolean place(int contentWidth, int contentHeight) {
        if (!enabled() || !(screen instanceof GuiContainer) || !NEIClientConfig.isLoaded()
                || ItemPanels.itemPanel == null) return false;
        Rectangle4i panel = LeftTooltipLayout.tooltipArea((GuiContainer) screen);
        if (panel == null) return false;
        int left = Math.max(0, panel.x);
        int right = Math.min(screen.width, panel.x + panel.w);
        int top = Math.max(0, panel.y);
        int bottom = Math.min(screen.height, panel.y + panel.h);
        int availableHeight = Math.min(panel.h, bottom - top);
        int width = Math.min(contentWidth + NativeTooltipViewport.HORIZONTAL_PADDING, right - left);
        int height = Math.min(contentHeight + NativeTooltipViewport.VERTICAL_PADDING, availableHeight);
        if (width < 16 || height < 16) return false;
        viewport.layout(left, top, width, height, contentWidth, contentHeight);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void nativeTooltip(Event event) {
        if (hidden() && event.getClass().getName().equals(
                "com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent") && event.isCancelable()) {
            event.setCanceled(true);
            return;
        }
        if (enabled() && screen != null && Display.isActive()) viewport.intercept(event, screen, tooltipLayout);
    }

    private void prepareChromatic(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (hidden()) { chromatic.hide(); return; }
        if (enabled() && event.gui == screen && Display.isActive()) chromatic.draw(screen, viewport, tooltipLayout);
    }

    @SubscribeEvent
    public void after(GuiScreenEvent.DrawScreenEvent.Post event) {
        chromatic.finishFrame();
        if (event.gui != screen || !viewport.isVisible()) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, 0, 500);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            viewport.drawScrollbars();
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
}
