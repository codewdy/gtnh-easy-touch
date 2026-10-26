package io.github.wdy.easytouch.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.opengl.Display;

/** Minecraft environment and attack/use key I/O, independent of mouse mapping policy. */
public final class MinecraftInput implements KeyStateMerger.Port {
    public final Minecraft mc;

    public MinecraftInput(Minecraft mc) { this.mc = mc; }

    public boolean inWorld() {
        return mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null
            && mc.inGameHasFocus && Display.isActive() && !mc.thePlayer.isDead;
    }

    public void turn(float dx, float dy) {
        if (!inWorld()) return;
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float scale = sensitivity * sensitivity * sensitivity * 8.0F;
        // SDL Y grows downwards; setAngles expects upwards-positive mouse movement.
        mc.thePlayer.setAngles(dx * mc.displayWidth * scale,
            dy * mc.displayHeight * scale * (mc.gameSettings.invertMouse ? 1 : -1));
    }

    public boolean mouseButtonDown(int button) {
        return io.github.wdy.easytouch.touch.TouchInput.mouseButtonDown(button);
    }

    private KeyBinding binding(KeyStateMerger.Key key) {
        switch (key) {
            case ATTACK: return mc.gameSettings.keyBindAttack;
            case USE: return mc.gameSettings.keyBindUseItem;
            default: throw new AssertionError(key);
        }
    }

    @Override public boolean physicalDown(KeyStateMerger.Key key) {
        int code = binding(key).getKeyCode();
        return code < 0 ? mouseButtonDown(code + 100) : GameSettings.isKeyDown(binding(key));
    }

    @Override public void setDown(KeyStateMerger.Key key, boolean down) {
        KeyBinding.setKeyBindState(binding(key).getKeyCode(), down);
    }

    @Override public void click(KeyStateMerger.Key key) {
        KeyBinding.onTick(binding(key).getKeyCode());
    }

    @Override public void clearClicks(KeyStateMerger.Key key) {
        while (binding(key).isPressed()) { }
    }
}
