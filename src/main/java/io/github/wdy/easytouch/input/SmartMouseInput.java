package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.Action;
import io.github.wdy.easytouch.TouchConfig;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;
import io.github.wdy.easytouch.matching.TargetResolver;
import io.github.wdy.easytouch.matching.MatcherRegistry.ToggleResult;
import io.github.wdy.easytouch.matching.MinecraftTargetContext;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Adaptive mouse buttons, their mode key, and the matching-target HUD. */
public final class SmartMouseInput {
    private final MinecraftInput input;
    private final Minecraft mc;
    private final TouchConfig config;
    private final TargetResolver resolver;
    private final KeyStateMerger.Source keys;
    private final Runnable resetInputs;
    private final MouseGesture gesture = new MouseGesture();
    private final KeyBinding toggle = new KeyBinding("key.easytouch.toggle", Keyboard.KEY_NONE, "key.categories.easytouch");
    private final KeyBinding toggleTarget = new KeyBinding("key.easytouch.toggleTarget", Keyboard.KEY_NONE,
        "key.categories.easytouch");
    private boolean waitForRelease;

    public SmartMouseInput(MinecraftInput input, TouchConfig config, KeyStateMerger.Source keys, Runnable resetInputs) {
        this.input = input;
        this.mc = input.mc;
        this.config = config;
        this.resolver = new TargetResolver(config);
        this.keys = keys;
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

    private boolean defaultButtons() {
        return mc.gameSettings.keyBindAttack.getKeyCode() == -100
            && mc.gameSettings.keyBindUseItem.getKeyCode() == -99;
    }

    public void mouse(MouseEvent event) {
        if (event.button < 0 || event.button > 1) return;
        if (!input.inWorld() || !defaultButtons()) {
            cancel();
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
                keys.click(action == Action.ATTACK ? Key.ATTACK : Key.USE);
            }
        } else gesture.release(event.button);
        publish();
    }

    /** Owns the physical buttons only while smart input is enabled or waiting for a safe release. */
    private void publish() {
        if (input.inWorld() && defaultButtons() && (config.enabled || waitForRelease)) {
            keys.override(Key.ATTACK, gesture.holds(Action.ATTACK));
            keys.override(Key.USE, gesture.holds(Action.USE));
        } else {
            keys.release(Key.ATTACK);
            keys.release(Key.USE);
        }
    }

    public void cancel() {
        if (gesture.active()) {
            gesture.reset();
            waitForRelease = true;
        }
        keys.clear();
        publish();
    }

    public void tickStart() {
        if (!input.inWorld() || !config.enabled || !defaultButtons()) cancel();
        else publish();
    }

    public void tickEnd() {
        // This shortcut uses individual input events, not queued/repeated tick presses.
        while (toggleTarget.isPressed()) { }
        while (toggle.isPressed()) {
            if (mc.currentScreen == null && mc.thePlayer != null) {
                resetInputs.run();
                config.toggle();
                waitForRelease = Mouse.isButtonDown(0) || Mouse.isButtonDown(1);
                mc.thePlayer.addChatMessage(new ChatComponentTranslation(
                    config.enabled ? "easytouch.enabled" : "easytouch.disabled"));
            }
        }
        if (!input.inWorld() || !config.enabled || !defaultButtons()) cancel();
        if (gesture.active()) {
            // Recover releases lost during a focus change without creating another press.
            for (int button = 0; button < 2; button++) {
                if (gesture.isDown(button) && !Mouse.isButtonDown(button)) gesture.release(button);
            }
        }
        if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) waitForRelease = false;
        publish();
    }

    public void hud(RenderGameOverlayEvent.Text event) {
        if (!config.enabled || !config.hud || !input.inWorld() || mc.gameSettings.hideGUI || !defaultButtons()) return;
        if (mc.thePlayer.isSneaking() || resolver.resolve(mc) != Action.USE) return;
        String text = I18n.format("easytouch.hud");
        mc.fontRenderer.drawStringWithShadow(text,
            (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(text)) / 2,
            event.resolution.getScaledHeight() / 2 + 16, 0xFFFFFF);
    }
}
