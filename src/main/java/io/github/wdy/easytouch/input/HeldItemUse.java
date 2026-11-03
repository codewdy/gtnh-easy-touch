package io.github.wdy.easytouch.input;

import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

/** Classifies sustained right-click items without a hard dependency on Tinkers Construct. */
public final class HeldItemUse {
    private HeldItemUse() { }

    public static boolean matches(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemFood || stack.getItem() instanceof ItemBow) return true;
        EnumAction action = stack.getItemUseAction();
        if (action == EnumAction.eat || action == EnumAction.bow) return true;
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            switch (type.getName()) {
                case "tconstruct.items.tools.ShortBow":
                case "tconstruct.items.tools.LongBow":
                case "tconstruct.items.tools.Crossbow": return true;
                default: break;
            }
        }
        return false;
    }
}
