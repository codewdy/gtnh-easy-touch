package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.MinecraftInput;
import io.github.wdy.easytouch.input.SmartMouseInput;
import io.github.wdy.easytouch.input.SneakUseInput;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

/** Event wiring and shared lifecycle boundaries; each feature owns its own behavior and state. */
public final class ClientInput {
    private final MinecraftInput input = new MinecraftInput(Minecraft.getMinecraft());
    private final KeyStateMerger keys = new KeyStateMerger(input);
    private final SmartMouseInput smartMouse;
    private final SneakUseInput sneakUse;

    private ClientInput(TouchConfig config) {
        smartMouse = new SmartMouseInput(input, config, keys.newSource(), this::cancelInputs);
        sneakUse = new SneakUseInput(input, keys.newSource());
    }

    public static void install(TouchConfig config) {
        ClientInput client = new ClientInput(config);
        client.smartMouse.registerKey();
        client.sneakUse.registerKey();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance().bus().register(client);
    }

    private void cancelInputs() {
        smartMouse.cancel();
        sneakUse.cancel();
        keys.discardClicks();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void mouse(MouseEvent event) {
        if (!input.inWorld()) cancelInputs();
        else smartMouse.mouse(event);
        keys.sync(input.inWorld());
    }

    private void inputChanged() {
        if (!input.inWorld()) cancelInputs();
        else sneakUse.input();
        keys.sync(input.inWorld());
    }

    @SubscribeEvent
    public void keyInput(InputEvent.KeyInputEvent event) { inputChanged(); }

    @SubscribeEvent
    public void mouseInput(InputEvent.MouseInputEvent event) { inputChanged(); }

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
            sneakUse.tickStart();
        } else {
            smartMouse.tickEnd();
            sneakUse.tickEnd();
        }
        keys.sync(input.inWorld());
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) { smartMouse.hud(event); }
}
