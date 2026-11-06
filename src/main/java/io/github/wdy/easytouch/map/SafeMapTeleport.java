package io.github.wdy.easytouch.map;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

/** Transfers requests to the integrated server thread before reading or generating terrain. */
public final class SafeMapTeleport {
    private static final AtomicReference<Request> PENDING = new AtomicReference<Request>();
    private static boolean installed;
    private static final class Request {
        final MinecraftServer server;
        final UUID player;
        final int dimension, x, z;
        Request(Minecraft mc, int x, int z) {
            server = mc.getIntegratedServer(); player = mc.thePlayer.getUniqueID();
            dimension = mc.thePlayer.dimension; this.x = x; this.z = z;
        }
    }
    public static void install() {
        if (!installed) {
            installed = true;
            FMLCommonHandler.instance().bus().register(new SafeMapTeleport());
        }
    }
    public static void request(Minecraft mc, int x, int z) {
        if (mc.getIntegratedServer() == null || mc.thePlayer == null) return;
        if (PENDING.compareAndSet(null, new Request(mc, x, z)))
            mc.thePlayer.addChatMessage(new ChatComponentText("正在加载目标地形并寻找安全落点……"));
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Request r = PENDING.getAndSet(null);
        if (r == null || r.server != MinecraftServer.getServer()) return;
        EntityPlayerMP player = null;
        for (Object entry : r.server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP candidate = (EntityPlayerMP) entry;
            if (candidate.getUniqueID().equals(r.player)) { player = candidate; break; }
        }
        if (player == null || player.dimension != r.dimension || player.isDead) return;
        if (!r.server.getConfigurationManager().func_152596_g(player.getGameProfile())) {
            player.addChatMessage(new ChatComponentText("传送不可用（权限不足）")); return;
        }
        if (player.isRiding()) {
            player.addChatMessage(new ChatComponentText("请先离开载具，再传送。")); return;
        }
        try {
            WorldServer world = player.getServerForPlayer();
            boolean nether = world.provider.dimensionId == -1;
            if (world.provider.hasNoSky && !nether) {
                player.addChatMessage(new ChatComponentText("该维度无法确定露天地表，已取消安全传送。"));
                return;
            }
            int bestX = 0, bestY = -1, bestZ = 0;
            // Closest column wins ties; Nether candidates compete by height.
            search:
            // Search closest columns first; cap work at a 9 x 9 area.
            for (int radius = 0; radius <= 4; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        int x = r.x + dx, z = r.z + dz;
                        if (Math.abs((long) x) >= 29999984 || Math.abs((long) z) >= 29999984) continue;
                        world.getChunkFromBlockCoords(x, z);
                        // Nether: keep all three air blocks below the roof at Y=127.
                        int surface = nether ? Math.min(124, world.getActualHeight() - 3) : world.getHeightValue(x, z);
                        int minimum = nether ? Math.max(1, bestY + 1) : surface;
                        for (int y = surface; y >= minimum && y >= 1 && y <= world.getActualHeight() - 2; y--) {
                            if (nether ? !world.isAirBlock(x, y + 2, z) : !world.canBlockSeeTheSky(x, y, z)) continue;
                            if (!world.getBlock(x, y - 1, z).isNormalCube()
                                || world.getBlock(x, y - 1, z) == Blocks.bedrock
                                || !world.isAirBlock(x, y, z) || !world.isAirBlock(x, y + 1, z)) continue;
                            AxisAlignedBB body = AxisAlignedBB.getBoundingBox(x + .2, y, z + .2, x + .8, y + 1.8, z + .8);
                            if (!world.getCollidingBoundingBoxes(player, body).isEmpty()
                                || world.isAnyLiquid(body.expand(.1, .1, .1)) || dangerous(world, x, y, z)) continue;
                            bestX = x; bestY = y; bestZ = z;
                            if (!nether) break search;
                            break;
                        }
                    }
                }
            }
            if (bestY >= 1) {
                player.motionX = player.motionY = player.motionZ = 0;
                player.fallDistance = 0;
                player.playerNetServerHandler.setPlayerLocation(bestX + .5, bestY, bestZ + .5, player.rotationYaw, player.rotationPitch);
                player.addChatMessage(new ChatComponentText("已传送至安全落点：" + bestX + ", " + bestY + ", " + bestZ));
            } else {
                player.addChatMessage(new ChatComponentText("目标附近没有找到符合条件的安全落点，已取消传送。"));
            }
        } catch (RuntimeException ex) {
            org.apache.logging.log4j.LogManager.getLogger("GTNH Easy Touch").warn("Safe map teleport failed", ex);
            player.addChatMessage(new ChatComponentText("安全落点检查失败，请查看日志。"));
        }
    }
    private static boolean dangerous(WorldServer world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int dy = -1; dy <= 1; dy++) {
                net.minecraft.block.Block b = world.getBlock(x + dx, y + dy, z + dz);
                if (b.getMaterial().isLiquid() || b == Blocks.fire || b == Blocks.cactus) return true;
            }
        return false;
    }
}
