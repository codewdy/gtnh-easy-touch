package io.github.wdy.easytouch.input;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
/** Read-only classification; no hard dependency on Tinkers Construct. */
public final class SwordAttack {
    private SwordAttack() { }
    public static boolean immediate(Minecraft mc) {
        if (mc.thePlayer==null || mc.objectMouseOver==null
                || mc.objectMouseOver.typeOfHit!=MovingObjectPosition.MovingObjectType.ENTITY
                || mc.objectMouseOver.entityHit==null) return false;
        if (!(mc.objectMouseOver.entityHit instanceof EntityLivingBase)
                && !mc.objectMouseOver.entityHit.canBeCollidedWith()) return false;
        ItemStack stack=mc.thePlayer.getHeldItem();
        return stack!=null && sword(stack.getItem());
    }
    public static boolean sword(Item item) {
        if (item instanceof ItemSword) return true;
        for (Class<?> type=item.getClass(); type!=null; type=type.getSuperclass()) {
            switch (type.getName()) {
                case "tconstruct.items.tools.Broadsword":
                case "tconstruct.items.tools.Longsword":
                case "tconstruct.items.tools.Rapier":
                case "tconstruct.items.tools.Cleaver":
                case "tconstruct.items.tools.Cutlass": return true;
                default: break;
            }
        }
        return false;
    }
}
