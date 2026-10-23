package io.github.wdy.easytouch.matching;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Captures one resolution's world, player and ray hit for all matchers. */
public final class MinecraftTargetContext implements TargetContext {
    private final World world;
    private final EntityPlayer player;
    private final MovingObjectPosition hit;

    public MinecraftTargetContext(Minecraft mc) {
        world = mc.theWorld;
        player = mc.thePlayer;
        hit = mc.objectMouseOver;
    }

    public World world() { return world; }
    public EntityPlayer player() { return player; }
    public MovingObjectPosition hit() { return hit; }
}
