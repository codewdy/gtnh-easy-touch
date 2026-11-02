package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipe;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

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
                && slot.getHasStack() && slot.getStack().stackSize > 1 && slot.canTakeStack(mc.thePlayer)
                && slot.isItemValid(slot.getStack()) && slot.getStack().stackSize <= slot.getStack().getMaxStackSize()
                && slot.getStack().stackSize <= slot.getSlotStackLimit()
                && !slot.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).matches(".*(fake|ghost|dummy|craft|output).*");
        }

        void split(GuiContainer gui, int quantity) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != gui || !matches(gui, slot) || !canSplit()) return;
            ItemStack original = slot.getStack().copy();
            if (quantity < 1 || quantity > original.stackSize) return;
            GuiContainerManager manager = GuiContainerManager.getManager(gui);
            if (manager == null) return;
            manager.handleMouseClick(slot, slot.slotNumber, 0, 0);
            for (int remaining = original.stackSize; remaining > quantity; remaining--) {
                ItemStack hand = mc.thePlayer.inventory.getItemStack();
                ItemStack expected = original.copy();
                expected.stackSize = remaining;
                ItemStack returned = slot.getStack();
                int deposited = original.stackSize - remaining;
                ItemStack expectedSlot = original.copy();
                expectedSlot.stackSize = deposited;
                // Stop immediately when a custom click handler does not follow ordinary slot semantics.
                if (mc.currentScreen != gui || !matches(gui, slot)
                    || !ItemStack.areItemStacksEqual(hand, expected)
                    || !ItemStack.areItemStacksEqual(returned, deposited == 0 ? null : expectedSlot)
                    || !slot.isItemValid(original)) return;
                manager.handleMouseClick(slot, slot.slotNumber, 1, 0);
            }
        }
    }
}
