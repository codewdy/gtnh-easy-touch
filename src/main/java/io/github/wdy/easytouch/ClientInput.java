package io.github.wdy.easytouch;

import io.github.wdy.easytouch.matching.TargetResolver;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public final class ClientInput {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final TouchConfig config;
    private final TargetResolver resolver;
    private final Gesture gesture = new Gesture();
    private final KeyBinding toggle = new KeyBinding("key.easytouch.toggle", Keyboard.KEY_NONE, "key.categories.easytouch");
    private boolean waitForRelease;

    private ClientInput(TouchConfig config) {
        this.config = config;
        resolver = new TargetResolver(config);
    }

    public static void install(TouchConfig config) {
        ClientInput input = new ClientInput(config);
        ClientRegistry.registerKeyBinding(input.toggle);
        MinecraftForge.EVENT_BUS.register(input);
        FMLCommonHandler.instance().bus().register(input);
    }

    private boolean inWorld() {
        return mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null
            && mc.inGameHasFocus && Display.isActive() && !mc.thePlayer.isDead;
    }

    private boolean defaultButtons() {
        return mc.gameSettings.keyBindAttack.getKeyCode() == -100
            && mc.gameSettings.keyBindUseItem.getKeyCode() == -99;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void mouse(MouseEvent event) {
        if (event.button < 0 || event.button > 1) return;
        if (!inWorld() || !defaultButtons()) {
            reset();
            return;
        }
        if (waitForRelease) {
            event.setCanceled(true);
            return;
        }
        if (!config.enabled) return;
        event.setCanceled(true);
        if (event.buttonstate) {
            if (!gesture.isDown(event.button)) {
                Action primary = mc.thePlayer.isSneaking() ? Action.ATTACK : resolver.resolve(mc);
                Action action = gesture.press(event.button, primary);
                KeyBinding.onTick(binding(action).getKeyCode());
            }
        } else {
            gesture.release(event.button);
        }
        sync();
    }

    private KeyBinding binding(Action action) {
        return action == Action.ATTACK ? mc.gameSettings.keyBindAttack : mc.gameSettings.keyBindUseItem;
    }

    private void sync() {
        for (Action action : Action.values()) {
            KeyBinding.setKeyBindState(binding(action).getKeyCode(), gesture.holds(action));
        }
    }

    private void reset() {
        if (!gesture.active()) return;
        gesture.reset();
        sync();
        // Drop queued clicks as well as held state on a GUI/focus/world boundary.
        for (Action action : Action.values()) while (binding(action).isPressed()) { }
        waitForRelease = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void gui(GuiOpenEvent event) {
        if (event.gui != null) reset();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) reset();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (!inWorld() || !config.enabled || !defaultButtons()) reset();
        if (event.phase != TickEvent.Phase.END) return;
        while (toggle.isPressed()) {
            if (mc.currentScreen == null && mc.thePlayer != null) {
                reset();
                config.toggle();
                waitForRelease = Mouse.isButtonDown(0) || Mouse.isButtonDown(1);
                mc.thePlayer.addChatMessage(new ChatComponentTranslation(
                    config.enabled ? "easytouch.enabled" : "easytouch.disabled"));
            }
        }
        if (!inWorld() || !config.enabled || !defaultButtons()) reset();
        // Recover a missed release (e.g. alt-tab) without creating a new press on returning.
        if (gesture.active()) {
            for (int button = 0; button < 2; button++) {
                if (gesture.isDown(button) && !Mouse.isButtonDown(button)) gesture.release(button);
            }
            sync();
        }
        if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) waitForRelease = false;
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) {
        if (!config.enabled || !config.hud || !inWorld() || mc.gameSettings.hideGUI || !defaultButtons()) return;
        if (mc.thePlayer.isSneaking()) return;
        // The hint follows the current target; an active gesture still keeps its locked button mapping.
        if (resolver.resolve(mc) != Action.USE) return;
        String text = I18n.format("easytouch.hud");
        mc.fontRenderer.drawStringWithShadow(text,
            (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(text)) / 2,
            event.resolution.getScaledHeight() / 2 + 16, 0xFFFFFF);
    }
}
