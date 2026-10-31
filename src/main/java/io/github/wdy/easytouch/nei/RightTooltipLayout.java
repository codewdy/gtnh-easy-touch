package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import java.util.Map;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanel;
import codechicken.nei.Widget;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid;
import codechicken.nei.LayoutManager;
import codechicken.nei.LayoutStyleMinecraft;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.LayoutStyle;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.apache.logging.log4j.Level;

/** Reserve the right column lower half without changing NEI settings or item data. */
final class RightTooltipLayout extends LayoutStyleMinecraft {
    private final LayoutStyleMinecraft original;
    private static Method header, footer;
    private static boolean failed;
    private static GuiContainer owner;
    private static Rectangle4i tooltipArea;

    private RightTooltipLayout(LayoutStyleMinecraft original) {
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
                if (current instanceof RightTooltipLayout)
                    entry.setValue(((RightTooltipLayout) current).original);
            } else if (current != null && current.getClass() == LayoutStyleMinecraft.class) {
                entry.setValue(new RightTooltipLayout((LayoutStyleMinecraft) current));
            }
        }
    }

    static Rectangle4i tooltipArea(GuiContainer gui) { return owner == gui ? tooltipArea : null; }

    @Override public void layout(GuiContainer gui, VisiblityData visibility) {
        super.layout(gui, visibility);
        owner = null; tooltipArea = null;
        if (failed || !DockedTooltips.enabled() || !visibility.showNEI || !visibility.showItemPanel) return;
        ItemPanel panel = ItemPanels.itemPanel;
        if (panel == null || panel.getGrid() == null) return;
        // calculateBounds is unchanged; never halve an already reduced panel.h.
        Rectangle4i full = panel.calculateBounds(gui);
        int lowerHeight = full.h / 2;
        int upperHeight = full.h - lowerHeight;
        if (full.w < 16 || lowerHeight < 16) return;
        try {
            if (header == null) {
                header = ItemPanel.class.getDeclaredMethod("resizeHeader", GuiContainer.class);
                footer = ItemPanel.class.getDeclaredMethod("resizeFooter", GuiContainer.class);
                header.setAccessible(true); footer.setAccessible(true);
            }
            panel.x = full.x; panel.y = full.y; panel.w = full.w; panel.h = upperHeight;
            int headerHeight = (Integer) header.invoke(panel, gui);
            moveFooter(LayoutManager.quantity, full, lowerHeight);
            moveFooter(LayoutManager.searchField, full, lowerHeight);
            int footerHeight = (Integer) footer.invoke(panel, gui);
            int gridHeight = panel.h - headerHeight - footerHeight;
            // On exceptionally small GUIs keep a usable native item row.
            if (gridHeight < ItemsGrid.SLOT_SIZE) { super.layout(gui, visibility); return; }
            panel.getGrid().setGridSize(panel.x, panel.y + headerHeight, panel.w, gridHeight);
            panel.getGrid().refresh(gui);
            panel.historyPanel.resize(gui);
            panel.craftablesPanel.resize(gui);
            owner = gui;
            tooltipArea = new Rectangle4i(full.x, full.y + upperHeight, full.w, lowerHeight);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            super.layout(gui, visibility);
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Right column split layout unavailable; restored native layout.");
        }
    }

    private static void moveFooter(Widget widget, Rectangle4i full, int shift) {
        if (widget != null && widget.x >= full.x && widget.y >= full.y + full.h / 2)
            widget.y -= shift;
    }
}
