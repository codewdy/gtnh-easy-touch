package io.github.wdy.easytouch.mining;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Optional EZMiner integration: no EZMiner types are linked when the mod is absent. */
public final class EzMinerAutoVein {
    public static volatile boolean enabled = true;
    private static volatile boolean failed;

    public static Object route(Object founder, Object mode, Object center, Object queue,
            EntityPlayer player, Object config) {
        if (!enabled || failed || founder == null || player == null || player.worldObj == null) return founder;
        try {
            Class<?> modeType = mode.getClass();
            if (modeType.getField("mainMode").getInt(mode) != 1
                    || modeType.getField("chainMode").getInt(mode) != 0) return founder;
            if (!founder.getClass().getName().equals("com.czqwq.EZMiner.core.founder.ChainPositionFounder")) return founder;
            Class<?> positionType = center.getClass();
            int x = positionType.getField("x").getInt(center);
            int y = positionType.getField("y").getInt(center);
            int z = positionType.getField("z").getInt(center);
            Block block = (Block) founder.getClass().getField("sampleBlock").get(founder);
            int meta = founder.getClass().getField("sampleBlockMeta").getInt(founder);
            ClassLoader loader = founder.getClass().getClassLoader();
            Class<?> matcher = Class.forName("com.czqwq.EZMiner.core.founder.DeterminingIdentical", true, loader);
            Method match = matcher.getMethod("isGTLargeVeinOre", Block.class, int.class,
                World.class, int.class, int.class, int.class);
            if (!Boolean.TRUE.equals(match.invoke(null, block, meta, player.worldObj, x, y, z))) return founder;
            Class<?> vein = Class.forName("com.czqwq.EZMiner.core.founder.GtVeinOreFounder", true, loader);
            Constructor<?> constructor = vein.getConstructor(positionType,
                java.util.concurrent.LinkedBlockingQueue.class, EntityPlayer.class, config.getClass());
            // The original constructor already queued the origin. Initialize the replacement
            // against a private queue, then reuse the original queue without a duplicate.
            Object replacement = constructor.newInstance(center,
                new java.util.concurrent.LinkedBlockingQueue<Object>(), player, config);
            vein.getField("positions").set(replacement, queue);
            return replacement;
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            System.err.println("[GTNH Easy Touch] EZMiner auto vein unavailable; keeping original mode: " + e);
            return founder;
        }
    }

    private EzMinerAutoVein() {}
}
