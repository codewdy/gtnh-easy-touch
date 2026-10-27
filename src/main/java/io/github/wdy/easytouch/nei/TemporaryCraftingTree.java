package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.recipe.GuiFavoriteButton;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** A native tree backed by private, unsaved data rather than the bookmark panel. */
final class TemporaryCraftingTree extends GuiCraftingTree {
    private TemporaryCraftingTree(BookmarkGrid grid, int groupId) {
        super(Minecraft.getMinecraft().currentScreen, grid, groupId);
    }

    @SuppressWarnings("unchecked")
    static boolean open(GuiFavoriteButton button) {
        final Recipe root = button.getRecipe();
        if (root == null) return false;
        final List<Recipe> recipes;
        try {
            // Reuse RC2's depth, favorite and permutation rules without its bookmark save.
            Method expand = GuiFavoriteButton.class.getDeclaredMethod("getRecipesTree", Recipe.class);
            expand.setAccessible(true);
            recipes = (List<Recipe>) expand.invoke(button, root);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot expand NEI favorites for a temporary tree", error);
        }
        Grid grid = new Grid();
        BookmarkGroup group = new BookmarkGroup(BookmarkViewMode.TODO_LIST, true);
        group.collapsedRecipes.add(root.getRecipeId());
        int groupId = grid.addGroup(group);
        Set<RecipeId> added = new HashSet<>();
        for (Recipe recipe : recipes) {
            if (added.add(recipe.getRecipeId())) grid.addRecipe(recipe, 1, groupId);
        }
        grid.loading = false;
        grid.onItemsChanged();
        Minecraft.getMinecraft().displayGuiScreen(new TemporaryCraftingTree(grid, groupId));
        return true;
    }

    @Override
    public void initGui() {
        super.initGui();
        // RC2's share button reads the global bookmark panel, not this tree's grid.
        buttonList.removeIf(button -> ((GuiButton) button).id == 5);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id != 5) super.actionPerformed(button);
    }

    static final class Grid extends BookmarkGrid {
        private boolean loading = true;

        @Override
        public void onItemsChanged() {
            super.onItemsChanged();
            if (loading) return;
            // Normal grids refresh these details when their bookmark panel is drawn.
            // This grid is never drawn there, so keep quantity/collapse calculations current here.
            for (Integer id : groups.keySet()) {
                BookmarkGroup group = getGroup(id);
                if (group.crafting != null) group.crafting.refresh(createChainItems(id), group.collapsedRecipes);
            }
        }
    }
}
