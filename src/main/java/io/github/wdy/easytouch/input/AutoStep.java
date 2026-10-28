package io.github.wdy.easytouch.input;

import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

/** Walking and water-exit step overrides owned only for the local player's current tick. */
public final class AutoStep {
    private static final float HEIGHT = 1.25F;
    private EntityPlayerSP owner;
    private float previousHeight, appliedHeight;

    public void begin(EntityPlayerSP player, boolean enabled, float forward, float strafe) {
        restore();
        if (!enabled || player == null || player.isDead || player.noClip
                || player.isPlayerSleeping() || player.isRiding() || player.capabilities.isFlying
                || player.isSneaking() || player.movementInput.sneak || player.isOnLadder()
                || player.worldObj.isMaterialInBB(player.boundingBox, Material.lava)) return;
        boolean inWater = player.isInWater();
        // In water, jump is the swim-up input. Let vanilla moveEntity decide whether the
        // player is supported, including downward contact made during this same tick.
        // Do not force onGround or add upward velocity to a freely swimming player.
        if (!inWater && (!player.onGround || player.movementInput.jump || player.motionY > 0.0D)) return;
        // Leave equipment/other mods' higher step heights alone. Never assume a vanilla baseline.
        if (!(player.stepHeight >= 0.0F && player.stepHeight < HEIGHT)) return;
        // Look ahead along the current controls and momentum, not the camera alone.
        // Vanilla still resolves the actual movement; prediction never changes coordinates.
        float length = MathHelper.sqrt_float(forward * forward + strafe * strafe);
        if (length > 0) { forward /= length; strafe /= length; }
        float yaw = player.rotationYaw * (float) Math.PI / 180.0F;
        double dx = player.motionX + (strafe * MathHelper.cos(yaw) - forward * MathHelper.sin(yaw)) * 0.5D;
        double dz = player.motionZ + (forward * MathHelper.cos(yaw) + strafe * MathHelper.sin(yaw)) * 0.5D;
        dx = Math.max(-1, Math.min(1, dx));
        dz = Math.max(-1, Math.min(1, dz));
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || dx * dx + dz * dz < 1.0E-10) return;
        AxisAlignedBB box = player.boundingBox;
        float height = StepGeometry.height(box, player.worldObj.getCollidingBoundingBoxes(player,
            box.addCoord(dx, HEIGHT, dz).expand(1.0E-5, 1.0E-5, 1.0E-5)), dx, dz, player.stepHeight, HEIGHT);
        if (height <= player.stepHeight) return;
        previousHeight = player.stepHeight;
        appliedHeight = height;
        owner = player;
        player.stepHeight = height;
    }

    public void restore() {
        if (owner == null) return;
        // A later equipment/mod update belongs to its writer, not to us.
        if (owner.stepHeight == appliedHeight) owner.stepHeight = previousHeight;
        owner = null;
    }
}
