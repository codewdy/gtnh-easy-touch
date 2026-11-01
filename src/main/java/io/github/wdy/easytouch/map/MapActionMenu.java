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
    private GuiButton add, teleport;
    public MapActionMenu(GuiScreen parent, int x, int z, float anchorX, float anchorY) {
        this.parent = parent; this.x = x; this.z = z;
        this.anchorX = anchorX; this.anchorY = anchorY;
    }
    @Override public void initGui() {
        buttonList.clear();
        add = new GuiButton(0, 0, 0, "添加路径点");
        teleport = new GuiButton(1, 0, 0, "传送");
        parent.setWorldAndResolution(mc, width, height);
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
            if (button.id == 0) {
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
        left = Math.max(4, Math.min((int) (anchorX * width), width - menuWidth - 4));
        top = Math.max(4, Math.min((int) (anchorY * height), height - ROW * 2 - 4));
    }
    @Override protected void mouseClicked(int mx, int my, int button) {
        layout();
        if (button != 0 || mx < left || mx >= left + menuWidth || my < top || my >= top + ROW * 2) {
            mc.displayGuiScreen(parent);
            return;
        }
        GuiButton selected = my < top + ROW ? add : teleport;
        if (selected.enabled) actionPerformed(selected);
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        // Keep the map visible behind the same compact rows used by the item touch menu.
        parent.drawScreen(-1, -1, partial);
        layout();
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(0, 0, 500);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        try {
            drawRect(left - 1, top - 1, left + menuWidth + 1, top + ROW * 2 + 1, 0xff777777);
            GuiButton[] rows = {add, teleport};
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
    @Override protected void keyTyped(char ch, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
