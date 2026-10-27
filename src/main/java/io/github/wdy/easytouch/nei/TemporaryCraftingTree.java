package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiFavoriteButton;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

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
        TemporaryCraftingTree tree = new TemporaryCraftingTree(grid, groupId);
        grid.initializeQuantity(groupId, root, tree.getFirstScreen());
        grid.loading = false;
        grid.onItemsChanged();
        Minecraft.getMinecraft().displayGuiScreen(tree);
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

        /** Set the initial target once; later quantity edits and execution keep NEI's normal rules. */
        private void initializeQuantity(int groupId, Recipe root, GuiContainer source) {
            ItemStack output = root.getResult();
            if (output == null) return;
            BookmarkItem result = null;
            for (BookmarkItem item : bookmarkItems) {
                if (item.equalsRecipe(root.getRecipeId(), groupId) && item.type == BookmarkItemType.RESULT
                        && !item.emptyFactor()
                        && NEIClientUtils.areStacksSameTypeCraftingWithNBT(item.itemStack, output)) {
                    result = item;
                    break;
                }
            }
            if (result == null) return;
            ItemStackAmount inventory = source == null || source.inventorySlots == null
                ? new ItemStackAmount() : AutoCraftingManager.getInventoryItems(source);
            long owned = 0;
            for (ItemStack stack : inventory.values()) {
                if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, result.itemStack)) {
                    owned += StackInfo.getAmount(stack);
                }
            }
            ExactCraftingTargets.set(this, result, ExactCraftingTargets.adjusted(owned, 1));
            final BookmarkItem target = result;
            // Use NEI's own output rounding, including chance and fluid/container units.
            long multiplier = InitialCraftingQuantity.above(owned,
                batches -> target.getStackSize(target.getAmount(batches)));
            for (BookmarkItem item : bookmarkItems) {
                if (item.equalsRecipe(root.getRecipeId(), groupId) && !item.emptyFactor()) {
                    item.multiplier = multiplier;
                }
            }
        }

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
