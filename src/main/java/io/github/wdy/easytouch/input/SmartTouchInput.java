package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.Action;
import io.github.wdy.easytouch.TouchConfig;
import io.github.wdy.easytouch.matching.TargetResolver;
import io.github.wdy.easytouch.matching.MatcherRegistry.ToggleResult;
import io.github.wdy.easytouch.matching.MinecraftTargetContext;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Smart target selection for touch only, mode shortcuts, and the matching-target HUD. */
public final class SmartTouchInput {
    private final MinecraftInput input;
    private final Minecraft mc;
    private final TouchConfig config;
    private final TargetResolver resolver;
    private final Runnable resetInputs;
    private final KeyBinding toggle = new KeyBinding("key.easytouch.toggle", Keyboard.KEY_NONE, "key.categories.easytouch");
    private final KeyBinding toggleTarget = new KeyBinding("key.easytouch.toggleTarget", Keyboard.KEY_NONE,
        "key.categories.easytouch");

    public SmartTouchInput(MinecraftInput input, TouchConfig config, Runnable resetInputs) {
        this.input = input;
        this.mc = input.mc;
        this.config = config;
        this.resolver = new TargetResolver(config);
        this.resetInputs = resetInputs;
    }

    public void registerKey() {
        ClientRegistry.registerKeyBinding(toggle);
        ClientRegistry.registerKeyBinding(toggleTarget);
    }

    public void targetKeyInput() {
        if (toggleTarget.getKeyCode() > Keyboard.KEY_NONE && Keyboard.getEventKeyState()
                && !Keyboard.isRepeatEvent() && Keyboard.getEventKey() == toggleTarget.getKeyCode()) toggleTarget();
    }

    public void targetMouseInput() {
        if (toggleTarget.getKeyCode() < Keyboard.KEY_NONE && Mouse.getEventButtonState()
                && Mouse.getEventButton() >= 0 && Mouse.getEventButton() - 100 == toggleTarget.getKeyCode()) toggleTarget();
    }

    private void toggleTarget() {
        if (!input.inWorld()) return;
        ToggleResult result = config.matchers.toggleTarget(new MinecraftTargetContext(mc));
        if (result == null) {
            mc.thePlayer.addChatMessage(new ChatComponentTranslation("easytouch.target.unavailable"));
            return;
        }
        // Keep the ongoing gesture's mapping locked; the changed rule applies to the next press.
        ChatComponentTranslation feedback = new ChatComponentTranslation(
            result.match ? "easytouch.target.enabled" : "easytouch.target.disabled", result.identity.displayName());
        if (!config.enabled) feedback.appendSibling(new ChatComponentTranslation("easytouch.target.globalOff"));
        mc.thePlayer.addChatMessage(feedback);
    }

    /** Resolve once when a touch click/hold actually starts, then lock it until release. */
    public Action touchPrimary() {
        return config.enabled && !mc.thePlayer.isSneaking()
            ? resolver.resolve(mc) : Action.ATTACK;
    }

    public void tickEnd() {
        // This shortcut uses individual input events, not queued/repeated tick presses.
        while (toggleTarget.isPressed()) { }
        while (toggle.isPressed()) {
            if (input.inWorld()) {
                resetInputs.run();
                config.toggle();
                mc.thePlayer.addChatMessage(new ChatComponentTranslation(
                    config.enabled ? "easytouch.enabled" : "easytouch.disabled"));
            }
        }
    }

    public void hud(RenderGameOverlayEvent.Text event) {
        if (!config.touchEnabled || !config.enabled || !config.hud || !input.inWorld() || mc.gameSettings.hideGUI) return;
        if (mc.thePlayer.isSneaking() || resolver.resolve(mc) != Action.USE) return;
        String text = I18n.format("easytouch.hud");
        mc.fontRenderer.drawStringWithShadow(text,
            (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(text)) / 2,
            event.resolution.getScaledHeight() / 2 + 16, 0xFFFFFF);
    }
}
