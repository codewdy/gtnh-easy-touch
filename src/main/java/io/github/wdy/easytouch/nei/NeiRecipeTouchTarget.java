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

    net.minecraft.item.ItemStack tinkerFluid(RecipeWidget widget, int x, int y)
            throws ReflectiveOperationException {
        RecipeHandlerRef ref = widget.getRecipeHandlerRef();
        Class<?> base = ref.handler.getClass();
        while (base != null && !base.getName().equals("tconstruct.plugins.nei.RecipeHandlerBase"))
            base = base.getSuperclass();
        if (base == null) return null;
        Object cached = ((codechicken.nei.recipe.TemplateRecipeHandler) ref.handler).arecipes.get(ref.recipeIndex);
        Class<?> cachedBase = Class.forName("tconstruct.plugins.nei.RecipeHandlerBase$CachedBaseRecipe");
        java.util.List<?> tanks = (java.util.List<?>) cachedBase.getMethod("getFluidTanks").invoke(cached);
        if (tanks == null) return null;
        Field infoField = RecipeWidget.class.getDeclaredField("handlerInfo");
        infoField.setAccessible(true);
        int shift = ((codechicken.nei.recipe.HandlerInfo) infoField.get(widget)).getYShift();
        for (Object tank : tanks) {
            java.awt.Rectangle bounds = (java.awt.Rectangle) tank.getClass().getField("position").get(tank);
            if (!bounds.contains(x - widget.x, y - widget.y - shift)) continue;
            net.minecraftforge.fluids.FluidStack fluid =
                (net.minecraftforge.fluids.FluidStack) tank.getClass().getField("fluid").get(tank);
            if (fluid != null && fluid.amount > 0)
                return codechicken.nei.item.ItemFluidDisplay.createStack(fluid);
        }
        return null;
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
