package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.MinecraftInput;
import io.github.wdy.easytouch.input.SmartTouchInput;
import io.github.wdy.easytouch.input.TouchLook;
import io.github.wdy.easytouch.input.TouchHotbar;
import io.github.wdy.easytouch.input.TouchMovement;
import io.github.wdy.easytouch.input.MovementWheelHud;
import net.minecraft.client.gui.ScaledResolution;
import io.github.wdy.easytouch.touch.TouchInputEvent;
import io.github.wdy.easytouch.touch.TouchInput;
import io.github.wdy.easytouch.input.TouchActions;
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
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.Level;

/** Client input registration, event wiring and lifecycle boundaries. */
public final class ClientInput {
    private final MinecraftInput input = new MinecraftInput(Minecraft.getMinecraft());
    private final KeyStateMerger keys = new KeyStateMerger(input);
    private final TouchHotbar hotbar = new TouchHotbar(slot -> input.mc.thePlayer.inventory.currentItem = slot);
    private final TouchMovement movement = new TouchMovement(keys.newSource());
    private final TouchConfig config;
    private final SmartTouchInput smartTouch;
    private final TouchLook touchLook;
    private final KeyStateMerger.Source touchKeys = keys.newSource();

    private ClientInput(TouchConfig config) {
        this.config = config;
        smartTouch = new SmartTouchInput(input, config);
        new io.github.wdy.easytouch.layout.TouchButtons(config, keys.newSource(), () -> { cancelInputs(); keys.sync(input.inWorld()); }, smartTouch::touchSecondary);
        final TouchActions actions = new TouchActions(touchKeys, smartTouch::touchTap);
        touchLook = new TouchLook(new TouchLook.Port() {
            public boolean inWorld() { return input.inWorld(); }
            public int width() { return input.mc.displayWidth; }
            public int height() { return input.mc.displayHeight; }
            public void turn(float dx, float dy) {
                input.turn(dx * config.touchDragSensitivity, dy * config.touchDragSensitivity);
            }
            public void click() { actions.click(); }
            public void secondaryClick() { touchKeys.click(smartTouch.touchSecondary() == Action.ATTACK
                ? KeyStateMerger.Key.ATTACK : KeyStateMerger.Key.USE); }
            public void press() { actions.press(); }
            public void release() { actions.release(); }
        });
    }

    public static void install(TouchConfig config) {
        ClientInput client = new ClientInput(config);
        io.github.wdy.easytouch.touch.ModularUiTouchInput.install();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance().bus().register(client);
    }

    public static void installNeiCrafting() {
        if (!Loader.isModLoaded("NotEnoughItems")) return;
        try {
            io.github.wdy.easytouch.nei.NeiTouchMenu.install();
            NeiCraftingTreeInput.install();
        } catch (RuntimeException | LinkageError error) {
            // Loading the optional adapter itself can fail before its install() body is entered.
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Cannot load NEI crafting tree controls.");
        }
    }

    private void cancelInputs() {
        touchLook.cancel();
        movement.cancel();
        hotbar.cancel();
        touchKeys.clear();
        keys.discardClicks();
    }

    @SubscribeEvent
    public void touch(TouchInputEvent event) {
        if (event.consumed) { keys.sync(input.inWorld()); return; }
        ScaledResolution resolution = new ScaledResolution(input.mc, input.mc.displayWidth, input.mc.displayHeight);
        if (hotbar.accept(event.sample, resolution.getScaledWidth(), resolution.getScaledHeight(),
                config.touchEnabled && input.inWorld() && !input.mc.gameSettings.hideGUI)) {
            event.consumed = true;
        } else if (!movement.accept(event.sample, resolution.getScaledWidth(), resolution.getScaledHeight(),
                config.touchEnabled && config.buttonsVisible && input.inWorld() && !input.mc.gameSettings.hideGUI)) {
            touchLook.accept(event.sample);
        }
        keys.sync(input.inWorld());
        if (event.sample.kind == io.github.wdy.easytouch.touch.TouchSample.Kind.RESET) touchKeys.clear();
    }

    @SubscribeEvent
    public void syncKeyboard(InputEvent.KeyInputEvent event) {
        keys.sync(input.inWorld());
        io.github.wdy.easytouch.layout.TouchButtons.instance.syncKeyboard();
    }

    @SubscribeEvent
    public void syncMouse(InputEvent.MouseInputEvent event) {
        // Vanilla has already processed this real mouse event. Preserve any independent touch hold.
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
        if (!config.touchEnabled || !config.buttonsVisible || input.mc.gameSettings.hideGUI) movement.cancel();
        if (!config.touchEnabled || input.mc.gameSettings.hideGUI) hotbar.cancel();
        if (event.phase == TickEvent.Phase.START) {
            touchLook.tick(TouchInput.nanoTime());
        }
        keys.sync(input.inWorld());
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) { smartTouch.hud(event); }

    @SubscribeEvent
    public void wheel(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !config.touchEnabled
                || !config.buttonsVisible || !input.inWorld() || input.mc.gameSettings.hideGUI) return;
        MovementWheelHud.draw(input.mc, movement, event.resolution.getScaledWidth(),
            event.resolution.getScaledHeight());
    }
}
