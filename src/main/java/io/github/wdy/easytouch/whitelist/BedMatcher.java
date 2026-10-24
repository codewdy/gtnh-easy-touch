package io.github.wdy.easytouch.whitelist;

import java.util.HashSet;
import java.util.Set;
import io.github.wdy.easytouch.matching.TargetContext;
import io.github.wdy.easytouch.matching.TargetMatcher;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.util.MovingObjectPosition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Matches real beds across mods and variants, without probing their activation behavior. */
public final class BedMatcher implements TargetMatcher {
    private static final Logger LOG = LogManager.getLogger("GTNH Easy Touch");
    private final Set<Class<?>> incompatible = new HashSet<Class<?>>();

    @Override
    public boolean matches(TargetContext context) {
        if (!context.isBlock()) return false;
        Block block = context.block();
        if (block == null) return false;
        // Forge's default isBed only recognizes Blocks.bed; subclasses may inherit that default.
        // This also covers all Et Futurum dyed beds without listing registry IDs or colors.
        if (block instanceof BlockBed) return true;
        Class<?> type = block.getClass();
        if (incompatible.contains(type)) return false;
        try {
            MovingObjectPosition hit = context.hit();
            // Carpenter's beds use a different base class and implement this Forge query.
            // Keep the actual hit position so state-dependent beds can distinguish both halves.
            return block.isBed(context.world(), hit.blockX, hit.blockY, hit.blockZ, context.player());
        } catch (RuntimeException | LinkageError error) {
            incompatible.add(type);
            LOG.warn("Cannot inspect bed type {} ({}); keeping vanilla buttons.",
                type.getName(), error.getClass().getSimpleName());
            return false;
        }
    }
}
