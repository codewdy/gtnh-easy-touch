package io.github.wdy.easytouch.input;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;

/** Session-local automatic attack controlled by a touch layout mapping. */
public final class AutoAttack {
    private static boolean enabled;
    private int delay;

    public static boolean isEnabled() { return enabled; }

    public static void toggle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        enabled = !enabled;
        mc.thePlayer.addChatMessage(new ChatComponentText("自动攻击：" + (enabled ? "开启" : "关闭")));
    }

    public void tick(Minecraft mc, boolean inWorld) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            enabled = false;
            delay = 0;
            return;
        }
        if (delay > 0) delay--;
        if (!enabled || !inWorld || mc.thePlayer.isUsingItem()) return;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(hit.entityHit instanceof EntityLivingBase) || !hit.entityHit.isEntityAlive()) return;
        if (!SwordAttack.holdingSword(mc)) {
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && SwordAttack.sword(stack.getItem())) { slot = i; break; }
            }
            if (slot < 0) return;
            mc.thePlayer.inventory.currentItem = slot;
        }
        if (delay != 0) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, hit.entityHit);
        delay = 4;
    }
}
