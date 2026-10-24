package io.github.wdy.easytouch.nei;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.Map;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemPanels;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.Widget;
import codechicken.nei.WidgetContainer;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiFavoriteButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.widget.RecipeWidget;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;

/** Configurable alias for all RC2 tree-opening contexts, without changing held keyboard state. */
final class NeiTreeShortcut {
    private final KeyBinding binding = new KeyBinding("key.easytouch.openTree", Keyboard.KEY_NONE,
        "key.categories.easytouch");
    private final Field recipeContainer;

    NeiTreeShortcut() throws ReflectiveOperationException {
        recipeContainer = GuiRecipe.class.getDeclaredField("container");
        recipeContainer.setAccessible(true);
    }

    void registerKey() {
        ClientRegistry.registerKeyBinding(binding);
    }

    void addHotkey(Map<String, String> hotkeys) {
        if (binding.getKeyCode() <= Keyboard.KEY_NONE) return;
        String action = hotkeys.get(KeyManager.getKeyName(NeiFavoriteTreeShortcut.KEY, NEIClientUtils.SHIFT_HASH));
        if (action == null) action = hotkeys.get(KeyManager.getKeyName(NeiFavoriteTreeShortcut.KEY));
        if (action != null) hotkeys.put(GameSettings.getKeyDisplayString(binding.getKeyCode()), action);
    }

    boolean keyTyped(GuiContainer gui, int keyCode) throws ReflectiveOperationException {
        if (keyCode <= Keyboard.KEY_NONE || keyCode != binding.getKeyCode()) return false;
        if (!NeiFavoriteTreeShortcut.canUse(gui)) return false;
        // Consume repeats even when no action is available; one press must create at most one group.
        if (!Keyboard.isRepeatEvent()) open(gui);
        return true;
    }

    private boolean open(GuiContainer gui) throws ReflectiveOperationException {
        if (gui instanceof GuiRecipe) {
            Point mouse = GuiDraw.getMousePosition();
            WidgetContainer container = (WidgetContainer) recipeContainer.get(gui);
            Widget widget = container.getWidgetUnderMouse(mouse.x, mouse.y);
            if (widget instanceof RecipeWidget) {
                // RecipeWidget stores button coordinates relative to itself, already accounting
                // for recipe scrolling. Use the displayed button, including its active recipe variant.
                for (GuiRecipeButton button : ((RecipeWidget) widget).getRecipeButtons()) {
                    int x = widget.x + button.xPosition;
                    int y = widget.y + button.yPosition;
                    if (button instanceof GuiFavoriteButton && button.visible && button.enabled
                            && mouse.x >= x && mouse.x < x + button.width
                            && mouse.y >= y && mouse.y < y + button.height) {
                        return NeiFavoriteTreeShortcut.openRecipe((GuiFavoriteButton) button);
                    }
                }
            }
        }

        // Native Shift+T over a group header opens that existing group, without adding a new one.
        if (GuiContainerManager.getStackMouseOver(gui) == null) {
            int groupId = ItemPanels.bookmarkPanel.getHoveredGroupId(true);
            BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();
            if (groupId != -1 && grid.isCraftingMode(groupId)) {
                GuiCraftingTree.openCraftingTreeGui(grid, groupId);
                return true;
            }
            return false;
        }

        return NeiFavoriteTreeShortcut.openFavorite(gui);
    }
}
