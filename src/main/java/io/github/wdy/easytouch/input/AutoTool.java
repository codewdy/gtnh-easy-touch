package io.github.wdy.easytouch.input;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

/** Select only hotbar slots; never move inventory contents or send speculative slot changes. */
public final class AutoTool {
    private AutoTool() {}

    public static void select(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null
                || !mc.inGameHasFocus || mc.playerController.isInCreativeMode()
                || mc.thePlayer.isUsingItem()) return;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        if (block.isAir(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ)
                || block.getBlockHardness(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ) <= 0) return;
        int metadata = mc.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ);
        if (block.canHarvestBlock(mc.thePlayer, metadata)) return;
        int original = mc.thePlayer.inventory.currentItem;
        int best = original;
        try {
            float speed = 0;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (slot == original || stack == null || stack.stackSize <= 0) continue;
                mc.thePlayer.inventory.currentItem = slot;
                if (!block.canHarvestBlock(mc.thePlayer, metadata)) continue;
                float candidateSpeed = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                if (candidateSpeed > 0 && (best == original || candidateSpeed > speed + 0.000001F)) {
                    best = slot;
                    speed = candidateSpeed;
                }
            }
        } finally {
            mc.thePlayer.inventory.currentItem = original;
        }
        mc.thePlayer.inventory.currentItem = best;
    }
}
