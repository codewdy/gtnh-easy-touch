package io.github.wdy.easytouch.nei;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import codechicken.nei.ItemPanels;
import codechicken.nei.KeyManager;
import codechicken.nei.GuiNEIButton;
import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.chain.RecipeChainMath;
import codechicken.nei.util.NEIMouseUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.inventory.Container;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Level;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** Optional NEI 2.8.155 integration for GTNH 2.9.0 RC2. Loaded only when NEI is present. */
public final class NeiCraftingTreeInput implements IContainerInputHandler, IContainerTooltipHandler {
    private final Field grid;
    private final Field groupId;
    private final Field searchField;
    private final NeiTreeQuantityScroll quantityScroll;
    private final NeiTreeQuantityButtons quantityButtons;
    private boolean failed;
    private PendingCraft pending;
    private GuiCraftingTree buttonTree;
    private final List<TreeButton> buttons = new ArrayList<>();
    private GuiCraftingTree checkedTree;
    private long checkedAt;
    private String cachedReason;

    private NeiCraftingTreeInput() throws ReflectiveOperationException {
        // NEI has no public accessors for the tree's selected group or search field bounds in RC2.
        grid = field("grid");
        groupId = field("groupId");
        searchField = field("searchField");
        quantityScroll = new NeiTreeQuantityScroll();
        quantityButtons = new NeiTreeQuantityButtons(quantityScroll, new NeiTreeQuantityButtons.Context() {
            @Override public boolean active(GuiScreen screen) { return isTree(screen); }
            @Override public void change(GuiCraftingTree tree, BookmarkItem item, int steps)
                    throws ReflectiveOperationException {
                BookmarkGrid bookmarks = (BookmarkGrid) grid.get(tree);
                int selectedGroup = groupId.getInt(tree);
                if (bookmarks == null || bookmarks.getGroup(selectedGroup) == null) return;
                cancelPending();
                checkedTree = null;
                quantityScroll.changeFixedSteps(tree, bookmarks, selectedGroup, item, steps);
            }
            @Override public void failed(Throwable error) { fail(error); }
        });
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field result = GuiCraftingTree.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    public static void install() {
        try {
            NeiCraftingTreeInput input = new NeiCraftingTreeInput();
            GuiContainerManager.addInputHandler(input);
            GuiContainerManager.addTooltipHandler(input);
            NeiTreeQuantityDisplay.install();
            MinecraftForge.EVENT_BUS.register(input);
            MinecraftForge.EVENT_BUS.register(input.quantityButtons);
            FMLCommonHandler.instance().bus().register(input);
            // The first touch may arrive while a crafting tree is already open.
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof GuiCraftingTree) {
                List buttons = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(
                    GuiScreen.class, screen, "buttonList", "field_146292_n");
                input.initGui(new GuiScreenEvent.InitGuiEvent.Post(screen, buttons));
                input.quantityButtons.init(new GuiScreenEvent.InitGuiEvent.Post(screen, buttons));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] NEI crafting tree controls unavailable.");
        }
    }

