package io.github.wdy.easytouch.smeltery;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.wdy.easytouch.touch.TouchSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import org.apache.logging.log4j.Level;
import org.lwjgl.opengl.GL11;

/** Optional adapter: use TConstruct's own server packet, selecting by fluid ID, never pixel height. */
public final class SmelteryTouch {
    private static final SmelteryTouch INSTANCE = new SmelteryTouch();
    private GuiScreen owner;
    private boolean open, held, moved, opening, failed;
    private long device, finger;
    private int startX, startY, startOffset, offset, selected;
    private int left, top, width, rows;
    private static final int ROW = 32;
    public static void install() { MinecraftForge.EVENT_BUS.register(INSTANCE); }
    public static boolean accept(TouchSample sample) { return INSTANCE.touch(sample); }

    private Object logic() throws ReflectiveOperationException { return owner.getClass().getField("logic").get(owner); }
    private int field(String name) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.getInt(owner); }
            catch (NoSuchFieldException missing) { /* Look in the parent class. */ }
        }
        throw new NoSuchFieldException(name);
    }
    @SuppressWarnings("unchecked")
    private List<FluidStack> fluids() throws ReflectiveOperationException {
        Object tile = logic();
        return new ArrayList<>((List<FluidStack>) tile.getClass().getField("moltenMetal").get(tile));
    }
    private void layout(int count) throws ReflectiveOperationException {
        width = Math.min(220, owner.width - 8);
        rows = Math.max(1, Math.min(count, (owner.height - 48) / ROW));
        int right = (owner.width + 254 + (field("columns") - 3) * 22) / 2 + 4;
        left = right + width <= owner.width - 4 ? right : (owner.width - width) / 2;
        top = Math.max(4, (owner.height - (rows * ROW + 32)) / 2);
        offset = Math.max(0, Math.min(offset, Math.max(0, count - rows)));
    }
    private int row(int x, int y, int count) {
        if (x < left || x >= left + width || y < top + 24 || y >= top + 24 + rows * ROW) return -1;
        int i = fluidIndex((y - top - 24) / ROW, count);
        return i >= 0 && i < count ? i : -1;
    }
    // Screen rows run top to bottom; the tank list starts with the bottom fluid.
    private int fluidIndex(int row, int count) { return count - 1 - offset - row; }

    private boolean touch(TouchSample s) {
        if (s.kind == TouchSample.Kind.RESET) { open = held = false; owner = null; return false; }
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (gui != owner) { open = held = false; owner = gui; }
        if (failed || gui == null || !gui.getClass().getName().equals("tconstruct.smeltery.gui.SmelteryGui")) return false;
        int x = (int) (s.x * gui.width), y = (int) (s.y * gui.height);
        try {
            List<FluidStack> list = fluids();
            layout(list.size());
            if (s.kind == TouchSample.Kind.DOWN) {
                if (held) return true;
                int columns = field("columns");
                int tankX = (gui.width - (254 + (columns - 3) * 22)) / 2 + 90 + (columns - 3) * 22;
                int tankY = (gui.height - 166) / 2 + 16;
                if (!open && !(x >= tankX && x < tankX + 52 && y >= tankY && y < tankY + 52)) return false;
                held = true; device = s.deviceId; finger = s.fingerId;
                opening = !open;
                if (opening) offset = Math.max(0, list.size() - rows);
                startX = x; startY = y; startOffset = offset; moved = false;
                open = true;
                int i = row(x, y, list.size());
                selected = i < 0 ? -1 : list.get(i).getFluidID();
                return true;
            }
            if (!held) return open;
            if (s.deviceId != device || s.fingerId != finger) return true;
            if (Math.abs(x - startX) > 6 || Math.abs(y - startY) > 6) moved = true;
            if (s.kind == TouchSample.Kind.MOVE && moved && !opening) {
                offset = startOffset + (startY - y) / ROW;
                layout(list.size());
            }
            if (s.kind == TouchSample.Kind.UP) {
                held = false;
                if (!opening && !moved) {
                    int i = row(x, y, list.size());
                    if (i >= 0 && list.get(i).getFluidID() == selected) select(selected);
                    open = false;
                }
            } else if (s.kind == TouchSample.Kind.CANCEL) { held = false; open = false; }
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true; open = held = false;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Smeltery touch adapter unavailable.");
            return true;
        }
    }
    private void select(int id) throws ReflectiveOperationException {
        TileEntity tile = (TileEntity) logic();
        Object packet = Class.forName("tconstruct.util.network.SmelteryPacket")
            .getConstructor(int.class, int.class, int.class, int.class, boolean.class, int.class)
            .newInstance(tile.getWorldObj().provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord, false, id);
        Object pipeline = Class.forName("tconstruct.TConstruct").getField("packetPipeline").get(null);
        pipeline.getClass().getMethod("sendToServer", Class.forName("mantle.common.network.AbstractPacket")).invoke(pipeline, packet);
    }
    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!open || event.gui != owner || Minecraft.getMinecraft().currentScreen != owner) return;
        try {
            List<FluidStack> list = fluids(); layout(list.size());
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0, 0, 500);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                Gui.drawRect(left, top, left + width, top + 32 + rows * ROW, 0xF018202B);
                Minecraft mc = Minecraft.getMinecraft();
                mc.fontRenderer.drawString("选择浇铸液体 · 上下滑动", left + 8, top + 8, 0xFFFFFF);
                if (list.isEmpty()) mc.fontRenderer.drawString("冶炼炉内没有液体", left + 8, top + 34, 0xCCCCCC);
                for (int n = 0; n < rows && offset + n < list.size(); n++) {
                    int i = fluidIndex(n, list.size()), y = top + 24 + n * ROW;
                    FluidStack fluid = list.get(i);
                    Gui.drawRect(left + 4, y, left + width - 4, y + ROW - 2, i == 0 ? 0xFF355642 : 0xFF303D4C);
                    Gui.drawRect(left + 9, y + 8, left + 21, y + 23, 0xFF000000 | fluid.getFluid().getColor(fluid));
                    String name = (i == 0 ? "[底部] " : "") + fluid.getLocalizedName();
                    mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(name, width - 34), left + 27, y + 4, 0xFFFFFF);
                    mc.fontRenderer.drawString(fluid.amount + " mB", left + 27, y + 17, 0xCCCCCC);
                }
            } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
        } catch (ReflectiveOperationException error) {
            open = false;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Cannot draw smeltery fluid selector.");
        }
    }
}
