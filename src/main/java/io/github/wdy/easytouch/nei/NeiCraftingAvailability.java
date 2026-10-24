package io.github.wdy.easytouch.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainIterator;
import codechicken.nei.recipe.chain.RecipeChainMath;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

/** Read-only NEI preflight against the real container, while the virtual tree is displayed. */
public final class NeiCraftingAvailability {
    private NeiCraftingAvailability() { }

    public static boolean canStart(BookmarkGrid grid, int group, GuiContainer container) {
        // Iterator.next() recalculates its math; never probe the plan queued for execution.
        RecipeChainMath probe = grid.createRecipeChainMath(group);
        if (probe == null || probe.outputRecipes.isEmpty()) return false;
        ItemStackAmount inventory = AutoCraftingManager.getInventoryItems(container);
        prepareBatch(probe, inventory);

        List<BookmarkItem> initialItems = new ArrayList<>();
        for (BookmarkItem item : probe.initialItems) {
            long missing = item.getAmount() - inventory.getOrDefault(item.itemStack, 0L) * item.fluidCellAmount;
            if (missing > 0) initialItems.add(item.copyWithAmount(missing));
        }
        RecipeChainIterator steps = new RecipeChainIterator(probe, initialItems);
        steps.updateInventory(inventory);
        while (steps.hasNext()) {
            for (Map.Entry<RecipeId, Long> step : steps.next().entrySet()) {
                if (step.getValue() <= 0) continue;
                RecipeHandlerRef handler = RecipeHandlerRef.of(step.getKey());
                if (handler != null && handler.canCraft(container)) return true;
            }
        }
        return false;
    }

    /** NEI 2.8.155 Shift+C batch rounding, with an explicit inventory instead of currentScreen. */
    public static void prepareBatch(RecipeChainMath plan, ItemStackAmount inventory) {
        RecipeId root = plan.createMasterRoot();
        List<ItemStack> stacks = inventory.values();
        for (int index = 0; index < plan.recipeIngredients.size(); index++) {
            BookmarkItem item = plan.recipeIngredients.get(index);
            long requested = item.getAmount();
            if (!root.equals(item.recipeId) || requested <= 0) continue;
            long owned = 0;
            for (ItemStack stack : stacks) {
                if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, item.itemStack)) {
                    owned += StackInfo.getAmount(stack);
                }
            }
            if (owned >= requested) {
                long target = owned - owned % requested + requested;
                plan.recipeIngredients.set(index, BookmarkItem.builder(item).multiplier(1)
                    .factor(target).chance(PositionedStack.CHANCE_FULL).build());
            }
        }
    }
}
