package io.github.wdy.easytouch.map;

import io.github.wdy.easytouch.layout.TouchLayout;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;

/** Resolve live JourneyMap waypoints each time the menu opens. */
public final class TeleportTargets {
    private TeleportTargets() {}
    public static void populate(TouchLayout.Button menu) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            Class<?> command = Class.forName("journeymap.client.command.CmdTeleportWaypoint");
            Class<?> type = Class.forName("journeymap.client.model.Waypoint");
            Class<?> store = Class.forName("journeymap.client.waypoint.WaypointStore");
            if (mc.thePlayer == null || !(Boolean) command.getMethod("isPermitted", Minecraft.class).invoke(null, mc)) {
                status(menu, "传送不可用（权限不足）"); return;
            }
            Collection<?> points = (Collection<?>) store.getMethod("getAll").invoke(store.getMethod("instance").invoke(null));
            for (Object point : points) {
                if (!(Boolean) type.getMethod("isTeleportReady").invoke(point)) continue;
                TouchLayout.Button option = new TouchLayout.Button();
                option.label = (String) type.getMethod("getName").invoke(point);
                option.action = () -> {
                    try {
                        if (!(Boolean) command.getMethod("isPermitted", Minecraft.class).invoke(null, mc)
                                || !(Boolean) type.getMethod("isTeleportReady").invoke(point)) return;
                        command.getMethod("run").invoke(command.getConstructor(type).newInstance(point));
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        LogManager.getLogger("GTNH Easy Touch").warn("Teleport failed", ex);
                        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("传送失败，请查看日志"));
                    }
                };
                menu.options.add(option);
            }
            menu.options.sort((a,b) -> a.label.compareToIgnoreCase(b.label));
            if (menu.options.isEmpty()) status(menu, "没有可传送的路径点");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LogManager.getLogger("GTNH Easy Touch").warn("Cannot load teleport targets", ex);
            status(menu, "旅行地图不可用");
        } finally {
            TouchLayout.Button back = new TouchLayout.Button();
            back.label = "返回";
            back.action = () -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer != null) mc.thePlayer.sendChatMessage("/back");
            };
            menu.options.add(0, back);
        }
    }
    private static void status(TouchLayout.Button menu, String text) {
        TouchLayout.Button option = new TouchLayout.Button();
        option.label=text;
        menu.options.add(option);
    }
}
