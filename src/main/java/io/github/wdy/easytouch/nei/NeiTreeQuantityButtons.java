package io.github.wdy.easytouch.nei;

import java.util.List;
import java.lang.reflect.Field;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Item-count quantity buttons with touch/mouse hold-to-repeat in a reserved canvas strip. */
// Forge dispatches @SubscribeEvent methods through classes generated in another package/loader.
public final class NeiTreeQuantityButtons extends Gui {
    interface Context {
        boolean active(GuiScreen screen);
        void change(GuiCraftingTree tree, BookmarkItem item, int steps) throws ReflectiveOperationException;
        void failed(Throwable error);
    }

    private static final int WIDTH = 38;
    private static final int MAX_HEIGHT = 104;
    private int height = MAX_HEIGHT;
    private int xPosition = 12;
    private int yPosition = 34;
    private final Field canvas;
    private final Field camera;
    private final Field widgetX;
    private final Field widgetY;
    private final Field widgetW;
    private final Field widgetH;
    private static final int HEADER = 24;
    private final Context context;
    private final NeiTreeQuantityScroll quantities;
    private final QuantityButtonRepeat repeat = new QuantityButtonRepeat();
    private final RenderItem renderer = new RenderItem();
    private GuiCraftingTree tree;
    private BookmarkItem selected;
    private int heldButton = -1;
    private int pressedPart = -1;
    private int outputCount;

