package io.github.wdy.easytouch.map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import org.apache.logging.log4j.LogManager;

/** Keeps the chosen map coordinates fixed while the player selects an action. */
public final class MapActionMenu extends GuiScreen {
    private final GuiScreen parent;
    private final int x, z;
    private Object waypoint;
    private Class<?> waypointType, teleportType;
    private String error = "";
    private static final int ROW = 22;
    private final float anchorX, anchorY;
    private int left, top, menuWidth;
    private GuiButton add, teleport, depleted;
    private final Object ore;
    private GuiButton[] rows;
    private java.lang.reflect.Field mapMouseX, mapMouseY;
    public MapActionMenu(GuiScreen parent, int x, int z, float anchorX, float anchorY) {
        this.parent = parent; this.x = x; this.z = z;
        this.anchorX = anchorX; this.anchorY = anchorY;
        ore = OreVeinActions.find((int) (anchorX * net.minecraft.client.Minecraft.getMinecraft().displayWidth),
            (int) (anchorY * net.minecraft.client.Minecraft.getMinecraft().displayHeight));
    }
    @Override public void initGui() {
        buttonList.clear();
        add = new GuiButton(0, 0, 0, "添加路径点");
        teleport = new GuiButton(1, 0, 0, "传送");
        depleted = new GuiButton(3, 0, 0, "标记为枯竭");
        if (ore != null) {
            try {
                if (OreVeinActions.isDepleted(ore)) depleted.displayString = "取消枯竭标记";
            } catch (ReflectiveOperationException | RuntimeException e) {
                depleted.enabled = false;
                fail(e);
            }
        }
        rows = ore == null ? new GuiButton[] {add, teleport} : new GuiButton[] {add, teleport, depleted};
        parent.setWorldAndResolution(mc, width, height);
        try {
            mapMouseX = parent.getClass().getDeclaredField("mx");
            mapMouseY = parent.getClass().getDeclaredField("my");
            mapMouseX.setAccessible(true);
            mapMouseY.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            mapMouseX = null; mapMouseY = null;
            fail(e);
        }
        try {
            waypointType = Class.forName("journeymap.client.model.Waypoint");
            teleportType = Class.forName("journeymap.client.command.CmdTeleportWaypoint");
            waypoint = waypointType.getMethod("of", EntityPlayer.class).invoke(null, mc.thePlayer);
            // Unexplored terrain has no reliable surface height; expose Y in the native editor.
            int y = (int) mc.thePlayer.posY;
            if (mc.theWorld.blockExists(x, 0, z)) y = mc.theWorld.getHeightValue(x, z);
            waypointType.getMethod("setLocation", int.class, int.class, int.class, int.class)
                .invoke(waypoint, x, y, z, mc.thePlayer.dimension);
            teleport.enabled = (Boolean) teleportType.getMethod("isPermitted", net.minecraft.client.Minecraft.class).invoke(null, mc)
                && (Boolean) waypointType.getMethod("isTeleportReady").invoke(waypoint);
            if (!teleport.enabled) teleport.displayString = "传送（不可用）";
        } catch (ReflectiveOperationException | RuntimeException e) {
            add.enabled = false; teleport.enabled = false; fail(e);
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 2) { mc.displayGuiScreen(parent); return; }
        try {
            if (button.id == 3) {
                OreVeinActions.toggleDepleted(ore);
                mc.displayGuiScreen(parent);
            } else if (button.id == 0) {
                Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
                ui.getMethod("openWaypointEditor", waypointType, boolean.class, Class.forName("journeymap.client.ui.component.JmUI"))
                    .invoke(ui.getMethod("getInstance").invoke(null), waypoint, true, parent);
            } else if (button.id == 1) {
                Object command = teleportType.getConstructor(waypointType).newInstance(waypoint);
                teleportType.getMethod("run").invoke(command);
            }
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    private void fail(Exception e) {
        error = "地图操作失败，请查看日志";
        LogManager.getLogger("GTNH Easy Touch").warn("JourneyMap action failed", e);
    }
    private void layout() {
        menuWidth = Math.min(width - 8, Math.max(64, Math.max(fontRendererObj.getStringWidth(add.displayString),
            fontRendererObj.getStringWidth(teleport.displayString)) + 20));
        for (GuiButton row : rows) menuWidth = Math.min(width - 8, Math.max(menuWidth, fontRendererObj.getStringWidth(row.displayString) + 20));
        left = Math.max(4, Math.min((int) (anchorX * width), width - menuWidth - 4));
        top = Math.max(4, Math.min((int) (anchorY * height), height - ROW * rows.length - 4));
    }
    @Override protected void mouseClicked(int mx, int my, int button) {
        layout();
        if (button != 0 || mx < left || mx >= left + menuWidth || my < top || my >= top + ROW * rows.length) {
            mc.displayGuiScreen(parent);
            return;
        }
        GuiButton selected = rows[(my - top) / ROW];
        if (selected.enabled) actionPerformed(selected);
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        // Keep the map visible behind the same compact rows used by the item touch menu.
        drawMapBackground(partial);
        layout();
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(0, 0, 500);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        try {
            drawRect(left - 1, top - 1, left + menuWidth + 1, top + ROW * rows.length + 1, 0xff777777);
            for (int i = 0; i < rows.length; i++) {
                int y = top + i * ROW;
                drawRect(left, y, left + menuWidth, y + ROW, 0xf0202020);
                if (i > 0) drawRect(left + 4, y, left + menuWidth - 4, y + 1, 0xff555555);
                fontRendererObj.drawStringWithShadow(rows[i].displayString, left + 10, y + (ROW - 8) / 2,
                    rows[i].enabled ? 0xffffff : 0x777777);
            }
            if (!error.isEmpty()) fontRendererObj.drawStringWithShadow(error, 4, 4, 0xff7777);
        } finally {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
    private void drawMapBackground(float partial) {
        if (mapMouseX == null || mapMouseY == null) {
            parent.drawScreen(-1, -1, partial);
            return;
        }
        try {
            int oldX = mapMouseX.getInt(parent), oldY = mapMouseY.getInt(parent);
            try {
                // Navigator hit testing uses these cached coordinates, not drawScreen's arguments.
                mapMouseX.setInt(parent, -10000);
                mapMouseY.setInt(parent, -10000);
                parent.drawScreen(-1, -1, partial);
            } finally {
                mapMouseX.setInt(parent, oldX);
                mapMouseY.setInt(parent, oldY);
            }
        } catch (IllegalAccessException e) {
            mapMouseX = null; mapMouseY = null;
            fail(e);
        }
    }
    @Override protected void keyTyped(char ch, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
