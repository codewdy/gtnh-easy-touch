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
        int original = mc.thePlayer.inventory.currentItem;
        int best = original;
        try {
            boolean harvest = block.canHarvestBlock(mc.thePlayer, mc.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ));
            float speed = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (slot == original || stack == null || stack.stackSize <= 0) continue;
                mc.thePlayer.inventory.currentItem = slot;
                boolean candidateHarvest = block.canHarvestBlock(mc.thePlayer, mc.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ));
                float candidateSpeed = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
                if (candidateSpeed > 0 && ((!harvest && candidateHarvest)
                        || (harvest == candidateHarvest && candidateSpeed > speed + 0.000001F))) {
                    best = slot;
                    harvest = candidateHarvest;
                    speed = candidateSpeed;
                }
            }
        } finally {
            mc.thePlayer.inventory.currentItem = original;
        }
        mc.thePlayer.inventory.currentItem = best;
    }
}
