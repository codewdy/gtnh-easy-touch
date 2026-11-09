package io.github.wdy.easytouch.nei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import codechicken.nei.guihook.GuiContainerManager;

/** EZ Storage renders filtered virtual items instead of server inventory slots. */
final class StorageItemTarget {
    ItemStack at(GuiContainer gui, int x, int y) throws ReflectiveOperationException {
        Class<?> type = gui.getClass();
        while (type != null && !type.getName().equals("com.zerofall.ezstorage.gui.GuiStorageCore")) {
            type = type.getSuperclass();
        }
        if (type == null) return null;
        GuiContainerManager manager = GuiContainerManager.getManager(gui);
        if (manager == null || manager.objectUnderMouse(x, y)) return null;
        Method hit = type.getDeclaredMethod("getSlotAt", int.class, int.class);
        hit.setAccessible(true);
        Integer index = (Integer) hit.invoke(gui, x, y);
        if (index == null || index < 0) return null;
        Field field = type.getDeclaredField("filteredList");
        field.setAccessible(true);
        List<?> items = (List<?>) field.get(gui);
        if (index >= items.size()) return null;
        ItemStack item = (ItemStack) items.get(index);
        return item == null || item.stackSize <= 0 ? null : item;
    }
}
