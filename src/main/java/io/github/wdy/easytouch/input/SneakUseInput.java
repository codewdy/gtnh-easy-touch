package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.input.KeyStateMerger.Key;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Native sneak-use shortcut. It has no dependency on smart mode, mouse mappings, or target matchers. */
public final class SneakUseInput {
    private final MinecraftInput input;
    private final Minecraft mc;
    private final KeyStateMerger.Source keys;
    private final SneakUseGesture gesture = new SneakUseGesture();
    private final KeyBinding key = new KeyBinding("key.easytouch.sneak_use", Keyboard.KEY_NONE, "key.categories.easytouch");
    private EntityClientPlayerMP playerAtTickStart;
    private int playerTickAtStart;

    public SneakUseInput(MinecraftInput input, KeyStateMerger.Source keys) {
        this.input = input;
        this.mc = input.mc;
        this.keys = keys;
    }

    public void registerKey() { ClientRegistry.registerKeyBinding(key); }

    public void input() {
        boolean pressed = false;
        while (key.isPressed()) pressed = true;
        if (input.inWorld()) gesture.updateKey(pressed, GameSettings.isKeyDown(key));
        else gesture.cancel();
        publish();
    }

    private void publish() {
        keys.hold(Key.SNEAK, gesture.holdsSneak());
        keys.hold(Key.USE, gesture.holdsUse());
    }

    public void cancel() {
        gesture.cancel();
        playerAtTickStart = null;
        while (key.isPressed()) { }
        keys.clear();
    }

    public void tickStart() {
        input();
        playerAtTickStart = input.inWorld() ? mc.thePlayer : null;
        if (playerAtTickStart == null) return;
        playerTickAtStart = playerAtTickStart.ticksExisted;
        if (gesture.beginTick(playerAtTickStart.isSneaking())) keys.click(Key.USE);
        publish();
    }

    public void tickEnd() {
        input();
        if (!input.inWorld()) return;
        // ClientTick END follows the player's normal sneak packet; PlayerTick END is too early.
        boolean sneakUpdated = mc.thePlayer == playerAtTickStart
            && mc.thePlayer.ticksExisted > playerTickAtStart
            && !mc.thePlayer.isRiding() && mc.thePlayer.isSneaking();
        gesture.endTick(sneakUpdated);
        publish();
    }
}
