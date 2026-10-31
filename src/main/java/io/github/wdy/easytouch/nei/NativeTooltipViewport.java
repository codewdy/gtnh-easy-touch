package io.github.wdy.easytouch.nei;

import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.math.MathHelper;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.Level;
import org.lwjgl.opengl.GL11;

/** Consumes rich tooltip lines only during the native renderer's current-frame callback. */
final class NativeTooltipViewport {
    interface Layout {
        boolean place(int width, int height);
        default int maxWidth() { return Integer.MAX_VALUE; }
    }
    static final int HORIZONTAL_PADDING = 16, VERTICAL_PADDING = 12, CONTENT_INSET = 6;
    private boolean visible;
    private final java.nio.IntBuffer scissor = org.lwjgl.BufferUtils.createIntBuffer(4);
    boolean isVisible() { return visible; }
    void hide() { visible = false; }
    private boolean unavailable;
    private int left, top, width, height, offsetX, offsetY, maxX, maxY;
    private Field gui, item, font, renderer, bgStart, bgEnd, borderStart, borderEnd, cursorX, cursorY;

    void intercept(Event event, GuiScreen screen, Layout layout) {
        if (layout.maxWidth() <= 0 || unavailable || event.isCanceled() || !event.getClass().getName().equals(
                "com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent")) return;
        try {
            if (renderer == null) {
                Class<?> type = event.getClass();
                gui = type.getField("gui"); item = type.getField("itemStack"); font = type.getField("font");
                renderer = type.getField("alternativeRenderer");
                bgStart = type.getField("backgroundStart"); bgEnd = type.getField("backgroundEnd");
                borderStart = type.getField("borderStart"); borderEnd = type.getField("borderEnd");
                cursorX = type.getField("x"); cursorY = type.getField("y");
            }
            ItemStack hovered = (ItemStack) item.get(event);
            // Custom alternative renderers retain their own layout and behavior.
            if (gui.get(event) != screen || renderer.get(event) != null) return;
            subject(hovered, null);
            FontRenderer text = (FontRenderer) font.get(event);
            int a = bgStart.getInt(event), b = bgEnd.getInt(event);
            int c = borderStart.getInt(event), d = borderEnd.getInt(event);
            int nativeX = cursorX.getInt(event) + 12, nativeY = cursorY.getInt(event) - 12;
            renderer.set(event, (Consumer<List<String>>) lines -> {
                if (lines.isEmpty()) return;
                List<String> wrapped = wrap(text, lines, layout.maxWidth());
                int contentWidth = 0, contentHeight = -2;
                for (int i = 0; i < wrapped.size(); i++) {
                    String line = wrapped.get(i);
                    GuiDraw.ITooltipLineHandler rich = GuiDraw.getTipLine(line);
                    Dimension size = rich == null ? new Dimension(text.getStringWidth(line),
                        line.endsWith(GuiDraw.TOOLTIP_LINESPACE) && i + 1 < wrapped.size() ? 12 : 10) : rich.getSize();
                    contentWidth = Math.max(contentWidth, size.width);
                    contentHeight += size.height;
                }
                if (!layout.place(contentWidth, contentHeight)) {
                    GuiDraw.drawMultilineTip(text, nativeX, nativeY, lines, a, b, c, d);
                    return;
                }
                draw(screen, text, wrapped, contentHeight, a, b, c, d);
            });
        } catch (ReflectiveOperationException | LinkageError error) {
            unavailable = true;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Native tooltip viewport unavailable; retaining native placement.");
        }
    }

