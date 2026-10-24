package io.github.wdy.easytouch.nei;

import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.tree.CraftingTreeGraph;
import codechicken.nei.bookmark.tree.GuiCraftingTree;

/** Uses RC2's native hit testing and quantity rules without changing global keyboard state. */
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

    void changeAmount(GuiCraftingTree tree, BookmarkGrid bookmarks, int groupId, BookmarkItem item, int wheel)
            throws ReflectiveOperationException {
        long step = NEIClientUtils.altKey() ? NEIClientUtils.getScrollMultiplier(item.itemStack) : 1;
        bookmarks.shiftRecipeAmount(groupId, item.recipeId, wheel > 0 ? step : -step);
        // Match native Ctrl+wheel: refresh both the graph and material statistics.
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
