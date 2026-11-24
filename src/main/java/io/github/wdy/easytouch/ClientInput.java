package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.AutoStep;
import io.github.wdy.easytouch.input.MinecraftInput;
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
    private final AutoStep autoStep = new AutoStep();
    private final io.github.wdy.easytouch.input.AutoAttack autoAttack = new io.github.wdy.easytouch.input.AutoAttack();
    private final TouchConfig config;
    private final TouchLook touchLook;
    private final KeyStateMerger.Source touchKeys = keys.newSource();

    private ClientInput(TouchConfig config) {
        this.config = config;
        new io.github.wdy.easytouch.layout.TouchButtons(config, keys.newSource(), () -> { cancelInputs(); keys.sync(input.inWorld()); },
            (dx, dy) -> input.turn(dx * config.touchDragSensitivityX, dy * config.touchDragSensitivityY));
        final TouchActions actions = new TouchActions(touchKeys);
        touchLook = new TouchLook(new TouchLook.Port() {
            public boolean inWorld() { return input.inWorld(); }
            public int width() { return input.mc.displayWidth; }
            public int height() { return input.mc.displayHeight; }
            public void turn(float dx, float dy) {
                input.turn(dx * config.touchDragSensitivityX, dy * config.touchDragSensitivityY);
            }
            public void button(int button, boolean down) {
                if (button == 0 && down && config.autoTool && input.inWorld())
                    io.github.wdy.easytouch.input.AutoTool.select(input.mc);
                actions.button(button, down);
            }
            public Object miningTarget() { return io.github.wdy.easytouch.input.WorldHold.target(input.mc); }
            public long holdNanos() { return io.github.wdy.easytouch.input.WorldHold.delay(input.mc, config.autoTool); }
            public boolean holdingUseItem() { return io.github.wdy.easytouch.input.HeldItemUse.matches(input.mc.thePlayer == null ? null : input.mc.thePlayer.getHeldItem()); }
        });
    }

    public static void install(TouchConfig config) {
        ClientInput client = new ClientInput(config);
        io.github.wdy.easytouch.guide.WorldGuideHotkey.install();
        io.github.wdy.easytouch.touch.ModularUiTouchInput.install();
        io.github.wdy.easytouch.smeltery.SmelteryTouch.install();
        io.github.wdy.easytouch.touch.VendingTouch.install();
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

    public static void installNeiTouch() {
        if (!Loader.isModLoaded("NotEnoughItems")) return;
        try {
            io.github.wdy.easytouch.nei.NeiTouchMenu.install();
            io.github.wdy.easytouch.nei.WorldUsageHotkey.install();
        } catch (RuntimeException | LinkageError error) {
            FMLLog.log(Level.WARN, error, "[GTNH Easy Touch] Cannot load NEI touch controls.");
        }
    }

    private void cancelInputs() {
        autoStep.restore();
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
                config.touchEnabled && input.inWorld())) {
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
        if (!config.touchEnabled) movement.cancel();
        if (!config.touchEnabled || input.mc.gameSettings.hideGUI) hotbar.cancel();
        if (event.phase == TickEvent.Phase.START) {
            autoAttack.tick(input.mc, input.inWorld());
            touchLook.tick(TouchInput.nanoTime());
            keys.sync(input.inWorld());
            if (config.autoTool && input.inWorld() && touchLook.mining())
                io.github.wdy.easytouch.input.AutoTool.select(input.mc);
        }
        keys.sync(input.inWorld());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerTick(TickEvent.PlayerTickEvent event) {
        // Integrated-server and remote players must keep their own movement rules.
        if (event.player != input.mc.thePlayer) return;
        if (event.phase == TickEvent.Phase.END) {
            autoStep.restore();
        } else {
            autoStep.begin(input.mc.thePlayer, config.autoStep && input.inWorld()
                && !input.mc.gameSettings.keyBindSneak.getIsKeyPressed()
                && (input.mc.thePlayer.isInWater() || !input.mc.gameSettings.keyBindJump.getIsKeyPressed()),
                (input.mc.gameSettings.keyBindForward.getIsKeyPressed() ? 1 : 0)
                    - (input.mc.gameSettings.keyBindBack.getIsKeyPressed() ? 1 : 0),
                (input.mc.gameSettings.keyBindLeft.getIsKeyPressed() ? 1 : 0)
                    - (input.mc.gameSettings.keyBindRight.getIsKeyPressed() ? 1 : 0));
        }
    }


    @SubscribeEvent
    public void holdProgress(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !config.touchEnabled
                || !input.inWorld() || input.mc.gameSettings.hideGUI) return;
        float progress = touchLook.holdProgress(TouchInput.nanoTime());
        if (progress < 0) return;
        boolean hasTarget = io.github.wdy.easytouch.input.HeldItemUse.matches(input.mc.thePlayer.getHeldItem())
            || io.github.wdy.easytouch.input.WorldHold.target(input.mc) != null;
        int size = 22, x = event.resolution.getScaledWidth()/2-size/2;
        int y = event.resolution.getScaledHeight()/2-size/2;
        io.github.wdy.easytouch.layout.Circle.ring(x,y,size,hasTarget ? 0x90202730 : 0x706F7780);
        if (hasTarget) io.github.wdy.easytouch.layout.Circle.progressRing(x,y,size,
            0xE0ECF4FA,progress);
        int slot = hasTarget ? io.github.wdy.easytouch.input.WorldHold.slot(input.mc, config.autoTool) : -1;
        net.minecraft.item.ItemStack stack = slot < 0 ? null : input.mc.thePlayer.inventory.getStackInSlot(slot);
        if (!hasTarget) {
            // Draw the invalid-target cross inside the same 16x16 area as item previews.
            int iconX = x + size + 4, iconY = y + 3;
            for (int i = 3; i < 13; i++) {
                net.minecraft.client.gui.Gui.drawRect(iconX + i, iconY + i,
                    iconX + i + 2, iconY + i + 2, 0xE0FF5555);
                net.minecraft.client.gui.Gui.drawRect(iconX + 15 - i, iconY + i,
                    iconX + 17 - i, iconY + i + 2, 0xE0FF5555);
            }
        } else if (stack != null) {
            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
            org.lwjgl.opengl.GL11.glPushMatrix();
            try {
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                new net.minecraft.client.renderer.entity.RenderItem().renderItemAndEffectIntoGUI(
                    input.mc.fontRenderer, input.mc.getTextureManager(), stack, x + size + 4, y + 3);
            } finally {
                org.lwjgl.opengl.GL11.glPopMatrix();
                org.lwjgl.opengl.GL11.glPopAttrib();
            }
        }
    }

    @SubscribeEvent
    public void wheel(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !config.touchEnabled
                || !config.buttonsVisible || !input.inWorld() || input.mc.gameSettings.hideGUI) return;
        MovementWheelHud.draw(input.mc, movement, event.resolution.getScaledWidth(),
            event.resolution.getScaledHeight());
    }
}
