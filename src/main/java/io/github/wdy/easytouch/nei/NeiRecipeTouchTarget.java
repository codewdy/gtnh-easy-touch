package io.github.wdy.easytouch.nei;

import java.lang.reflect.Field;
import codechicken.nei.Widget;
import codechicken.nei.WidgetContainer;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.widget.RecipeWidget;
import net.minecraft.client.gui.inventory.GuiContainer;

/** Resolves the actual displayed recipe, including scrolling and grouped recipe variants. */
final class NeiRecipeTouchTarget {
    private final Field container;

    NeiRecipeTouchTarget() throws ReflectiveOperationException {
        // RC2 exposes the widgets, but not the recipe screen's scroll container.
        container = GuiRecipe.class.getDeclaredField("container");
        container.setAccessible(true);
    }

    RecipeWidget widgetAt(GuiContainer gui, int x, int y) throws IllegalAccessException {
        if (!(gui instanceof GuiRecipe)) return null;
        Widget widget = ((WidgetContainer) container.get(gui)).getWidgetUnderMouse(x, y);
        return widget instanceof RecipeWidget ? (RecipeWidget) widget : null;
    }

    static final class Selection {
        final RecipeWidget widget;
        final RecipeHandlerRef recipe;
        final boolean output;

        Selection(RecipeWidget widget, int x, int y) {
            this.widget = widget;
            recipe = widget.getRecipeHandlerRef();
            output = widget.isFocusedRecipe(x, y);

        }

        boolean matches(RecipeWidget current, int x, int y) {
            if (current != widget) return false;
            RecipeHandlerRef ref = current.getRecipeHandlerRef();
            return ref.handler == recipe.handler && ref.recipeIndex == recipe.recipeIndex
                && current.isFocusedRecipe(x, y) == output;
        }

    }
}
