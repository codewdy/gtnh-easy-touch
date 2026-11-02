package io.github.wdy.easytouch.nei;

import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.Level;

/** Optional adapter for Chromatic 1.0.36: use its own layout, renderer and render event. */
final class ChromaticTooltipBridge {
    private boolean initialized, unavailable, logged;
    private Field pending, box, pages, lastRevision, currentPage, size, contextRenderer, minWidth;
    private Class<?> rendererInterface;
    private static final int LAYOUT_HEIGHT_LIMIT = 1_000_000;
    private interface Restore { void run() throws ReflectiveOperationException; }
    private Restore restore;
    private Method getContext, getItem, getRenderer, getSections, getScale, getRevision, getAnimationStart;
    private Method prepare, paginate, freeSpace, getSize;
    private Constructor<?> section, copyBox;

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws ReflectiveOperationException {
        Method m = type.getDeclaredMethod(name, args); m.setAccessible(true); return m;
    }
    private void initialize() throws ReflectiveOperationException {
        initialized = true;
        if (!Loader.isModLoaded("chromatictooltips")) { unavailable = true; return; }
        Class<?> handler = Class.forName("com.slprime.chromatictooltips.TooltipHandler");
        Class<?> context = Class.forName("com.slprime.chromatictooltips.api.TooltipContext");
        Class<?> renderer = Class.forName("com.slprime.chromatictooltips.TooltipRenderer");
        Class<?> component = Class.forName("com.slprime.chromatictooltips.component.SectionComponent");
        Class<?> sectionBox = Class.forName("com.slprime.chromatictooltips.util.SectionBox");
        pending = field(handler, "renderLastTooltip");
        getContext = handler.getMethod("getLastTooltipContext");
        getItem = context.getMethod("getItem"); getRenderer = context.getMethod("getRenderer");
        getSections = context.getMethod("getSections"); getScale = context.getMethod("getScaleFactor");
        getAnimationStart = context.getMethod("getAnimationStartTime");
        getRevision = context.getMethod("getRevision"); getSize = context.getMethod("getTooltipSize");
        contextRenderer = field(context, "renderer");
        rendererInterface = Class.forName("com.slprime.chromatictooltips.api.ITooltipRenderer");
        pages = field(context, "pagedComponents"); lastRevision = field(context, "lastRevision");
        currentPage = field(context, "currentPage"); size = field(context, "tooltipSize");
        box = field(renderer, "tooltipBox");
        minWidth = field(sectionBox, "minWidth");
        copyBox = sectionBox.getConstructor(sectionBox);
        prepare = method(renderer, "prepareComponents", List.class);
        paginate = method(renderer, "paginateComponents", context, component, int.class, int.class);
        freeSpace = method(renderer, "getFreeSpace", int.class);
        section = component.getConstructor(String.class, sectionBox, List.class);
    }

    static int wrapWidth(int pixels, int guiScale, int tooltipScale, int available) {
        if (pixels == Integer.MAX_VALUE) return available;
        return Math.max(1, Math.min(available,
            (int) Math.floor(pixels * (double) guiScale / Math.max(1, tooltipScale))));
    }

    // A failed field restore must not prevent restoring the remaining native state.
    static void restoreFields(Object target, Field[] fields, Object[] values) throws IllegalAccessException {
        IllegalAccessException failure = null;
        for (int i = 0; i < fields.length; i++) {
            try { fields[i].set(target, values[i]); }
            catch (IllegalAccessException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    void hide() {
        try {
            if (!initialized) initialize();
            if (pending != null) pending.setBoolean(null, false);
        } catch (ReflectiveOperationException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Could not hide Chromatic tooltip.");
        }
    }

    void finishFrame() {
        if (restore == null) return;
        Restore pendingRestore = restore;
        restore = null;
        try { pendingRestore.run(); }
        catch (ReflectiveOperationException error) {
            unavailable = true;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Could not restore Chromatic tooltip context.");
        }
    }

    void draw(GuiScreen screen, NativeTooltipViewport viewport, NativeTooltipViewport.Layout layout) {
        try {
            if (layout.maxWidth() <= 0) return;
            if (!initialized) initialize();
            if (unavailable || !pending.getBoolean(null)) return;
            Object context = getContext.invoke(null);
            if (context == null) return;
            ItemStack item = (ItemStack) getItem.invoke(context);
            viewport.subject(item, item == null ? getAnimationStart.invoke(context) : null);
            Object renderer = getRenderer.invoke(context);
            if (!box.getDeclaringClass().isInstance(renderer)) return;
            int scale = (Integer) getScale.invoke(context);
            Dimension available = (Dimension) freeSpace.invoke(renderer, scale);
            Minecraft mc = Minecraft.getMinecraft();
            int guiScale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
            int wrapWidth = wrapWidth(layout.maxWidth(), guiScale, scale, available.width);
            Object originalBox = box.get(renderer);
            Object narrowBox = copyBox.newInstance(originalBox);
            minWidth.setInt(narrowBox, Math.min(minWidth.getInt(narrowBox), wrapWidth));
            List<?> fullPages;
            try {
                // Use a temporary theme box so its minimum width cannot override the
                // column limit. Native component pagination still owns text wrapping.
                box.set(renderer, narrowBox);
                Object content = section.newInstance("page", narrowBox,
                    prepare.invoke(renderer, getSections.invoke(context)));
                fullPages = (List<?>) paginate.invoke(renderer, context, content, wrapWidth, LAYOUT_HEIGHT_LIMIT);
            } finally { box.set(renderer, originalBox); }
            if (fullPages.size() != 1) return;
            Object oldPages = pages.get(context), oldSize = size.get(context);
            int oldRevision = lastRevision.getInt(context), oldPage = currentPage.getInt(context);
            restore = () -> restoreFields(context,
                new Field[] {contextRenderer, pages, lastRevision, currentPage, size},
                new Object[] {renderer, oldPages, oldRevision, oldPage, oldSize});
            pages.set(context, fullPages);
            lastRevision.setInt(context, (Integer) getRevision.invoke(context));
            currentPage.setInt(context, 0); size.set(context, null);
            Dimension bounds = (Dimension) getSize.invoke(context);
            if (!layout.place(bounds.width, bounds.height)) { finishFrame(); return; }
            Object wrapper = java.lang.reflect.Proxy.newProxyInstance(rendererInterface.getClassLoader(),
                new Class<?>[] {rendererInterface}, (proxy, method, args) -> {
                    if (!method.getName().equals("draw")) return method.invoke(renderer, args);
                    try {
                        viewport.drawNative(screen, (x, y) -> method.invoke(renderer, context, x, y));
                        if (!logged) {
                            logged = true;
                            FMLLog.log(Level.INFO, "[GTNH Easy Touch] Chromatic native tooltip viewport active.");
                        }
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        unavailable = true; viewport.reset();
                        FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Chromatic viewport draw failed.");
                    } finally { finishFrame(); }
                    return null;
                });
            // The original Post listener invokes this wrapper and consumes its pending flag
            // normally, preserving its cache and animation lifecycle.
            contextRenderer.set(context, wrapper);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            finishFrame();
            unavailable = true; viewport.reset();
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Chromatic viewport unavailable; retaining native tooltip.");
        }
    }
}
