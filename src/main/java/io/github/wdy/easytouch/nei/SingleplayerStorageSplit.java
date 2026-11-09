package io.github.wdy.easytouch.nei;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;

/** Local requests only: inventory mutation happens exclusively on the integrated server tick. */
public final class SingleplayerStorageSplit {
    private static final AtomicReference<Request> pending = new AtomicReference<>();

    public static void install() {
        FMLCommonHandler.instance().bus().register(new SingleplayerStorageSplit());
    }

    static boolean available() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.isSingleplayer() && mc.getIntegratedServer() != null && mc.thePlayer != null;
    }

    static void request(GuiContainer gui, ItemStack item, int quantity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!available() || mc.currentScreen != gui || mc.thePlayer.openContainer != gui.inventorySlots
            || mc.thePlayer.inventory.getItemStack() != null || item == null
            || quantity < 1 || quantity > Math.min(item.stackSize, item.getMaxStackSize())) return;
        pending.compareAndSet(null, new Request(mc.getIntegratedServer(), mc.thePlayer.getUniqueID(),
            gui.inventorySlots.windowId, item.copy(), quantity));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Request request = pending.getAndSet(null);
        if (request == null || MinecraftServer.getServer() != request.server || request.server.isDedicatedServer()) return;
        for (Object value : request.server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (!player.getUniqueID().equals(request.player)) continue;
            if (!player.getCommandSenderName().equals(request.server.getServerOwner())) return;
            try { extract(player, request); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Singleplayer storage split failed.");
            }
            return;
        }
    }

    private static void extract(EntityPlayerMP player, Request request) throws ReflectiveOperationException {
        Container container = player.openContainer;
        if (container == null || container.windowId != request.windowId || player.isDead
            || player.inventory.getItemStack() != null || !container.canInteractWith(player)) return;
        Class<?> type = container.getClass();
        while (type != null && !type.getName().equals("com.zerofall.ezstorage.container.ContainerStorageCore")) {
            type = type.getSuperclass();
        }
        if (type == null) return;
        Object inventory = type.getField("inventory").get(container);
        Class<?> inventoryType = inventory.getClass();
        // Resolve identity again on the server: sorting/removal can change numeric indices.
        int index = (Integer) inventoryType.getMethod("getIndexOf", ItemStack.class).invoke(inventory, request.item);
        if (index < 0) return;
        Method simulate = inventoryType.getMethod("simulateRemove", int.class, int.class);
        ItemStack preview = (ItemStack) simulate.invoke(inventory, index, request.quantity);
        if (preview == null || preview.stackSize != request.quantity || !preview.isItemEqual(request.item)
            || !ItemStack.areItemStackTagsEqual(preview, request.item)) return;
        // Resolve synchronization before mutation so an incompatible API cannot strand the result.
        Method sync = Class.forName("com.zerofall.ezstorage.util.EZInventoryManager")
            .getMethod("sendToClients", inventoryType);
        ItemStack extracted = (ItemStack) inventoryType.getMethod("getItemStackAt", int.class, int.class)
            .invoke(inventory, index, request.quantity);
        player.inventory.setItemStack(extracted);
        player.sendContainerAndContentsToPlayer(container, container.getInventory());
        sync.invoke(null, inventory);
    }

    private static final class Request {
        final MinecraftServer server;
        final UUID player;
        final int windowId, quantity;
        final ItemStack item;
        Request(MinecraftServer server, UUID player, int windowId, ItemStack item, int quantity) {
            this.server = server; this.player = player; this.windowId = windowId;
            this.item = item; this.quantity = quantity;
        }
    }
}