    NeiTreeQuantityButtons(NeiTreeQuantityScroll quantities, Context context) throws ReflectiveOperationException {
        this.quantities = quantities;
        this.context = context;
        canvas = field(GuiCraftingTree.class, "canvas");
        camera = field(canvas.getType(), "camera");
        widgetX = field(camera.getType(), "widgetX");
        widgetY = field(camera.getType(), "widgetY");
        widgetW = field(camera.getType(), "widgetW");
        widgetH = field(camera.getType(), "widgetH");
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    void layout(GuiCraftingTree gui) throws ReflectiveOperationException {
        Object viewport = camera.get(canvas.get(gui));
        // Native rebuildStatsPanel resets the viewport. Reserve the strip again after each rebuild.
        int nativeLeft = 12;
        int reservedLeft = nativeLeft + WIDTH + 8;
        int currentLeft = widgetX.getInt(viewport);
        if (currentLeft < reservedLeft) {
            widgetW.setInt(viewport, Math.max(1, widgetW.getInt(viewport) - (reservedLeft - currentLeft)));
            widgetX.setInt(viewport, reservedLeft);
        }
        xPosition = nativeLeft;
        height = Math.min(MAX_HEIGHT, widgetH.getInt(viewport));
        yPosition = widgetY.getInt(viewport) + Math.max(0, (widgetH.getInt(viewport) - height) / 2);
    }

    private int left() { return xPosition; }
    private int top(GuiScreen gui) { return yPosition; }

    boolean contains(GuiScreen gui, int x, int y) {
        return height >= 80 && context.active(gui) && x >= left() && x < left() + WIDTH
            && y >= top(gui) && y < top(gui) + height;
    }

    void cancel() { heldButton = -1; pressedPart = -1; repeat.cancel(); }

    void tick(GuiScreen current) {
        if (current != tree || !context.active(current)) {
            cancel();
            tree = null;
            selected = null;
            return;
        }
        if (heldButton < 0) return;
        if (!Mouse.isButtonDown(heldButton)) { cancel(); return; }
        Minecraft mc = Minecraft.getMinecraft();
        int x = Mouse.getX() * current.width / mc.displayWidth;
        int y = current.height - Mouse.getY() * current.height / mc.displayHeight - 1;
        if (part(current, x, y) != pressedPart) { cancel(); return; }
        if (pressedPart < 0 || !repeat.poll(Minecraft.getSystemTime())) return;
        try { change(steps(pressedPart)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError error) { context.failed(error); }
    }

    // -2 is the output selector, -1 is outside; 0..3 are the quantity buttons.
    private int part(GuiScreen gui, int x, int y) {
        if (!contains(gui, x, y)) return -1;
        int localY = y - top(gui);
        if (localY < HEADER) return -2;
        for (int row = 0; row < 4; row++) {
            if (localY < HEADER + (row + 1) * (height - HEADER) / 4) return row;
        }
        return -1;
    }

    private void select(GuiCraftingTree current, boolean next) throws ReflectiveOperationException {
        if (tree != current) {
            cancel();
            selected = null;
            tree = current;
        }
        List<BookmarkItem> outputs = quantities.outputs(current);
        outputCount = outputs.size();
        int index = -1;
        if (selected != null) {
            for (int i = 0; i < outputs.size(); i++) {
                BookmarkItem item = outputs.get(i);
                if (selected.recipeId.equals(item.recipeId)
                        && NEIClientUtils.areStacksSameTypeCraftingWithNBT(selected.itemStack, item.itemStack)) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0) {
            if (selected != null) cancel();
            index = 0;
        }
        else if (next) index = (index + 1) % Math.max(1, outputs.size());
        selected = outputs.isEmpty() ? null : outputs.get(index);
    }

    private int steps(int part) {
        int amount = part == 0 || part == 3 ? 64 : 1;
        return part < 2 ? amount : -amount;
    }

    private String label(int part) {
        return (part < 2 ? "+" : "") + steps(part)
            + (selected != null && StackInfo.isFluidDisplayItem(selected.itemStack) ? "L" : "");
    }

    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!context.active(event.gui)) return;
        cancel();
        try { layout((GuiCraftingTree) event.gui); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError error) { context.failed(error); }
    }

    boolean click(GuiScreen gui, int x, int y, int button) {
        if (!contains(gui, x, y)) return false;
        try {
            select((GuiCraftingTree) gui, false);
            if (selected != null && heldButton < 0 && (button == 0 || button == 1)) {
                heldButton = button;
                pressedPart = part(gui, x, y);
                if (pressedPart >= 0) {
                    repeat.begin(Minecraft.getSystemTime());
                    change(steps(pressedPart));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { context.failed(error); }
        return true;
    }

    void release(GuiScreen gui, int x, int y, int button) {
        if (heldButton != button || gui != tree) return;
        try {
            if (context.active(gui) && pressedPart == -2 && part(gui, x, y) == -2) select(tree, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { context.failed(error); }
        finally { cancel(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void beforeDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!context.active(event.gui)) { cancel(); return; }
        try {
            select((GuiCraftingTree) event.gui, false);
            layout(tree);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { context.failed(error); }
    }

    private void change(int steps) throws ReflectiveOperationException {
        if (selected == null || steps == 0) return;
        context.change(tree, selected, steps);
        layout(tree);
        select(tree, false);
    }

    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!context.active(event.gui)) return;
        try {
            select((GuiCraftingTree) event.gui, false);
            if (height < 80) return;
            Minecraft mc = Minecraft.getMinecraft();
            int x = left(), y = top(event.gui);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0, 0, 300);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                drawRect(x, y, x + WIDTH, y + height, 0xED161C24);
                drawRect(x, y + HEADER, x + WIDTH, y + HEADER + 1, 0xFF5A6978);
                int color = selected == null ? 0xFF555555 : heldButton >= 0 ? 0xFF69CFFF : 0xFFB4C4D4;
                drawRect(x, y + HEADER, x + 1, y + height, color);
                drawRect(x + WIDTH - 1, y + HEADER, x + WIDTH, y + height, color);
                if (selected != null) {
                    RenderHelper.enableGUIStandardItemLighting();
                    renderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(),
                        selected.getItemStack(1), x + (WIDTH - 16) / 2, y + 4);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                }
                if (outputCount > 1) mc.fontRenderer.drawStringWithShadow(">", x + WIDTH - 9, y + 8, 0xFFFFFF);
                for (int i = 0; i < 4; i++) {
                    int by = y + HEADER + i * (height - HEADER) / 4;
                    int bottom = y + HEADER + (i + 1) * (height - HEADER) / 4;
                    int bh = bottom - by;
                    boolean pressed = heldButton >= 0 && pressedPart == i;
                    drawRect(x + 1, by + 1, x + WIDTH - 1, bottom - 1,
                        pressed ? 0xFF365F7B : 0xFF293541);
                    drawCenteredString(mc.fontRenderer, label(i), x + WIDTH / 2,
                        by + (bh - mc.fontRenderer.FONT_HEIGHT) / 2,
                        selected == null ? 0x777777 : pressed ? 0x69CFFF : 0xFFFFFF);
                }
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            cancel();
            context.failed(error);
        }
    }
}