    @SubscribeEvent
    public void recipeButtons(codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        // The native heart handles Shift+T before our last-key callback. Replace only
        // NEI's own button; keep normal favorite clicks and Shift+A bookmark saving intact.
        for (int i = 0; i < event.buttonList.size(); i++) {
            codechicken.nei.recipe.GuiRecipeButton original = event.buttonList.get(i);
            if (original.getClass() != codechicken.nei.recipe.GuiFavoriteButton.class) continue;
            event.buttonList.set(i, new codechicken.nei.recipe.GuiFavoriteButton(
                    original.handlerRef, original.xPosition, original.yPosition) {
                @Override
                public void lastKeyTyped(char keyChar, int keyCode) {
                    if (keyCode == KeyManager.getKeyCode(NeiFavoriteTreeShortcut.KEY)
                            && KeyManager.isHashDown(NeiFavoriteTreeShortcut.KEY, NEIClientUtils.SHIFT_HASH)) {
                        if (failed || org.lwjgl.input.Keyboard.isRepeatEvent()
                                || !NeiFavoriteTreeShortcut.canUse((GuiContainer) event.gui)) return;
                        try {
                            NeiFavoriteTreeShortcut.openRecipe(this);
                        } catch (RuntimeException | LinkageError error) {
                            fail(error);
                        }
                    } else {
                        super.lastKeyTyped(keyChar, keyCode);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (failed || !(event.gui instanceof GuiCraftingTree)) return;
        try {
            GuiTextField search = (GuiTextField) searchField.get(event.gui);
            if (search == null) return;
            GuiButton saveImage = null;
            // RC2's native toolbar ends with SCREENSHOT_BUTTON_ID = 6 (Save Image).
            for (Object entry : event.buttonList) {
                GuiButton button = (GuiButton) entry;
                if (button.id == 6) { saveImage = button; break; }
            }
            if (saveImage == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            int saveImageRight = saveImage.xPosition + saveImage.width;
            int titleLeft = (event.gui.width - mc.fontRenderer.getStringWidth(
                I18n.format("nei.bookmark.tree.title"))) / 2;
            int available = Math.min(titleLeft, search.xPosition - 2) - saveImageRight;
            int gap = available >= 24 ? 4 : 1;
            int x = saveImageRight + gap;
            buttonTree = (GuiCraftingTree) event.gui;
            checkedTree = null;
            buttons.clear();
            buttons.add(new TreeButton(x, saveImage.yPosition));
            event.buttonList.addAll(buttons);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public void clickButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof TreeButton)) return;
        event.setCanceled(true);
        if (isTree(event.gui)) {
            event.button.func_146113_a(Minecraft.getMinecraft().getSoundHandler());
            craft((GuiCraftingTree) event.gui);
        }
    }

    private final class TreeButton extends GuiNEIButton {
        TreeButton(int x, int y) {
            super(0x455443, x, y, 16, 16, I18n.format("easytouch.craftTree.buttonCraft"));
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            enabled = unavailableReason(buttonTree, false) == null;
            super.drawButton(mc, mouseX, mouseY);
        }

        @Override
        protected void drawContent(Minecraft mc, int x, int y, boolean hovered) {
            TreeActionIcon.CRAFT.draw(xPosition + (width - TreeActionIcon.SIZE) / 2,
                yPosition + (height - TreeActionIcon.SIZE) / 2, enabled, hovered);
        }
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int x, int y, List<String> tips) {
        if (gui != buttonTree) return tips;
        if (quantityButtons.contains(gui, x, y)) {
            tips.clear();
            return tips;
        }
        for (TreeButton button : buttons) {
            if (x >= button.xPosition && x < button.xPosition + button.width
                    && y >= button.yPosition && y < button.yPosition + button.height) {
                tips.add(button.displayString);
                tips.add(I18n.format("easytouch.craftTree.craftHint"));
                String reason = unavailableReason(buttonTree, false);
                if (reason != null) tips.add(I18n.format("easytouch.craftTree." + reason));
                break;
            }
        }
        return tips;
    }

    private String unavailableReason(GuiCraftingTree tree, boolean refresh) {
        if (failed) return "unavailable";
        if (!isTree(tree)) return "inactive";
        try {
            if (!NEIClientConfig.isLoaded() || !NEIClientConfig.autocraftingEnabled()) return "disabled";
            if (pending != null || AutoCraftingManager.processing()) return "busy";
            if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null) return "heldItem";
            GuiContainer container = tree.getFirstScreen();
            if (container == null || container == tree || container.inventorySlots == null
                    || container.inventorySlots.inventorySlots.isEmpty()) return "noContainer";
            long now = Minecraft.getSystemTime();
            // Cache the read-only probe; do not rebuild the recipe chain every frame.
            if (refresh || checkedTree != tree || now - checkedAt >= 250) {
                BookmarkGrid bookmarks = (BookmarkGrid) grid.get(tree);
                int selectedGroup = groupId.getInt(tree);
                if (bookmarks == null || bookmarks.getGroup(selectedGroup) == null
                        || !bookmarks.isCraftingMode(selectedGroup)) cachedReason = "noChain";
                else cachedReason = NeiCraftingAvailability.canStart(bookmarks, selectedGroup, container)
                    ? null : "notCraftable";
                checkedTree = tree;
                checkedAt = now;
            }
            return cachedReason;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
            return "unavailable";
        }
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int x, int y, Map<String, String> hotkeys) {
        if (failed) return hotkeys;
        try {
            if (NeiFavoriteTreeShortcut.favoriteUnderMouse(gui) != null) {
                hotkeys.put(KeyManager.getKeyName(NeiFavoriteTreeShortcut.KEY, NEIClientUtils.SHIFT_HASH),
                    I18n.format("easytouch.craftTree.favoriteShortcut"));
            }
            if (!isTree(gui)) return hotkeys;
            if (quantityScroll.outputUnderMouse((GuiCraftingTree) gui) != null) {
                int wheel = NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL;
                hotkeys.remove(NEIClientUtils.getKeyName(NEIClientUtils.CTRL_HASH, wheel));
                hotkeys.remove(NEIClientUtils.getKeyName(NEIClientUtils.CTRL_HASH + NEIClientUtils.ALT_HASH, wheel));
                hotkeys.put(NEIClientUtils.getKeyName(0, wheel),
                    I18n.format("nei.bookmark.change_quantity"));
                hotkeys.put(NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, wheel),
                    I18n.format("nei.bookmark.change_quantity_step"));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
        return hotkeys;
    }

    private boolean isTree(Object screen) {
        Minecraft mc = Minecraft.getMinecraft();
        return !failed && screen instanceof GuiCraftingTree && mc.currentScreen == screen
            && mc.thePlayer != null && mc.theWorld != null && !mc.thePlayer.isDead && Display.isActive();
    }

    private static boolean mouseReleased() {
        for (int button = 0; button < Mouse.getButtonCount(); button++) {
            if (Mouse.isButtonDown(button)) return false;
        }
        return true;
    }

    private void craft(GuiCraftingTree tree) {
        try {
            String reason = unavailableReason(tree, true);
            if (reason != null) {
                message(reason);
                return;
            }
            GuiContainer container = tree.getFirstScreen();
            if (container == null || container == tree || container.inventorySlots == null
                || container.inventorySlots.inventorySlots.isEmpty()) {
                message("noContainer");
                return;
            }
            BookmarkGrid bookmarks = (BookmarkGrid) grid.get(tree);
            int selectedGroup = groupId.getInt(tree);
            if (bookmarks == null || bookmarks.getGroup(selectedGroup) == null
                || !bookmarks.isCraftingMode(selectedGroup)) {
                message("noChain");
                return;
            }
            // Build from the selected tree's own grid, never the hovered item or another bookmark page.
            RecipeChainMath plan = bookmarks.createRecipeChainMath(selectedGroup);
            if (plan == null || plan.outputRecipes.isEmpty()) {
                message("noChain");
                return;
            }

            // The button's press/release must finish in the tree. The inventory synchronization
            // reply must also finish before NEI starts moving stacks on its worker thread.
            pending = new PendingCraft(tree, container, plan, bookmarks, selectedGroup);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        quantityButtons.tick(Minecraft.getMinecraft().currentScreen);
        if (Minecraft.getMinecraft().currentScreen != buttonTree) {
            buttonTree = null;
            checkedTree = null;
            buttons.clear();
        }
        if (pending == null) return;
        PendingCraft request = pending;
        try {
            request.launch.tick(Minecraft.getSystemTime());
        } catch (RuntimeException | LinkageError error) {
            fail(error);
        } finally {
            if (request.launch.done() && pending == request) pending = null;
        }
    }

    private final class PendingCraft implements DeferredCraftingLaunch.Context {
        final Minecraft mc = Minecraft.getMinecraft();
        final GuiCraftingTree tree;
        final GuiContainer target;
        final Container inventory;
        final int windowId;
        final EntityClientPlayerMP player = mc.thePlayer;
        final WorldClient world = mc.theWorld;
        final RecipeChainMath plan;
        final BookmarkGrid bookmarks;
        final int selectedGroup;
        final BookmarkGroup bookmarkGroup;
        final DeferredCraftingLaunch launch;
        ContainerSyncBarrier sync;

        PendingCraft(GuiCraftingTree tree, GuiContainer target, RecipeChainMath plan,
                BookmarkGrid bookmarks, int selectedGroup) {
            this.tree = tree;
            this.target = target;
            this.inventory = target.inventorySlots;
            this.windowId = inventory.windowId;
            this.plan = plan;
            this.bookmarks = bookmarks;
            this.selectedGroup = selectedGroup;
            this.bookmarkGroup = bookmarks.getGroup(selectedGroup);
            this.launch = new DeferredCraftingLaunch(this, 10000);
        }

        private boolean valid() {
            return !failed && mc.thePlayer == player && mc.theWorld == world && !player.isDead
                && Display.isActive() && target.inventorySlots == inventory && inventory.windowId == windowId;
        }

        @Override public boolean sourceCurrent() { return valid() && mc.currentScreen == tree; }

        @Override public boolean targetCurrent() {
            return valid() && mc.currentScreen == target && player.openContainer == inventory;
        }

        @Override public boolean inputReleased() {
            return mouseReleased();
        }

        @Override public void restoreAndRequestSync() {
            mc.displayGuiScreen(target);
            // A GuiOpenEvent listener may veto or replace the screen.
            if (!targetCurrent()) return;
            sync = new ContainerSyncBarrier(player.sendQueue.getNetworkManager().channel(),
                windowId, inventory.inventorySlots.size());
            NEICPH.sendRequestContainer();
        }

        @Override public boolean syncApplied() { return sync != null && sync.applied(); }

        @Override public void closeSync() {
            if (sync != null) sync.close();
        }

        @Override public boolean startCrafting() {
            if (player.inventory.getItemStack() != null) {
                message("heldItem");
                return false;
            }
            if (AutoCraftingManager.processing()) {
                message("busy");
                return false;
            }
            try {
                // Recheck after the server's snapshot arrives; a stale UI preview must not
                // launch an empty task or delete its bookmarks.
                if (bookmarks.getGroup(selectedGroup) != bookmarkGroup
                        || !NeiCraftingAvailability.canStart(bookmarks, selectedGroup, target)) {
                    message("notCraftable");
                    return false;
                }
                NeiCraftingAvailability.prepareBatch(plan, AutoCraftingManager.getInventoryItems(target));
                AutoCraftingManager.runProcessing(plan);
                return true;
            } catch (RuntimeException | LinkageError error) {
                fail(error);
                return false;
            }
        }

        @Override public void syncTimedOut() { message("syncTimeout"); }
    }

    private void cancelPending() {
        if (pending != null) {
            pending.launch.cancel();
            pending = null;
        }
    }

    private void fail(Throwable error) {
        quantityButtons.cancel();
        cancelPending();
        if (!failed) {
            failed = true;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Disabling incompatible NEI crafting tree controls.");
            message("unavailable");
        }
    }

    private static void message(String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
            new ChatComponentTranslation("easytouch.craftTree." + reason));
    }

    @Override public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) { return false; }
    @Override public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        // Manual input can cancel a pending button action, but never triggers crafting.
        if (pending != null && gui == pending.target) cancelPending();
    }
    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        if (failed) return false;
        try {
            return NeiFavoriteTreeShortcut.open(gui, keyCode);
        } catch (RuntimeException | LinkageError error) {
            fail(error);
            return true;
        }
    }
    @Override public boolean mouseClicked(GuiContainer gui, int x, int y, int button) {
        return quantityButtons.click(gui, x, y, button);
    }
    @Override public void onMouseClicked(GuiContainer gui, int x, int y, int button) {
        // A manual inventory action supersedes a launch that is still waiting for synchronization.
        if (pending != null && gui == pending.target) cancelPending();
    }
    @Override public void onMouseUp(GuiContainer gui, int x, int y, int button) {
        quantityButtons.release(gui, x, y, button);
    }
    @Override
    public boolean mouseScrolled(GuiContainer gui, int x, int y, int scroll) {
        if (scroll == 0 || !isTree(gui)) return false;
        if (quantityButtons.contains(gui, x, y)) return true;
        // Ctrl retains the native path; Shift retains ingredient selection/horizontal scrolling.
        checkedTree = null;
        if (NEIClientUtils.controlKey() || NEIClientUtils.shiftKey()) return false;
        try {
            GuiCraftingTree tree = (GuiCraftingTree) gui;
            BookmarkItem item = quantityScroll.outputUnderMouse(tree);
            if (item == null) return false;
            BookmarkGrid bookmarks = (BookmarkGrid) grid.get(tree);
            int selectedGroup = groupId.getInt(tree);
            if (bookmarks == null || bookmarks.getGroup(selectedGroup) == null) return false;
            // A quantity edit supersedes a button request holding an older recipe snapshot.
            cancelPending();
            quantityScroll.changeAmount(tree, bookmarks, selectedGroup, item, scroll);
            return true; // Consume the wheel so NEI cannot also scroll/zoom the canvas.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
            return true;
        }
    }
    @Override public void onMouseScrolled(GuiContainer gui, int x, int y, int scroll) { }
    @Override public void onMouseDragged(GuiContainer gui, int x, int y, int button, long heldTime) { }
}
