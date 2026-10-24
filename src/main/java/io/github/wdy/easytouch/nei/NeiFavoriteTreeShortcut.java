package io.github.wdy.easytouch.nei;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.ItemPanels;
import codechicken.nei.KeyManager;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiFavoriteButton;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/** Extends NEI's existing tree key to items, using the same favorite recipe expansion. */
final class NeiFavoriteTreeShortcut {
    static final String KEY = "bookmark.open_crafting_tree";

    private NeiFavoriteTreeShortcut() { }

    static RecipeId favoriteUnderMouse(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        // Native recipe buttons can already have opened a tree before the last-key callbacks.
        // Never act on their old screen, or while a NEI text widget owns the keyboard.
        if (gui == null || mc.currentScreen != gui || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || !Display.isActive() || !NEIClientConfig.isLoaded()
                || LayoutManager.getInputFocused() != null || ItemPanels.bookmarkPanel == null
                || ItemPanels.bookmarkPanel.inEditingState()) return null;
        ItemStack hovered = GuiContainerManager.getStackMouseOver(gui);
        if (hovered == null) return null;
        // NEI handles metadata/NBT/fluids and prefers manual favorites to generated favorites.
        RecipeId recipe = FavoriteRecipes.getFavorite(hovered);
        // A favorite item alone is a stop-expanding marker, not a saved crafting recipe.
        return recipe != null && !FavoriteRecipes.FAVORITE_ITEM.equals(recipe.getHandlerName()) ? recipe : null;
    }

    static boolean open(GuiContainer gui, int keyCode) {
        // Follow the user's NEI binding, and react only to that key's own non-repeat event.
        if (keyCode <= Keyboard.KEY_NONE || keyCode != KeyManager.getKeyCode(KEY)
                || Keyboard.isRepeatEvent() || !KeyManager.isHashDown(KEY, NEIClientUtils.SHIFT_HASH)) return false;
        RecipeId favorite = favoriteUnderMouse(gui);
        if (favorite == null) return false;
        RecipeHandlerRef recipe = RecipeHandlerRef.of(favorite);
        if (recipe == null) return false;

        // Reuse the actual native Shift+T implementation's save operation. It recursively
        // expands favorites, honors NEI's depth/permutation choices, and adds a TODO crafting group.
        GuiFavoriteButton favoriteButton = new GuiFavoriteButton(recipe, 0, 0);
        if (!favoriteButton.saveRecipeInBookmark()) return false;
        BookmarkGrid bookmarks = ItemPanels.bookmarkPanel.getGrid();
        GuiCraftingTree.openCraftingTreeGui(bookmarks, bookmarks.getNextGroupId() - 1);
        return true;
    }
}
