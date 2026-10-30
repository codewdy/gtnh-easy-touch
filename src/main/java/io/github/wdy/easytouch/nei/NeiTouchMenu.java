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
public final class NeiTouchMenu implements codechicken.nei.guihook.IContainerInputHandler {
    private boolean mouseSliding;
    private static final int ROW = 22;
    private static final int[] PANEL_ACTIONS = {1, 2, 3, 4};
    private static final int[] RECIPE_ACTIONS = {1, 2, 3};
    private static final int[] INVENTORY_ACTIONS = {0, 5, 8, 6};
    private static final int[] INVENTORY_NEI_ACTIONS = {1, 2, 3, 4, 7};
    private static final int[] QUANTITY_ACTIONS = {9, 10, 11, 12};
    private int quantity, maximum;
    private AeItemTarget aeTargets;
    private AeItemTarget.Selection aeSelection, pendingAe;
    private int requested, received, awaiting;
    private long deadline;
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
        try { aeTargets = new AeItemTarget(); }
        catch (ReflectiveOperationException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] AE quantity pickup unavailable.");
        }
        gesture = new ItemTouchGesture(new ItemTouchGesture.Port() {
            public void hover(float x, float y) { new GuiTouchMouse(mc).move(x, y); }
            public void drag(TouchSample sample) { TouchInput.dragGuiTouch(sample); }
            public void cancelDrag() { TouchInput.cancelGuiTouch(); }
            public void menu() { menuOpen = true; }
            public void tap(TouchSample down, TouchSample up) {
                TouchInput.dragGuiTouch(down);
                TouchInput.dragGuiTouch(up);
            }
        });
    }

    public static void install() {
        try {
            NeiTouchMenu menu = new NeiTouchMenu();
            NeiSearchTouch search = new NeiSearchTouch();
            TouchInput.setGuiInterceptor(sample -> {
                boolean consumed = menu.accept(sample);
                if (!consumed || sample.kind == TouchSample.Kind.RESET) consumed |= search.accept(sample);
                return consumed;
            });
            MinecraftForge.EVENT_BUS.register(search);
            FMLCommonHandler.instance().bus().register(search);
            codechicken.nei.guihook.GuiContainerManager.addInputHandler(menu);
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
        if (sample.kind == TouchSample.Kind.RESET) { pendingAe = null; close(); contacts.clear(); return false; }
        if (sample.simulated() || !sample.valid()) return false;
        String id = sample.deviceId + ":" + sample.fingerId;
        try {
            if (screen != null && (mc.currentScreen != screen || !Display.isActive())) close();
            if (pendingAe != null && sample.kind == TouchSample.Kind.DOWN) {
                pendingAe = null;
                contacts.put(id, -1);
                return true;
            }
            Integer captured = contacts.get(id);
            if (captured != null) {
                if (captured == 10 && screen != null && actions == QUANTITY_ACTIONS) {
                    if (sample.kind != TouchSample.Kind.CANCEL) slide((int) (sample.x * screen.width));
                }
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
                    else if (row >= 0 && actions[row] == 10) slide(x);
                }
                return true;
            }
            if (sample.kind != TouchSample.Kind.DOWN || TouchInput.guiMouseHeld()
                    || TouchInput.guiAwaitingSecond()) return false;
            Widget target = panelAt(x, y);
            if (target == null) target = recipeTargets.widgetAt(gui, x, y);
            Slot slot = target == null ? inventoryTargets.at(gui, x, y) : null;
            AeItemTarget.Selection ae = target == null && slot == null && aeTargets != null
                ? aeTargets.at(gui, x, y) : null;
            // Preserve normal placement/distribution while the cursor already carries a stack.
            if ((slot != null || ae != null) && (mc.thePlayer == null || mc.thePlayer.inventory.getItemStack() != null)) return false;
            ItemStack item = target != null ? target.getStackMouseOver(x, y) : slot != null ? slot.getStack() : ae != null ? ae.item : null;
            if (item == null || ItemPanels.bookmarkPanel.inEditingState()
                    || ItemPanels.itemPanel.draggedStack != null) return false;
            screen = gui;
            panel = target;
            recipeSelection = target instanceof RecipeWidget
                ? new NeiRecipeTouchTarget.Selection((RecipeWidget) target, x, y) : null;
            aeSelection = ae;
            inventorySelection = slot == null ? null : new ContainerItemTarget.Selection(gui, slot);
            actions = inventorySelection != null || aeSelection != null ? INVENTORY_ACTIONS
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
        width = actions == QUANTITY_ACTIONS ? Math.min(180, screen.width - 4) : 64;
        for (int i = 0; i < actions.length; i++) width = Math.max(width, mc.fontRenderer.getStringWidth(label(i)) + 20);
        left = Math.max(0, Math.min(itemX, screen.width - width));
        top = Math.max(0, Math.min(itemY, screen.height - ROW * actions.length));
    }

    private boolean targetCurrent() throws ReflectiveOperationException {
        if (screen == null || mc.currentScreen != screen || !Display.isActive()) return false;
        if (aeSelection != null) return aeSelection.current()
            && mc.thePlayer.inventory.getItemStack() == null;
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
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingAe != null) {
            try { tickAe(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                pendingAe = null;
                FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] AE pickup stopped.");
            }
        }
        if (screen == null) return;
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
        if (!enabled(row)) return;
        if (action == 5) {
            maximum = aeSelection != null ? aeSelection.maximum : inventorySelection.slot.getStack().stackSize;
            quantity = Math.max(1, (maximum + 1) / 2);
            actions = QUANTITY_ACTIONS;
            sizeMenu();
            return;
        }
        if (action == 9 || action == 10) return;
        if (action == 12) { close(); return; }
        if (action == 11) {
            if (aeSelection != null) {
                pendingAe = aeSelection;
                requested = quantity; received = 0; awaiting = 0;
                deadline = System.currentTimeMillis() + 3000;
            } else if (inventorySelection != null) inventorySelection.split(screen, quantity);
            close();
            return;
        }
        ContainerItemTarget.Selection selectedSlot = inventorySelection;
        ItemStack selected = stack;
        GuiContainer source = screen;
        new GuiTouchMouse(mc).move(touchX, touchY);
        close();
        if (action == 1) GuiCraftingRecipe.openRecipeGui("item", selected);
        else if (action == 2) GuiUsageRecipe.openRecipeGui("item", selected);
        else if (action == 3) bookmark.invoke(null, selected, false, false);
        else if (action == 4) NeiFavoriteTreeShortcut.openFavorite(source);
        else if (action == 8) selectedSlot.collect(source);
    }

    private boolean enabled(int row) {
        if (actions[row] == 5) return aeSelection != null || inventorySelection != null && inventorySelection.canSplit();
        if (actions[row] == 8) return inventorySelection != null && inventorySelection.canCollect();
        return true;
    }

    private String label(int row) {
        if (actions[row] == 9) return I18n.format("easytouch.split.quantity", quantity, maximum);
        if (actions[row] == 10) return "";
        return I18n.format("easytouch.neiMenu." + actions[row]);
    }

    private void slide(int x) {
        int span = Math.max(1, width - 24);
        quantity = 1 + Math.round(Math.max(0, Math.min(span, x - left - 12)) * (maximum - 1f) / span);
    }

    private void tickAe() throws ReflectiveOperationException {
        if (!pendingAe.current() || !Display.isActive()) { pendingAe = null; return; }
        ItemStack hand = mc.thePlayer.inventory.getItemStack();
        int count = hand == null ? 0 : hand.stackSize;
        if (hand != null && (!hand.isItemEqual(pendingAe.item)
            || !ItemStack.areItemStackTagsEqual(hand, pendingAe.item))) { pendingAe = null; return; }
        if (awaiting != 0) {
            if (count == awaiting) { received = count; awaiting = 0; }
            else if (count != received || System.currentTimeMillis() >= deadline) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    I18n.format("easytouch.split.stopped", count, requested)));
                pendingAe = null;
                return;
            } else return;
        } else if (count != received) { pendingAe = null; return; }
        if (received >= requested) { pendingAe = null; return; }
        pendingAe.takeOne();
        awaiting = received + 1;
        deadline = System.currentTimeMillis() + 3000;
    }
    private void close() {
        gesture.cancel();
        screen = null; stack = null; panel = null; recipeSelection = null; inventorySelection = null;
        holdContact = null; menuOpen = false; aeSelection = null; mouseSliding = false;
    }

    @Override public boolean mouseClicked(GuiContainer gui, int x, int y, int button) {
        if (pendingAe != null) { pendingAe = null; return true; }
        if (gui != screen || !menuOpen) return false;
        try {
            int row = hit(x, y);
            if (button != 0 || row < 0) close();
            else if (actions[row] == 10) { mouseSliding = true; slide(x); }
            else select(row);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { fail(error); }
        return true;
    }
    @Override public void onMouseDragged(GuiContainer gui, int x, int y, int button, long time) {
        if (mouseSliding && gui == screen) slide(x);
    }
    @Override public void onMouseUp(GuiContainer gui, int x, int y, int button) { mouseSliding = false; }
    @Override public boolean mouseScrolled(GuiContainer gui, int x, int y, int scroll) {
        if (gui != screen || !menuOpen) return false;
        if (actions == QUANTITY_ACTIONS) quantity = Math.max(1, Math.min(maximum, quantity + Integer.signum(scroll)));
        return true;
    }
    @Override public boolean keyTyped(GuiContainer gui, char c, int key) {
        pendingAe = null;
        if (gui != screen || !menuOpen) return false;
        close();
        return true;
    }
    @Override public void onKeyTyped(GuiContainer gui, char c, int key) { pendingAe = null; }
    @Override public boolean lastKeyTyped(GuiContainer gui, char c, int key) { return false; }
    @Override public void onMouseClicked(GuiContainer gui, int x, int y, int button) { }
    @Override public void onMouseScrolled(GuiContainer gui, int x, int y, int scroll) { pendingAe = null; }

    @SubscribeEvent
    public void gui(GuiOpenEvent event) { pendingAe = null; close(); }

    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (pendingAe != null && event.gui == pendingAe.gui && Display.isActive()) {
            mc.fontRenderer.drawStringWithShadow(I18n.format("easytouch.split.progress", received, requested), 8, 8, 0xffffff);
        }
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
                if (actions[i] == 10) {
                    int knob = left + 12 + (maximum <= 1 ? 0 : Math.round((width - 24f) * (quantity - 1) / (maximum - 1)));
                    Gui.drawRect(left + 12, y + 9, left + width - 12, y + 13, 0xff777777);
                    Gui.drawRect(left + 12, y + 9, knob, y + 13, 0xff55bbff);
                    Gui.drawRect(knob - 4, y + 3, knob + 5, y + ROW - 3, 0xffeeeeee);
                }
                mc.fontRenderer.drawStringWithShadow(label(i), left + 10, y + (ROW - 8) / 2, enabled(i) ? 0xffffff : 0x777777);
            }
        } finally {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }
}
