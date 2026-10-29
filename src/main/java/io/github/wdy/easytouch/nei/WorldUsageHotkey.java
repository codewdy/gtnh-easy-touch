package io.github.wdy.easytouch.nei;

import codechicken.nei.recipe.GuiUsageRecipe;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/** Uses NEI's item usage lookup for the block under the world crosshair. */
public final class WorldUsageHotkey {
    private final KeyBinding key = new KeyBinding(
        "easytouch.key.worldUsage", Keyboard.KEY_U, "GTNH Easy Touch");

    public static void install() {
        WorldUsageHotkey handler = new WorldUsageHotkey();
        ClientRegistry.registerKeyBinding(handler.key);
        FMLCommonHandler.instance().bus().register(handler);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean pressed = false;
        while (key.isPressed()) pressed = true;
        Minecraft mc = Minecraft.getMinecraft();
        if (!pressed || mc.currentScreen != null || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || !mc.inGameHasFocus || !Display.isActive()) return;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        ItemStack stack = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ)
            .getPickBlock(hit, mc.theWorld, hit.blockX, hit.blockY, hit.blockZ, mc.thePlayer);
        if (stack != null && stack.getItem() != null) GuiUsageRecipe.openRecipeGui("item", stack);
    }
}
