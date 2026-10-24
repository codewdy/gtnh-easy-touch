package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.MinecraftInput;
import io.github.wdy.easytouch.input.SmartMouseInput;
import io.github.wdy.easytouch.nei.NeiCraftingTreeInput;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.Level;

/** Client input registration, event wiring and lifecycle boundaries. */
public final class ClientInput {
    private final MinecraftInput input = new MinecraftInput(Minecraft.getMinecraft());
    private final KeyStateMerger keys = new KeyStateMerger(input);
    private final SmartMouseInput smartMouse;

    private ClientInput(TouchConfig config) {
        smartMouse = new SmartMouseInput(input, config, keys.newSource(), this::cancelInputs);
    }

    public static void install(TouchConfig config) {
        ClientInput client = new ClientInput(config);
        client.smartMouse.registerKey();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance().bus().register(client);
    }

    public static void installNeiCrafting() {
        if (!Loader.isModLoaded("NotEnoughItems")) return;
        try {
            NeiCraftingTreeInput.install();
        } catch (RuntimeException | LinkageError error) {
            // Loading the optional adapter itself can fail before its install() body is entered.
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Cannot load NEI crafting tree controls.");
        }
    }

    private void cancelInputs() {
        smartMouse.cancel();
        keys.discardClicks();
    }

    @SubscribeEvent
    public void targetKey(InputEvent.KeyInputEvent event) { smartMouse.targetKeyInput(); }

    @SubscribeEvent
    public void targetMouse(InputEvent.MouseInputEvent event) { smartMouse.targetMouseInput(); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void mouse(MouseEvent event) {
        if (!input.inWorld()) cancelInputs();
        else smartMouse.mouse(event);
        keys.sync(input.inWorld());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void gui(GuiOpenEvent event) {
        if (event.gui != null) {
            cancelInputs();
            keys.sync(false);
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            cancelInputs();
            keys.sync(false);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (!input.inWorld()) cancelInputs();
        if (event.phase == TickEvent.Phase.START) {
            smartMouse.tickStart();
        } else {
            smartMouse.tickEnd();
        }
        keys.sync(input.inWorld());
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) { smartMouse.hud(event); }
}
