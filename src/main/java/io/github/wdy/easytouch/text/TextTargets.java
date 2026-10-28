package io.github.wdy.easytouch.text;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;

/** Bounded discovery: screen fields, visible NEI widgets and the actual MUI focus owner. */
final class TextTargets {
    interface Target {
        Object identity();
        TextState read() throws ReflectiveOperationException;
        TextGeometry geometry() throws ReflectiveOperationException;
        void write(TextState state) throws ReflectiveOperationException;
        int limit();
    }

    private static final Map<Class<?>, List<Field>> SCREEN_FIELDS = new HashMap<>();
    private static final Field ENABLED = ReflectionHelper.findField(GuiTextField.class, "isEnabled", "field_146226_p");
    private static final Field SCROLL = ReflectionHelper.findField(GuiTextField.class, "lineScrollOffset", "field_146225_q");
    private static final Field FONT = ReflectionHelper.findField(GuiTextField.class, "field_146211_a");
    private static final Method KEY = method(GuiScreen.class, new String[] {"keyTyped", "func_73869_a"}, char.class, int.class);

    static void key(GuiScreen gui, char character, int code) throws ReflectiveOperationException {
        KEY.invoke(gui, character, code);
    }

    static Target find(GuiScreen gui) throws ReflectiveOperationException {
        if (gui == null) return null;
        Target target = modular(gui);
        if (target != null) return target;
        for (Field f : fields(gui.getClass())) {
            Object value = f.get(gui);
            target = adapt(value, gui);
            if (target != null) return target;
            if (value instanceof Iterable<?>) {
                int count = 0;
                for (Object item : (Iterable<?>) value) {
                    if (++count > 128) break;
                    target = adapt(item, gui);
                    if (target != null) return target;
                }
            }
        }
        if (gui instanceof GuiContainer) {
            try {
                Class<?> config = Class.forName("codechicken.nei.NEIClientConfig");
                if (!(Boolean) callStatic(config, "isEnabled") || (Boolean) callStatic(config, "isHidden")) return null;
                Object widgets = field(Class.forName("codechicken.nei.LayoutManager"), "controlWidgets").get(null);
                for (Object widget : (Iterable<?>) widgets) {
                    target = adapt(widget, gui);
                    if (target != null) return target;
                }
            } catch (ClassNotFoundException absent) { /* optional */ }
        }
        return null;
    }

    private static Target adapt(Object value, GuiScreen gui) throws ReflectiveOperationException {
        if (value instanceof GuiTextField) {
            GuiTextField text = (GuiTextField) value;
            if (text.isFocused() && text.getVisible() && ENABLED.getBoolean(text)) return new Vanilla(text, gui, null);
        } else if (value != null && isType(value, "codechicken.nei.TextField")) {
            if ((Boolean) call(value, "focused")) {
                GuiTextField text = (GuiTextField) field(value.getClass(), "field").get(value);
                if (text.getVisible() && ENABLED.getBoolean(text)) return new Vanilla(text, gui, value);
            }
        }
        return null;
    }

    private static Target modular(GuiScreen gui) throws ReflectiveOperationException {
        if (isType(gui, "com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui")) {
            Object cursor = call(gui, "getCursor");
            Object widget = call(cursor, "getFocused");
            if (widget != null && isType(widget, "com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget")
                    && (Boolean) call(widget, "isEnabled")) {
                return new Modular(widget, false);
            }
        }
        try {
            Object screen = callStatic(Class.forName("com.cleanroommc.modularui.screen.ClientScreenHandler"), "getMuiScreen");
            if (screen == null || call(call(screen, "getScreenWrapper"), "getGuiScreen") != gui) return null;
            Object context = call(screen, "getContext");
            Object located = call(context, "getFocusedWidget");
            Object widget = located == null ? null : call(located, "getElement");
            if (widget != null && isType(widget, "com.cleanroommc.modularui.widgets.textfield.TextFieldWidget")
                    && (Boolean) call(widget, "isEnabled") && (Boolean) call(widget, "areAncestorsEnabled")) {
                return new Modular(widget, true);
            }
        } catch (ClassNotFoundException absent) { /* optional */ }
        return null;
    }

