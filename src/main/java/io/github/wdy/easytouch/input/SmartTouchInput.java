package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.Action;
import io.github.wdy.easytouch.TouchConfig;
import io.github.wdy.easytouch.matching.TargetResolver;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/** Smart target selection for touch only, the matching-target HUD. */
public final class SmartTouchInput {
    private final MinecraftInput input;
    private final Minecraft mc;
    private final TouchConfig config;
    private final TargetResolver resolver;
    public SmartTouchInput(MinecraftInput input, TouchConfig config) {
        this.input = input;
        this.mc = input.mc;
        this.config = config;
        this.resolver = new TargetResolver(config);
    }

    /** Resolve the action for a smart tap or secondary button; primary-mode world long presses always attack. */
    public Action touchPrimary() {
        return config.enabled && !mc.thePlayer.isSneaking()
            ? resolver.resolve(mc) : Action.ATTACK;
    }

    public Action touchTap() {
        return touchPrimary();
    }

    /** Sneaking or disabled smart makes primary ATTACK, so secondary is always USE. */
    public Action touchSecondary() {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                || mc.gameSettings.keyBindSneak.getIsKeyPressed()) return Action.USE;
        return touchPrimary().opposite();
    }

    public void hud(RenderGameOverlayEvent.Text event) {
        if (!config.touchEnabled || !config.enabled || !config.hud || !input.inWorld() || mc.gameSettings.hideGUI) return;
        if (touchTap() != Action.USE) return;
        String text = I18n.format("easytouch.hud");
        mc.fontRenderer.drawStringWithShadow(text,
            (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(text)) / 2,
            event.resolution.getScaledHeight() / 2 + 16, 0xFFFFFF);
    }
}
