package io.github.wdy.easytouch.nei;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.tree.CraftingTreeGraph;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.scroll.GuiHelper;
import codechicken.nei.util.ReadableNumberConverter;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.Level;
import org.lwjgl.opengl.GL11;

/** Read-only annotations: neither bookmark quantities nor crafting plans are changed here. */
final class NeiTreeQuantityDisplay implements IContainerDrawHandler, IContainerTooltipHandler {
    private final Field canvas = field(GuiCraftingTree.class, "canvas");
    private final Field hoveredNode = field(GuiCraftingTree.class, "hoveredNode");
    private final Field graph = field(canvas.getType(), "graph");
    private final Field points = field(canvas.getType(), "pointByNode");
    private final Field camera = field(canvas.getType(), "camera");
    private final Field widgetX = field(camera.getType(), "widgetX");
    private final Field widgetY = field(camera.getType(), "widgetY");
    private final Field widgetW = field(camera.getType(), "widgetW");
    private final Field widgetH = field(camera.getType(), "widgetH");
    private final Field scrollX = field(camera.getType(), "scrollX");
    private final Field scrollY = field(camera.getType(), "scrollY");
    private final Field zoom = field(camera.getType(), "zoomLevel");
    private final Field ingredient = field(hoveredNode.getType(), "ingrItem");
    private final Field requested = field(hoveredNode.getType(), "currentRequestedAmount");
    private GuiCraftingTree cachedTree;
    private ItemStackAmount inventory;
    private long checkedAt;
    private boolean failed;

    private NeiTreeQuantityDisplay() throws ReflectiveOperationException { }

    static void install() {
        try {
            NeiTreeQuantityDisplay display = new NeiTreeQuantityDisplay();
            GuiContainerManager.addDrawHandler(display);
            GuiContainerManager.addTooltipHandler(display);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] NEI quantity annotations unavailable.");
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private boolean updateInventory(GuiCraftingTree tree) {
        GuiContainer source = tree.getFirstScreen();
        if (source == null || source == tree || source.inventorySlots == null) return false;
        long now = Minecraft.getSystemTime();
        if (cachedTree != tree || inventory == null || now - checkedAt >= 250) {
            inventory = AutoCraftingManager.getInventoryItems(source);
            cachedTree = tree;
            checkedAt = now;
        }
        return true;
    }

    private long owned(BookmarkItem item) {
        long amount = 0;
        for (ItemStack stack : inventory.values()) {
            if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, item.itemStack)) {
                amount += StackInfo.getAmount(stack);
            }
        }
        return amount;
    }

    static long missing(long owned, long target) {
        return target > owned ? target - owned : 0;
    }

    private long target(Object node, BookmarkItem item) throws IllegalAccessException {
        // Use the root's own target, excluding demand inherited from other output recipes.
        // Convert fluid quantities to the same item/container units as the inventory count.
        return item.getStackSize(requested.getLong(node));
    }

    private static String compact(long amount) {
        return amount < 10000 ? Long.toString(amount)
            : ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void renderObjects(GuiContainer gui, int mouseX, int mouseY) {
        if (failed || !(gui instanceof GuiCraftingTree)) return;
        try {
            GuiCraftingTree tree = (GuiCraftingTree) gui;
            if (!updateInventory(tree)) return;
            Object view = canvas.get(tree);
            if (view == null) return;
            CraftingTreeGraph treeGraph = (CraftingTreeGraph) graph.get(view);
            Map<Object, Point> positions = (Map<Object, Point>) points.get(view);
            Object viewport = camera.get(view);
            int x = widgetX.getInt(viewport), y = widgetY.getInt(viewport);
            int width = widgetW.getInt(viewport), height = widgetH.getInt(viewport);
            float scale = zoom.getFloat(viewport);
            float offsetX = x - scrollX.getFloat(viewport) * scale;
            float offsetY = y - scrollY.getFloat(viewport) * scale;
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            // At very small zoom the native icons are already tiny; retain the exact tooltip.
            if (scale < 0.5f) return;
            for (Object node : treeGraph.roots) {
                Point point = positions.get(node);
                if (point == null) continue;
                BookmarkItem item = (BookmarkItem) ingredient.get(node);
                long owned = owned(item);
                String unit = StackInfo.isFluidDisplayItem(item.itemStack) ? "L" : "";
                String label = compact(owned) + unit + " + " + compact(missing(owned, target(node, item))) + unit;
                float textScale = Math.min(1.0f, scale);
                float labelX = offsetX + (point.x + 9) * scale;
                float labelY = offsetY + point.y * scale - (font.FONT_HEIGHT + 2) * textScale;
                GuiHelper.useScissor(x, y, width, height, () -> {
                    GL11.glPushMatrix();
                    GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
                    try {
                        GL11.glDisable(GL11.GL_LIGHTING);
                        GL11.glDisable(GL11.GL_DEPTH_TEST);
                        GL11.glColor4f(1, 1, 1, 1);
                        GL11.glTranslatef(labelX, labelY, 0);
                        GL11.glScalef(textScale, textScale, 1);
                        font.drawStringWithShadow(label, -font.getStringWidth(label) / 2, 0, 0xFFFFFF);
                    } finally {
                        GL11.glPopAttrib();
                        GL11.glPopMatrix();
                    }
                });
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mouseX, int mouseY,
            List<String> tips) {
        if (failed || !(gui instanceof GuiCraftingTree)) return tips;
        try {
            GuiCraftingTree tree = (GuiCraftingTree) gui;
            if (!updateInventory(tree)) return tips;
            Object view = canvas.get(tree);
            Object node = hoveredNode.get(tree);
            if (view == null || node == null || !((CraftingTreeGraph) graph.get(view)).roots.contains(node)) return tips;
            BookmarkItem item = (BookmarkItem) ingredient.get(node);
            long owned = owned(item);
            long target = target(node, item);
            String unit = StackInfo.isFluidDisplayItem(item.itemStack) ? "L" : "";
            tips.add(I18n.format("easytouch.craftTree.quantity", owned + unit,
                missing(owned, target) + unit, target + unit));
            tips.add(I18n.format("easytouch.craftTree.quantityHint"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
        return tips;
    }

    private void fail(Throwable error) {
        failed = true;
        FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Disabling NEI quantity annotations.");
    }

    @Override public void onPreDraw(GuiContainer gui) {
        if (gui != cachedTree) { cachedTree = null; inventory = null; }
    }
    @Override public void postRenderObjects(GuiContainer gui, int x, int y) { }
    @Override public void renderSlotUnderlay(GuiContainer gui, Slot slot) { }
    @Override public void renderSlotOverlay(GuiContainer gui, Slot slot) { }
}