    private static final class Vanilla implements Target {
        final GuiTextField text;
        final GuiScreen gui;
        final Object nei;
        Vanilla(GuiTextField text, GuiScreen gui, Object nei) { this.text = text; this.gui = gui; this.nei = nei; }
        public Object identity() { return text; }
        public int limit() { return text.getMaxStringLength(); }
        public TextState read() { return new TextState(text.getText(), text.getCursorPosition(), text.getSelectionEnd()); }
        public TextGeometry geometry() throws IllegalAccessException {
            FontRenderer font = (FontRenderer) FONT.get(text);
            String value = text.getText();
            int start = Math.min(value.length(), SCROLL.getInt(text));
            String visible = font.trimStringToWidth(value.substring(start), text.getWidth());
            int cursor = Math.max(0, Math.min(visible.length(), text.getCursorPosition() - start));
            int x = text.xPosition + (text.getEnableBackgroundDrawing() ? 4 : 0);
            int y = text.yPosition + (text.getEnableBackgroundDrawing() ? (text.height - 8) / 2 : 0);
            int caretX = x + font.getStringWidth(visible.substring(0, cursor));
            return new TextGeometry(text.xPosition, text.yPosition, text.xPosition + text.width,
                text.yPosition + text.height, caretX, y, font.FONT_HEIGHT);
        }
        public void write(TextState state) throws ReflectiveOperationException {
            String previous = text.getText();
            if (!previous.equals(state.text)) {
                text.setCursorPosition(0);
                text.setSelectionPos(previous.length());
                text.writeText(state.text); // Respect character filters, length limits and subclass validation.
                if (nei != null) {
                    nei.getClass().getMethod("onTextChange", String.class).invoke(nei, previous);
                } else {
                    // Screen callbacks (e.g. anvil rename/server sync) run through the normal key handler.
                    // Right at end is handled by the textbox without modifying its contents.
                    text.setCursorPositionEnd();
                    key(gui, '\0', 205);
                }
            }
            text.setCursorPosition(state.anchor);
            text.setSelectionPos(state.caret);
        }
    }

