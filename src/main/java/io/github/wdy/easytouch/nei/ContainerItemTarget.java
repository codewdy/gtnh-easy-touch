package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipe;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

/** Actual inventory slots only; respects NEI overlays and custom container slot-click handlers. */
final class ContainerItemTarget {
    private final Method slotAt = ReflectionHelper.findMethod(GuiContainer.class, null,
        new String[] {"getSlotAtPosition", "func_146975_c"}, int.class, int.class);

    Slot at(GuiContainer gui, int x, int y) throws ReflectiveOperationException {
        if (gui instanceof GuiRecipe || gui.inventorySlots == null) return null;
        GuiContainerManager manager = GuiContainerManager.getManager(gui);
        if (manager == null || manager.objectUnderMouse(x, y)) return null;
        Slot slot = (Slot) slotAt.invoke(gui, x, y);
        // Recipe trees and other synthetic slots must never be treated as server inventory slots.
        return slot != null && gui.inventorySlots.inventorySlots.contains(slot) ? slot : null;
    }

    static final class Selection {
        final Slot slot;
        final Container container;
        final int windowId;

        Selection(GuiContainer gui, Slot slot) {
            this.slot = slot;
            container = gui.inventorySlots;
            windowId = container.windowId;
        }

        boolean matches(GuiContainer gui, Slot current) {
            return current == slot && gui.inventorySlots == container && container.windowId == windowId;
        }

        boolean canSplit() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer != null && mc.thePlayer.inventory.getItemStack() == null
                && slot.getHasStack() && slot.getStack().stackSize > 1 && slot.canTakeStack(mc.thePlayer);
        }

        void split(GuiContainer gui) {
            if (matches(gui, slot) && canSplit()) {
                // Ordinary right-click pickup, without shift or local stack manipulation.
                GuiContainerManager.getManager(gui).handleMouseClick(slot, slot.slotNumber, 1, 0);
            }
        }
    }
}
