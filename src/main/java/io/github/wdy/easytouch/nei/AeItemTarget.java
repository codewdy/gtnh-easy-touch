package io.github.wdy.easytouch.nei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/** Optional AE2 integration. Never treats the ME list as ordinary inventory slots. */
final class AeItemTarget {
    private Class<?> guiType, slotType, itemType, stackType, actionType, packetType;
    private Field slots;
    private Object network, pickup;
    private Method send;

    AeItemTarget() throws ReflectiveOperationException {
        if (!Loader.isModLoaded("appliedenergistics2")) return;
        guiType = Class.forName("appeng.client.gui.implementations.GuiMEMonitorable");
        Class<?> base = Class.forName("appeng.client.gui.AEBaseGui");
        slots = base.getDeclaredField("virtualSlots");
        slots.setAccessible(true);
        slotType = Class.forName("appeng.client.gui.slots.VirtualMEMonitorableSlot");
        itemType = Class.forName("appeng.api.storage.data.IAEItemStack");
        stackType = Class.forName("appeng.api.storage.data.IAEStack");
        actionType = Class.forName("appeng.helpers.MonitorableAction");
        packetType = Class.forName("appeng.core.sync.packets.PacketMonitorableAction");
        pickup = actionType.getField("PICKUP_SINGLE").get(null);
        Class<?> net = Class.forName("appeng.core.sync.network.NetworkHandler");
        network = net.getField("instance").get(null);
        send = net.getMethod("sendToServer", Class.forName("appeng.core.sync.AppEngPacket"));
    }

    Selection at(GuiContainer gui, int x, int y) throws ReflectiveOperationException {
        if (guiType == null || !guiType.isInstance(gui)) return null;
        codechicken.nei.guihook.GuiContainerManager manager = codechicken.nei.guihook.GuiContainerManager.getManager(gui);
        if (manager == null || manager.objectUnderMouse(x, y)) return null;
        int gx = (Integer) gui.getClass().getMethod("getGuiLeft").invoke(gui);
        int gy = (Integer) gui.getClass().getMethod("getGuiTop").invoke(gui);
        for (Object slot : (List<?>) slots.get(gui)) {
            if (!slotType.isInstance(slot)
                || !(Boolean) slot.getClass().getMethod("isHovered", int.class, int.class).invoke(slot, x - gx + 1, y - gy + 1)) continue;
            Object value = slot.getClass().getMethod("getAEStack").invoke(slot);
            if (!itemType.isInstance(value)) return null;
            long count = (Long) stackType.getMethod("getStackSize").invoke(value);
            if (count <= 0) return null;
            Object copy = stackType.getMethod("copy").invoke(value);
            ItemStack item = (ItemStack) itemType.getMethod("getItemStack").invoke(copy);
            return new Selection(gui, copy, item, (int) Math.min(count, item.getMaxStackSize()));
        }
        return null;
    }

    final class Selection {
        final GuiContainer gui;
        final Container container;
        final int windowId, maximum;
        final Object target;
        final ItemStack item;
        Selection(GuiContainer gui, Object target, ItemStack item, int maximum) {
            this.gui = gui; this.container = gui.inventorySlots; this.windowId = container.windowId;
            this.target = target; this.item = item.copy(); this.maximum = maximum;
        }
        boolean current() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.currentScreen == gui && mc.thePlayer != null && gui.inventorySlots == container
                && container.windowId == windowId && mc.thePlayer.openContainer == container;
        }
        void takeOne() throws ReflectiveOperationException {
            container.getClass().getMethod("setTargetStack", stackType).invoke(container, target);
            send.invoke(network, packetType.getConstructor(actionType, int.class).newInstance(pickup, -1));
        }
    }
}