    /** Wrap only ordinary text; rich handlers remain native and may still overflow. */
    static List<String> wrap(FontRenderer font, List<String> lines, int width) {
        if (width == Integer.MAX_VALUE) return lines;
        List<String> result = new java.util.ArrayList<>();
        int limit = Math.max(16, width);
        for (String line : lines) {
            if (line.startsWith(GuiDraw.TOOLTIP_HANDLER)) { result.add(line); continue; }
            boolean spaced = line.endsWith(GuiDraw.TOOLTIP_LINESPACE);
            String text = spaced ? line.substring(0, line.length() - GuiDraw.TOOLTIP_LINESPACE.length()) : line;
            List<String> parts = font.listFormattedStringToWidth(text, limit);
            if (parts.isEmpty()) parts = java.util.Collections.singletonList("");
            result.addAll(parts);
            if (spaced) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + GuiDraw.TOOLTIP_LINESPACE);
            }
        }
        return result;
    }

    private ItemStack subjectItem;
    private Object subjectContext;
    void subject(ItemStack item, Object context) {
        boolean same = item != null && subjectItem != null && item.isItemEqual(subjectItem)
            && ItemStack.areItemStackTagsEqual(item, subjectItem);
        if (!same && (item != null || subjectItem != null || !java.util.Objects.equals(context, subjectContext))) {
            offsetX = offsetY = 0;
        }
        subjectItem = item == null ? null : item.copy();
        subjectContext = context;
    }

    void layout(int x, int y, int w, int h, int contentWidth, int contentHeight) {
        left = x; top = y; width = w; height = h;
        maxX = Math.max(0, contentWidth + HORIZONTAL_PADDING - width);
        maxY = Math.max(0, contentHeight + VERTICAL_PADDING - height);
        scroll(0, 0);
        visible = true;
    }

    void scroll(int dx, int dy) {
        offsetX = Math.max(0, Math.min(maxX, offsetX + dx));
        offsetY = Math.max(0, Math.min(maxY, offsetY + dy));
    }

    boolean contains(int x, int y) {
        return visible && x >= left && x < left + width && y >= top && y < top + height;
    }

    void reset() {
        hide(); offsetX = offsetY = maxX = maxY = 0;
        subjectItem = null; subjectContext = null;
    }

    private void draw(GuiScreen screen, FontRenderer font, List<String> lines, int contentHeight,
            int a, int b, int c, int d) {
        float z = beginClip(screen);
        try {
            // Native GuiDraw clamps tall tips to screen edges. Compensate for that
            // before translating its unchanged renderer into the scroll viewport.
            int nativeY = (int) MathHelper.clip(8, 8, GuiDraw.displaySize().height - 8 - contentHeight);
            GL11.glTranslatef(left + CONTENT_INSET - 8 - offsetX, top + CONTENT_INSET - nativeY - offsetY, 0);
            GuiDraw.drawMultilineTip(font, 8, 8, lines, a, b, c, d);
        } finally {
            endClip(z);
        }
    }

    interface NativeDraw { void draw(int x, int y) throws ReflectiveOperationException; }
    void drawNative(GuiScreen screen, NativeDraw renderer) throws ReflectiveOperationException {
        float z = beginClip(screen);
        try {
            renderer.draw(left + CONTENT_INSET - offsetX, top + CONTENT_INSET - offsetY);
        } finally { endClip(z); }
    }

    private float beginClip(GuiScreen screen) {
        Minecraft mc = Minecraft.getMinecraft();
        // GUI scaling can round the framebuffer dimensions; round inward when clipping.
        double sx = mc.displayWidth / (double) screen.width, sy = mc.displayHeight / (double) screen.height;
        int x = (int) Math.ceil(left * sx), y = (int) Math.ceil((screen.height - top - height) * sy);
        int right = (int) Math.floor((left + width) * sx), upper = (int) Math.floor((screen.height - top) * sy);
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            scissor.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
            right = Math.min(right, scissor.get(0) + scissor.get(2));
            upper = Math.min(upper, scissor.get(1) + scissor.get(3));
            x = Math.max(x, scissor.get(0)); y = Math.max(y, scissor.get(1));
        }
        float z = GuiDraw.gui.getZLevel();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, Math.max(0, right - x), Math.max(0, upper - y));
        return z;
    }

    private void endClip(float z) {
        GuiDraw.gui.setZLevel(z);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    void drawScrollbars() {
        if (!visible) return;
        if (maxY > 0) {
            int track = height - 4;
            int thumb = Math.max(8, track * height / (height + maxY));
            int y = top + 2 + (track - thumb) * offsetY / maxY;
            Gui.drawRect(left + width - 3, top + 2, left + width - 1, top + height - 2, 0xff555555);
            Gui.drawRect(left + width - 3, y, left + width - 1, y + thumb, 0xffbbbbbb);
        }
        if (maxX > 0) {
            int track = width - 4;
            int thumb = Math.max(8, track * width / (width + maxX));
            int x = left + 2 + (track - thumb) * offsetX / maxX;
            Gui.drawRect(left + 2, top + height - 3, left + width - 2, top + height - 1, 0xff555555);
            Gui.drawRect(x, top + height - 3, x + thumb, top + height - 1, 0xffbbbbbb);
        }
    }
}
