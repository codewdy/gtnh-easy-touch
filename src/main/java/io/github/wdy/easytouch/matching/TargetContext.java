package io.github.wdy.easytouch.matching;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Target facts and full game context. This extracts data; it never decides which targets may swap. */
public interface TargetContext {
    World world();
    EntityPlayer player();
    MovingObjectPosition hit();

    default boolean isBlock() {
        return hit() != null && hit().typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    default Block block() {
        return isBlock() && world() != null ? world().getBlock(hit().blockX, hit().blockY, hit().blockZ) : null;
    }

    default String blockId() {
        Block block = block();
        return block == null ? null : String.valueOf(Block.blockRegistry.getNameForObject(block));
    }

    default int blockMetadata() {
        return isBlock() && world() != null ? world().getBlockMetadata(hit().blockX, hit().blockY, hit().blockZ) : 0;
    }

    default TileEntity tileEntity() {
        return isBlock() && world() != null ? world().getTileEntity(hit().blockX, hit().blockY, hit().blockZ) : null;
    }

    default Entity entity() {
        return hit() != null && hit().typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY ? hit().entityHit : null;
    }

    default String entityId() {
        return entity() == null ? null : EntityList.getEntityString(entity());
    }

    default boolean entityTamed() {
        return entity() instanceof EntityTameable && ((EntityTameable) entity()).isTamed();
    }

    default ItemStack heldItem() { return player() == null ? null : player().getHeldItem(); }
}
