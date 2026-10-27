package io.github.wdy.easytouch.nei;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;

/** Session-local item targets, separate from NEI's integer recipe multipliers. */
final class ExactCraftingTargets {
    private static final Map<BookmarkGrid, Map<BookmarkItem, Long>> targets = new WeakHashMap<>();

    private ExactCraftingTargets() { }

    static Long get(BookmarkGrid grid, BookmarkItem item) {
        Map<BookmarkItem, Long> values = targets.get(grid);
        return values == null ? null : values.get(item);
    }

    static void set(BookmarkGrid grid, BookmarkItem item, long amount) {
        targets.computeIfAbsent(grid, key -> new HashMap<>()).put(item.copy(), amount);
    }

    static long adjusted(long current, long delta) {
        return Math.max(1, Math.min(Integer.MAX_VALUE, current + delta));
    }
}
