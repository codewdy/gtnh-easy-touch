package io.github.wdy.easytouch.guide;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import org.apache.logging.log4j.Level;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Optional adapter for GuideNH 1.3.x. No GuideNH classes are bundled or required. */
public final class WorldGuideHotkey {
    private final KeyBinding itemKey;
    private final KeyBinding homeKey;
    private final Method update;
    private final Method isHeld;
    private final Method openHome;
    private final Field ticks;
    private boolean active;
    private boolean deferredHome;
    private boolean waitForRelease;
    private boolean disabled;
    private ItemStack previousStack;
    private int x, y, z;

    private WorldGuideHotkey() throws ReflectiveOperationException {
        Class<?> item = Class.forName("com.hfstudio.guidenh.client.hotkey.OpenGuideHotkey");
        Class<?> home = Class.forName("com.hfstudio.guidenh.client.hotkey.OpenGuideHomeHotkey");
        itemKey = (KeyBinding) item.getField("OPEN_GUIDE_KEY").get(null);
        homeKey = (KeyBinding) home.getField("OPEN_GUIDE_HOME_KEY").get(null);
        update = item.getMethod("update", ItemStack.class);
        isHeld = item.getMethod("isKeyHeld");
        ticks = item.getField("ticksKeyHeld");
        openHome = Class.forName("com.hfstudio.guidenh.guide.internal.GuideScreen")
            .getMethod("openFromHomeHotkey");
    }

    public static void install() {
        try {
            FMLCommonHandler.instance().bus().register(new WorldGuideHotkey());
        } catch (ClassNotFoundException ignored) {
            // Optional mod is absent.
        } catch (ReflectiveOperationException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Cannot install world guide shortcut.");
        }
    }

    private void reset() throws IllegalAccessException {
        if (active) ticks.setInt(null, 0);
        active = false;
        deferredHome = false;
        previousStack = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (disabled || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            boolean down = (Boolean) isHeld.invoke(null);
            if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !mc.inGameHasFocus) {
                reset();
                waitForRelease = down;
                return;
            }
            boolean shared = itemKey.getKeyCode() != 0 && itemKey.getKeyCode() == homeKey.getKeyCode();
            boolean requested = false;
            if (shared) {
                // Drain the press queue before GuideNH's NORMAL-priority home handler.
                while (homeKey.isPressed()) requested = true;
            }
            if (waitForRelease) {
                if (!down) waitForRelease = false;
                return;
            }
            if (!down) {
                boolean showHome = shared && (deferredHome || requested);
                reset();
                if (showHome) openHome.invoke(null);
                return;
            }
            if (!active) {
                ticks.setInt(null, 0);
                active = true;
            }
            deferredHome |= shared;
            MovingObjectPosition hit = mc.objectMouseOver;
            ItemStack stack = null;
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                stack = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ)
                    .getPickBlock(hit, mc.theWorld, hit.blockX, hit.blockY, hit.blockZ, mc.thePlayer);
            }
            if (stack == null) {
                ticks.setInt(null, 0);
                previousStack = null;
                return;
            }
            // Identical adjacent machines still require a fresh hold on the new target.
            if (previousStack == null || x != hit.blockX || y != hit.blockY || z != hit.blockZ
                    || !ItemStack.areItemStacksEqual(previousStack, stack)) {
                ticks.setInt(null, 0);
                previousStack = stack.copy();
                x = hit.blockX;
                y = hit.blockY;
                z = hit.blockZ;
            }
            // Reuse GuideNH's timing, item/ore indices, and multiple-page selection.
            update.invoke(null, stack);
            if (mc.currentScreen != null) {
                reset();
                waitForRelease = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disabled = true;
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] World guide shortcut disabled after an error.");
        }
    }
}
