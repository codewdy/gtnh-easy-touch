package io.github.wdy.easytouch.input;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

/** Preview and duration use the same tool selection as world hold mining. */
public final class WorldHold {
    private WorldHold() { }

    public static int slot(Minecraft mc, boolean autoTool) {
        if (mc.thePlayer == null) return -1;
        return autoTool && !HeldItemUse.matches(mc.thePlayer.getHeldItem())
            ? AutoTool.bestSlot(mc) : mc.thePlayer.inventory.currentItem;
    }

    public static long delay(Minecraft mc, boolean autoTool) {
        int selected = slot(mc, autoTool);
        if (selected < 0 || mc.theWorld == null) return 300_000_000L;
        ItemStack held = mc.thePlayer.getHeldItem();
        double durationMs = 0;
        if (HeldItemUse.matches(held)) {
            // Sustained-use items expose their use duration in game ticks.
            durationMs = held.getMaxItemUseDuration() * 50.0;
        } else {
            MovingObjectPosition hit = mc.objectMouseOver;
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                int original = mc.thePlayer.inventory.currentItem;
                try {
                    mc.thePlayer.inventory.currentItem = selected;
                    float speed = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ)
                        .getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                    durationMs = speed > 0 ? Math.ceil(1.0 / speed) * 50.0 : Double.POSITIVE_INFINITY;
                } finally {
                    mc.thePlayer.inventory.currentItem = original;
                }
            }
        }
        return (long)(Math.max(300, 600 - durationMs) * 1_000_000);
    }
}