    private static final class Modular implements Target {
        final Object widget, handler;
        final boolean modern;
        Modular(Object widget, boolean modern) throws ReflectiveOperationException {
            this.widget = widget;
            this.modern = modern;
            handler = field(widget.getClass(), "handler").get(widget);
        }
        public Object identity() { return widget; }
        public int limit() { return 32767; } // Handler owns the actual per-widget length/pattern rules.
        public TextState read() throws ReflectiveOperationException {
            Point caret = (Point) call(handler, "getMainCursor"), anchor = (Point) call(handler, "getOffsetCursor");
            return new TextState((String) call(widget, "getText"), anchor.x, caret.x);
        }
        public TextGeometry geometry() throws ReflectiveOperationException {
            Object renderer = field(widget.getClass(), "renderer").get(widget);
            Object lines = renderer.getClass().getMethod("measureLines", List.class).invoke(renderer, call(handler, "getText"));
            Point2D.Float caret = (Point2D.Float) renderer.getClass().getMethod("getPosOf", List.class, Point.class)
                .invoke(renderer, lines, call(handler, "getMainCursor"));
            float height = ((Number) call(renderer, "getFontHeight")).floatValue();
            if (!modern) {
                Rectangle rect = (Rectangle) call(widget, "getRenderAbsoluteRectangle");
                Method dragging = method(Class.forName("com.gtnewhorizons.modularui.common.widget.textfield.BaseTextFieldWidget"),
                    new String[] {"getDraggableTranslate"});
                Point offset = (Point) dragging.invoke(widget);
                rect.translate(offset.x, offset.y);
                int scroll = ((Number) call(widget, "getHorizontalScrollOffset")).intValue();
                Object window = call(widget, "getWindow"), position = call(window, "getPos"), size = call(window, "getSize");
                double scale = ((Number) call(window, "getScale")).doubleValue();
                double centerX = field(position.getClass(), "x").getInt(position) + field(size.getClass(), "width").getInt(size) / 2.0;
                double centerY = field(position.getClass(), "y").getInt(position) + field(size.getClass(), "height").getInt(size) / 2.0;
                AffineTransform matrix = new AffineTransform();
                matrix.translate(centerX, centerY);
                matrix.rotate(Math.toRadians(field(window.getClass(), "rotation").getFloat(window)));
                matrix.translate(field(window.getClass(), "translateX").getFloat(window), field(window.getClass(), "translateY").getFloat(window));
                matrix.scale(scale, scale);
                matrix.translate(-centerX, -centerY);
                return new TextGeometry(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                    rect.x + caret.x - scroll, rect.y + caret.y, height).transform(matrix);
            }
            // Rebuild the transform, rather than retaining the matrix captured when focus was acquired.
            Class<?> locatedType = Class.forName("com.cleanroommc.modularui.screen.viewport.LocatedWidget");
            Object located = locatedType.getMethod("of", Class.forName("com.cleanroommc.modularui.api.widget.IWidget"))
                .invoke(null, widget);
            Object matrix = call(located, "getTransformationMatrix");
            Object area = call(widget, "getArea");
            float width = ((Number) call(area, "w")).floatValue(), h = ((Number) call(area, "h")).floatValue();
            // LocatedWidget.of includes this editor's transformChildren (its own scroll).
            // Undo that scroll for the viewport rectangle only; the text caret must keep it.
            float scrollX = ((Number) call(widget, "getScrollX")).floatValue();
            float scrollY = ((Number) call(widget, "getScrollY")).floatValue();
            float cx = caret.x, cy = caret.y;
            double x1 = transform(matrix, "transformX", scrollX, scrollY), y1 = transform(matrix, "transformY", scrollX, scrollY);
            double x2 = transform(matrix, "transformX", width + scrollX, h + scrollY);
            double y2 = transform(matrix, "transformY", width + scrollX, h + scrollY);
            double x3 = transform(matrix, "transformX", width + scrollX, scrollY), y3 = transform(matrix, "transformY", width + scrollX, scrollY);
            double x4 = transform(matrix, "transformX", scrollX, h + scrollY), y4 = transform(matrix, "transformY", scrollX, h + scrollY);
            double caretTop = transform(matrix, "transformY", cx, cy);
            return new TextGeometry(Math.min(Math.min(x1, x2), Math.min(x3, x4)), Math.min(Math.min(y1, y2), Math.min(y3, y4)),
                Math.max(Math.max(x1, x2), Math.max(x3, x4)), Math.max(Math.max(y1, y2), Math.max(y3, y4)),
                transform(matrix, "transformX", cx, cy), caretTop,
                Math.max(1, transform(matrix, "transformY", cx, cy + height) - caretTop));
        }
        public void write(TextState state) throws ReflectiveOperationException {
            if (!state.text.equals(call(widget, "getText"))) {
                call(handler, "markAll");
                // A singleton list preserves an empty string, unlike String.split in the string overload.
                if (modern) handler.getClass().getMethod("insert", List.class, boolean.class)
                    .invoke(handler, Collections.singletonList(state.text), false);
                else handler.getClass().getMethod("insert", List.class).invoke(handler, Collections.singletonList(state.text));
            }
            int length = ((String) call(widget, "getText")).length();
            int caret = Math.min(length, state.caret), anchor = Math.min(length, state.anchor);
            if (modern) handler.getClass().getMethod("setMainCursor", int.class, int.class, boolean.class)
                .invoke(handler, 0, caret, false);
            else handler.getClass().getMethod("setMainCursor", int.class, int.class).invoke(handler, 0, caret);
            handler.getClass().getMethod("setOffsetCursor", int.class, int.class).invoke(handler, 0, anchor);
        }
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = SCREEN_FIELDS.get(type);
        if (result != null) return result;
        result = new ArrayList<>();
        for (Class<?> c = type; c != null && c != GuiScreen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                if (GuiTextField.class.isAssignableFrom(f.getType()) || Iterable.class.isAssignableFrom(f.getType())
                        || f.getType().getName().startsWith("codechicken.nei.")) {
                    f.setAccessible(true);
                    result.add(f);
                }
            }
        }
        SCREEN_FIELDS.put(type, result);
        return result;
    }

    private static boolean isType(Object object, String name) {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(name)) return true;
        return false;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException absent) { /* continue */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String[] names, Class<?>... args) {
        for (String name : names) {
            try { Method m = type.getDeclaredMethod(name, args); m.setAccessible(true); return m; }
            catch (NoSuchMethodException absent) { /* try SRG name */ }
        }
        throw new IllegalStateException("Missing text input method in " + type.getName());
    }

    private static Object call(Object object, String name) throws ReflectiveOperationException {
        return object.getClass().getMethod(name).invoke(object);
    }
    private static double transform(Object matrix, String name, float x, float y) throws ReflectiveOperationException {
        return ((Number) matrix.getClass().getMethod(name, float.class, float.class).invoke(matrix, x, y)).doubleValue();
    }
    private static Object callStatic(Class<?> type, String name) throws ReflectiveOperationException {
        return type.getMethod(name).invoke(null);
    }
}
