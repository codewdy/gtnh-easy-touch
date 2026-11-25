package io.github.wdy.easytouch.input;

import net.minecraft.client.Minecraft;

/** Applies a minimum duration only to the second block of a world-touch mining hold. */
public final class SecondBlockGuard {
    private static final long MIN_NS = 500_000_000L;
    private static boolean active, tracking;
    private static int destroyed, targetX, targetY, targetZ;
    private static long startedAt;
    private static net.minecraft.block.Block firstBlock;
    private static int firstX, firstY, firstZ;
    private static boolean firstObserved;

    private SecondBlockGuard() { }
    public static void begin() { active = true; tracking = false; destroyed = 0; firstObserved = false; firstBlock = null; }
    public static void end() { active = tracking = false; destroyed = 0; firstObserved = false; firstBlock = null; }

    public static boolean allowClick(int x, int y, int z) {
        if (!active) return true;
        Minecraft mc = Minecraft.getMinecraft();
        // A custom tool or server update can remove the first block without a successful vanilla return.
        if (destroyed == 0 && firstObserved
                && mc.theWorld.getBlock(firstX, firstY, firstZ) != firstBlock) {
            destroyed = 1;
        }
        if (destroyed == 0) {
            net.minecraft.block.Block block = mc.theWorld.getBlock(x, y, z);
            if (!block.isAir(mc.theWorld, x, y, z)) {
                firstObserved = true;
                firstBlock = block;
                firstX = x; firstY = y; firstZ = z;
            }
            return true;
        }
        // Ignore the stale ray hit from the frame that removed the first block.
        if (mc.theWorld.isAirBlock(x, y, z)) return false;
        float speed = mc.theWorld.getBlock(x, y, z)
            .getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, x, y, z);
        // Instant blocks break on the START packet itself: delay that packet, not merely local destruction.
        long naturalNs = speed >= 1 ? 0 : speed > 0
            ? (long)Math.min(MIN_NS, Math.ceil(1.0 / speed) * 50_000_000.0) : MIN_NS;
        return allowClick(System.nanoTime(), x, y, z, naturalNs);
    }

    static boolean allowClick(long now, int x, int y, int z, long naturalNs) {
        if (!active || destroyed != 1) return true;
        if (!tracking || x != targetX || y != targetY || z != targetZ) {
            tracking = true;
            targetX = x; targetY = y; targetZ = z;
            startedAt = now;
        }
        return now - startedAt >= Math.max(0, MIN_NS - naturalNs);
    }

    public static void onDestroyed(boolean success) {
        if (!active || !success) return;
        if (++destroyed >= 2) end();
    }
}
