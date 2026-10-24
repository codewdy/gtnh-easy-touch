package io.github.wdy.easytouch.matching;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.StatCollector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A persistent target type, independent of world coordinates and individual entity UUIDs. */
public final class TargetIdentity {
    private static final Logger LOG = LogManager.getLogger("GTNH Easy Touch");
    private static final Set<Class<?>> warned = new HashSet<Class<?>>();
    public final String key;
    private final ItemStack item;
    private final String name;

    private TargetIdentity(String key, ItemStack item, String name) {
        this.key = key;
        this.item = item;
        this.name = name;
    }

    public static TargetIdentity resolve(TargetContext context) {
        if (context.hit() == null) return null;
        try {
            if (context.isBlock()) {
                ItemStack picked = context.pickedBlock();
                if (picked != null && picked.getItem() != null) {
                    Object id = Item.itemRegistry.getNameForObject(picked.getItem());
                    if (id != null) {
                        // Pick-block normalizes facing/occupied states and exposes GT's machine ID
                        // and AE2's selected part. Ignore stack size and mutable inventory/custom-name NBT.
                        return new TargetIdentity("block_item:" + id + "@" + picked.getItemDamage(), picked, null);
                    }
                }
                String id = context.blockId();
                if (id == null || "null".equals(id) || "minecraft:air".equals(id)) return null;
                // Only use a world-block fallback when there is no pick item. A multipart host's
                // fallback never matches its selectable parts, which use block_item keys above.
                return new TargetIdentity("block_state:" + id + "@" + context.blockMetadata(), null, id);
            }
            if (context.hit().typeOfHit != MovingObjectType.ENTITY) return null;
            Entity entity = context.entity();
            if (entity instanceof EntityPlayer) return new TargetIdentity("entity:Player", null,
                StatCollector.translateToLocal("easytouch.target.players"));
            String id = context.entityId();
            if (id != null && !id.isEmpty()) return new TargetIdentity("entity:" + id, null,
                StatCollector.translateToLocal("entity." + id + ".name"));
            return entity == null ? null : new TargetIdentity("entity_class:" + entity.getClass().getName(), null,
                entity.getClass().getSimpleName());
        } catch (RuntimeException | LinkageError error) {
            if (warned.add(error.getClass())) LOG.warn("Cannot identify pointed target type ({}); leaving rules unchanged.",
                error.getClass().getSimpleName());
            return null;
        }
    }

    public String displayName() {
        try {
            return item == null ? name : item.getDisplayName();
        } catch (RuntimeException | LinkageError error) {
            return key;
        }
    }
}
