package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import java.util.Map;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.BookmarkPanel;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid;
import codechicken.nei.LayoutManager;
import codechicken.nei.LayoutStyleMinecraft;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.LayoutStyle;
import cpw.mods.fml.common.FMLLog;
import io.github.wdy.easytouch.layout.GlobalTouchMenu;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.apache.logging.log4j.Level;

/** Reserve the left bookmark column lower half without changing NEI settings or item data. */
final class LeftTooltipLayout extends LayoutStyleMinecraft {
    private final LayoutStyleMinecraft original;
    private static Method header, footer;
    private static boolean failed;
    private static GuiContainer owner;
    private static Rectangle4i tooltipArea;

    private LeftTooltipLayout(LayoutStyleMinecraft original) {
        this.original = original;
        buttonCount = original.buttonCount; leftSize = original.leftSize; numButtons = original.numButtons;
    }

    static void prepare() {
        owner = null; tooltipArea = null;
        if (LayoutManager.layoutStyles == null) return;
        for (Map.Entry<Integer, LayoutStyle> entry : LayoutManager.layoutStyles.entrySet()) {
            // BookmarkPanel casts the active style to LayoutStyleMinecraft internally.
            // Preserve that type and leave third-party layout subclasses untouched.
            LayoutStyle current = entry.getValue();
            if (failed || !DockedTooltips.enabled()) {
                if (current instanceof LeftTooltipLayout)
                    entry.setValue(((LeftTooltipLayout) current).original);
            } else if (current != null && current.getClass() == LayoutStyleMinecraft.class) {
                entry.setValue(new LeftTooltipLayout((LayoutStyleMinecraft) current));
            }
        }
    }

    static Rectangle4i tooltipArea(GuiContainer gui) { return owner == gui ? tooltipArea : null; }

    @Override public void layout(GuiContainer gui, VisiblityData visibility) {
        // Native layout briefly expands the grid and clamps its page to the smaller
        // full-height page count. Preserve the requested page across that intermediate layout.
        BookmarkPanel previousPanel = ItemPanels.bookmarkPanel;
        int requestedPage = previousPanel != null && previousPanel.getGrid() != null
            ? previousPanel.getGrid().getPage() - 1 : 0;
        super.layout(gui, visibility);
        owner = null; tooltipArea = null;
        if (failed || !DockedTooltips.enabled() || !visibility.showNEI || !visibility.showBookmarkPanel) return;
        BookmarkPanel panel = ItemPanels.bookmarkPanel;
        if (panel == null || panel.getGrid() == null) return;
        // calculateBounds is unchanged; never halve an already reduced panel.h.
        Rectangle4i full = panel.calculateBounds(gui);
        int lowerHeight = full.h / 2;
        int upperHeight = full.h - lowerHeight;
        // Keep the tooltip viewport above the bottom settings button, including a small gap.
        int tooltipTop = full.y + upperHeight;
        int tooltipBottom = Math.min(full.y + full.h,
            gui.height - GlobalTouchMenu.MARGIN - GlobalTouchMenu.SIZE - 4);
        int tooltipHeight = tooltipBottom - tooltipTop;
        if (full.w < 16 || tooltipHeight < 16) return;
        try {
            if (header == null) {
                header = BookmarkPanel.class.getDeclaredMethod("resizeHeader", GuiContainer.class);
                footer = BookmarkPanel.class.getDeclaredMethod("resizeFooter", GuiContainer.class);
                header.setAccessible(true); footer.setAccessible(true);
            }
            panel.x = full.x; panel.y = full.y; panel.w = full.w; panel.h = upperHeight;
            int headerHeight = (Integer) header.invoke(panel, gui);
            int footerHeight = (Integer) footer.invoke(panel, gui);
            int gridHeight = panel.h - headerHeight - footerHeight;
            // On exceptionally small GUIs keep a usable native item row.
            if (gridHeight < ItemsGrid.SLOT_SIZE) { super.layout(gui, visibility); return; }
            panel.getGrid().setGridSize(panel.x, panel.y + headerHeight, panel.w, gridHeight);
            panel.getGrid().refresh(gui);
            panel.getGrid().setPage(requestedPage);
            // Header text must use the final capacity and restored (clamped) page.
            header.invoke(panel, gui);
            owner = gui;
            tooltipArea = new Rectangle4i(full.x, tooltipTop, full.w, tooltipHeight);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            super.layout(gui, visibility);
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Left bookmark column split layout unavailable; restored native layout.");
        }
    }

}
