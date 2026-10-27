package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import codechicken.nei.ItemPanels;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.Widget;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.widget.RecipeWidget;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.wdy.easytouch.touch.GuiTouchMouse;
import io.github.wdy.easytouch.input.ItemTouchGesture;
import io.github.wdy.easytouch.touch.TouchInput;
import io.github.wdy.easytouch.touch.TouchSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Level;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/** Captures panel contacts before GUI mouse injection; ordinary mouse input stays native. */
public final class NeiTouchMenu {
    private static final int ROW = 22;
    private static final int[] PANEL_ACTIONS = {1, 2, 3, 4};
    private static final int[] RECIPE_ACTIONS = {1, 2, 3};
    private static final int[] INVENTORY_ACTIONS = {0, 5, 6};
    private static final int[] INVENTORY_NEI_ACTIONS = {1, 2, 3, 4, 7};
    private int[] actions = PANEL_ACTIONS;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Integer> contacts = new HashMap<>();
    private final Method bookmark;
    private final NeiRecipeTouchTarget recipeTargets;
    private NeiRecipeTouchTarget.Selection recipeSelection;
    private final ContainerItemTarget inventoryTargets;
    private ContainerItemTarget.Selection inventorySelection;
    private GuiContainer screen;
    private ItemStack stack;
    private Widget panel;
    private float touchX, touchY;
    private int itemX, itemY, left, top, width;
    private boolean failed, menuOpen;
    private String holdContact;
    private final ItemTouchGesture gesture;

    private NeiTouchMenu() throws ReflectiveOperationException {
        // RC2 exposes no public entry point for the complete unmodified A-key operation.
        bookmark = ShortcutInputHandler.class.getDeclaredMethod("saveRecipeInBookmark",
            ItemStack.class, boolean.class, boolean.class);
        bookmark.setAccessible(true);
        recipeTargets = new NeiRecipeTouchTarget();
        inventoryTargets = new ContainerItemTarget();
        gesture = new ItemTouchGesture(new ItemTouchGesture.Port() {
            public void hover(float x, float y) { new GuiTouchMouse(mc).move(x, y); }
            public void drag(TouchSample sample) { TouchInput.dragGuiTouch(sample); }
            public void cancelDrag() { TouchInput.cancelGuiTouch(); }
            public void menu() { menuOpen = true; }
            public void tap(TouchSample down, TouchSample up) {
                if (inventorySelection != null) {
                    TouchInput.dragGuiTouch(down);
                    TouchInput.dragGuiTouch(up);
                }
            }
        });
    }

