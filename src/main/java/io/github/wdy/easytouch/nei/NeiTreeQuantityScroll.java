package io.github.wdy.easytouch.nei;

import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.tree.CraftingTreeGraph;
import codechicken.nei.bookmark.tree.GuiCraftingTree;

/** Uses RC2's native hit testing with item-count targets without changing global keyboard state. */
final class NeiTreeQuantityScroll {
    private final Field canvas;
    private final Field graph;
    private final Field ingredient;
    private final Method mousePosition;
    private final Method isInWidget;
    private final Method isDragging;
    private final Method nodeAt;
    private final Method rebuild;

    NeiTreeQuantityScroll() throws ReflectiveOperationException {
        // The canvas and node classes are package-private in NEI 2.8.155.
        canvas = field(GuiCraftingTree.class, "canvas");
        Class<?> canvasType = canvas.getType();
        graph = field(canvasType, "graph");
        mousePosition = method(GuiCraftingTree.class, "getMousePosition");
        isInWidget = method(canvasType, "isInWidget", int.class, int.class);
        isDragging = method(canvasType, "isDragging");
        nodeAt = method(canvasType, "getNodeMouseOver", Point2D.class);
        ingredient = field(nodeAt.getReturnType(), "ingrItem");
        rebuild = method(GuiCraftingTree.class, "rebuildTree", nodeAt.getReturnType());
    }

    BookmarkItem outputUnderMouse(GuiCraftingTree tree) throws ReflectiveOperationException {
        Object view = canvas.get(tree);
        if (view == null || (Boolean) isDragging.invoke(view)) return null;
        // Use NEI's precise screen coordinates, including its GUI scale and canvas zoom/pan.
        // The input handler's integer coordinates and last frame's hovered node may differ.
        Point2D mouse = (Point2D) mousePosition.invoke(tree);
        if (!(Boolean) isInWidget.invoke(view, (int) mouse.getX(), (int) mouse.getY())) return null;
        Object node = nodeAt.invoke(view, mouse);
        if (node == null || !((CraftingTreeGraph) graph.get(view)).roots.contains(node)) return null;
        BookmarkItem item = (BookmarkItem) ingredient.get(node);
        return item != null && item.recipeId != null ? item : null;
    }

    List<BookmarkItem> outputs(GuiCraftingTree tree) throws ReflectiveOperationException {
        List<BookmarkItem> outputs = new ArrayList<>();
        Object view = canvas.get(tree);
        if (view == null) return outputs;
        for (Object node : ((CraftingTreeGraph) graph.get(view)).roots) {
            BookmarkItem item = (BookmarkItem) ingredient.get(node);
            if (item != null && item.recipeId != null) outputs.add(item);
        }
        return outputs;
    }

    void changeAmount(GuiCraftingTree tree, BookmarkGrid bookmarks, int groupId, BookmarkItem item, int wheel)
            throws ReflectiveOperationException {
        long step = NEIClientUtils.altKey() ? NEIClientUtils.getScrollMultiplier(item.itemStack) : 1;
        changeFixedSteps(tree, bookmarks, groupId, item, (int) (wheel > 0 ? step : -step));
    }

    void changeFixedSteps(GuiCraftingTree tree, BookmarkGrid bookmarks, int groupId, BookmarkItem item, int steps)
            throws ReflectiveOperationException {
        Long exact = ExactCraftingTargets.get(bookmarks, item);
        long current = exact == null ? item.getStackSize(item.getAmount()) : exact;
        // Graph nodes may include inherited demand; read the root's native multiplier instead.
        if (exact == null) {
            Long batches = bookmarks.createRecipeChainMath(groupId).outputRecipes.get(item.recipeId);
            if (batches != null) current = item.getStackSize(item.getAmount(batches));
        }
        long amount = ExactCraftingTargets.adjusted(current, steps);
        ExactCraftingTargets.set(bookmarks, item, amount);
        long batches = InitialCraftingQuantity.above(amount - 1,
            count -> item.getStackSize(item.getAmount(count)));
        for (int index : bookmarks.createChainItems(groupId).keySet()) {
            BookmarkItem stored = bookmarks.getBookmarkItem(index);
            if (stored.equalsRecipe(item.recipeId, groupId) && !stored.emptyFactor()) {
                stored.multiplier = batches;
            }
        }
        bookmarks.onItemsChanged();
        rebuild.invoke(tree, new Object[] {null});
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method result = owner.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }
}