    public static void install() {
        try {
            NeiTouchMenu menu = new NeiTouchMenu();
            TouchInput.setGuiInterceptor(menu::accept);
            MinecraftForge.EVENT_BUS.register(menu);
            FMLCommonHandler.instance().bus().register(menu);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] NEI touch menu unavailable.");
        }
    }

    private Widget panelAt(int x, int y) {
        if (!NEIClientConfig.isLoaded() || LayoutManager.instance() == null) return null;
        Widget widget = LayoutManager.instance().getWidgetUnderMouse(x, y);
        return widget == ItemPanels.itemPanel || widget == ItemPanels.bookmarkPanel ? widget : null;
    }

    private int hit(int x, int y) {
        return menuOpen && stack != null && x >= left && x < left + width && y >= top && y < top + ROW * actions.length
            ? (y - top) / ROW : -1;
    }

    private boolean accept(TouchSample sample) {
        if (sample.kind == TouchSample.Kind.RESET) { close(); contacts.clear(); return false; }
        if (sample.simulated() || !sample.valid()) return false;
        String id = sample.deviceId + ":" + sample.fingerId;
        try {
            if (screen != null && (mc.currentScreen != screen || !Display.isActive())) close();
            Integer captured = contacts.get(id);
            if (captured != null) {
                if (id.equals(holdContact)) {
                    if (!gesture.dragging() && !targetCurrent()) close();
                    else gesture.accept(sample);
                    if (sample.kind == TouchSample.Kind.CANCEL) close();
                    if (sample.kind == TouchSample.Kind.UP) {
                        holdContact = null;
                        if (!menuOpen) close();
                    }
                }
                if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) {
                    contacts.remove(id);
                    if (sample.kind == TouchSample.Kind.UP && screen != null && captured >= 0) {
                        int row = hit((int)(sample.x * screen.width), (int)(sample.y * screen.height));
                        if (row >= 0 && captured == actions[row]) select(row);
                    }
                }
                return true;
            }
            if (failed || !(mc.currentScreen instanceof GuiContainer) || !Display.isActive()) return false;
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            int x = (int)(sample.x * gui.width), y = (int)(sample.y * gui.height);
            if (stack != null) {
                if (sample.kind == TouchSample.Kind.DOWN) {
                    int row = hit(x, y);
                    contacts.put(id, row < 0 ? -1 : actions[row]);
                    if (menuOpen && row < 0) close();
                }
                return true;
            }
            if (sample.kind != TouchSample.Kind.DOWN || TouchInput.guiMouseHeld()) return false;
            Widget target = panelAt(x, y);
            if (target == null) target = recipeTargets.widgetAt(gui, x, y);
            Slot slot = target == null ? inventoryTargets.at(gui, x, y) : null;
            // Preserve normal placement/distribution while the cursor already carries a stack.
            if (slot != null && (mc.thePlayer == null || mc.thePlayer.inventory.getItemStack() != null)) return false;
            ItemStack item = target != null ? target.getStackMouseOver(x, y) : slot != null ? slot.getStack() : null;
            if (item == null || ItemPanels.bookmarkPanel.inEditingState()
                    || ItemPanels.itemPanel.draggedStack != null) return false;
            screen = gui;
            panel = target;
            recipeSelection = target instanceof RecipeWidget
                ? new NeiRecipeTouchTarget.Selection((RecipeWidget) target, x, y) : null;
            inventorySelection = slot == null ? null : new ContainerItemTarget.Selection(gui, slot);
            actions = inventorySelection != null ? INVENTORY_ACTIONS
                : recipeSelection == null ? PANEL_ACTIONS : RECIPE_ACTIONS;
            menuOpen = false;
            holdContact = id;
            stack = item.copy();
            touchX = sample.x; touchY = sample.y;
            itemX = x; itemY = y;
            sizeMenu();
            contacts.put(id, -1); // Opening release must never choose the first row.
            gesture.start(sample, mc.displayWidth, mc.displayHeight);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
            return true;
        }
    }

    private void sizeMenu() {
        width = 64;
        for (int i = 0; i < actions.length; i++) width = Math.max(width, mc.fontRenderer.getStringWidth(label(i)) + 20);
        left = Math.max(0, Math.min(itemX, screen.width - width));
        top = Math.max(0, Math.min(itemY, screen.height - ROW * actions.length));
    }

    private boolean targetCurrent() throws ReflectiveOperationException {
        if (screen == null || mc.currentScreen != screen || !Display.isActive()) return false;
        if (inventorySelection != null) {
            Slot current = inventoryTargets.at(screen, itemX, itemY);
            return inventorySelection.matches(screen, current) && mc.thePlayer != null
                && mc.thePlayer.inventory.getItemStack() == null
                && ItemStack.areItemStacksEqual(stack, current.getStack());
        }
        Widget currentWidget = panelAt(itemX, itemY);
        if (currentWidget == null) currentWidget = recipeTargets.widgetAt(screen, itemX, itemY);
        if (recipeSelection != null && (!(currentWidget instanceof RecipeWidget)
                || !recipeSelection.matches((RecipeWidget) currentWidget, itemX, itemY))) return false;
        ItemStack current = currentWidget == panel ? panel.getStackMouseOver(itemX, itemY) : null;
        // A page/filter/bookmark change must not act on a different item at the old position.
        return current != null && ItemStack.areItemStacksEqual(stack, current);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || screen == null) return;
        try {
            if (mc.currentScreen != screen || !Display.isActive()) close();
            else if (!gesture.dragging()) {
                if (!targetCurrent()) close();
                else gesture.tick(TouchInput.nanoTime());
            }
        } catch (RuntimeException | LinkageError | ReflectiveOperationException error) {
            fail(error);
        }
    }

    private void fail(Throwable error) {
        failed = true;
        close();
        FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Disabling incompatible NEI touch menu.");
    }

    private void select(int row) throws ReflectiveOperationException {
        if (!targetCurrent()) { close(); return; }
        int action = actions[row];
        if (action == 6 || action == 7) {
            actions = action == 6 ? INVENTORY_NEI_ACTIONS : INVENTORY_ACTIONS;
            sizeMenu();
            return;
        }
        if (action == 5 && (inventorySelection == null || !inventorySelection.canSplit())) return;
        ContainerItemTarget.Selection selectedSlot = inventorySelection;
        ItemStack selected = stack;
        GuiContainer source = screen;
        new GuiTouchMouse(mc).move(touchX, touchY);
        close();
        if (action == 1) GuiCraftingRecipe.openRecipeGui("item", selected);
        else if (action == 2) GuiUsageRecipe.openRecipeGui("item", selected);
        else if (action == 3) bookmark.invoke(null, selected, false, false);
        else if (action == 4) NeiFavoriteTreeShortcut.openFavorite(source);
        else if (action == 5) selectedSlot.split(source);
    }

    private boolean enabled(int row) {
        return actions[row] != 5 || inventorySelection != null && inventorySelection.canSplit();
    }

    private String label(int row) { return I18n.format("easytouch.neiMenu." + actions[row]); }
    private void close() {
        gesture.cancel();
        screen = null; stack = null; panel = null; recipeSelection = null; inventorySelection = null;
        holdContact = null; menuOpen = false;
    }

    @SubscribeEvent
    public void gui(GuiOpenEvent event) { close(); }

    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!menuOpen || stack == null || event.gui != screen || !Display.isActive()) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 500);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        try {
            Gui.drawRect(left - 1, top - 1, left + width + 1, top + ROW * actions.length + 1, 0xff777777);
            for (int i = 0; i < actions.length; i++) {
                int y = top + i * ROW;
                Gui.drawRect(left, y, left + width, y + ROW, 0xf0202020);
                if (i > 0) Gui.drawRect(left + 4, y, left + width - 4, y + 1, 0xff555555);
                mc.fontRenderer.drawStringWithShadow(label(i), left + 10, y + (ROW - 8) / 2, enabled(i) ? 0xffffff : 0x777777);
            }
        } finally {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }
}
