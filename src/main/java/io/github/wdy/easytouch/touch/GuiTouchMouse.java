package io.github.wdy.easytouch.touch;

import io.github.wdy.easytouch.input.TouchMouse;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDL_MouseMotionEvent;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;

/** Injects normal GUI mouse events through lwjgl3ify, without sending them back into SDL. */
@Lwjgl3Aware
public final class GuiTouchMouse implements TouchMouse.Port {
    private final Minecraft mc;

    public GuiTouchMouse(Minecraft mc) { this.mc = mc; }
    @Override public boolean active() { return Display.isActive() && mc.currentScreen != null; }

    @Override public void move(float x, float y) {
        // addMoveEvent expects logical window pixels and applies the DPI scale itself.
        float scale = Display.getPixelScaleFactor();
        try (SDL_MouseMotionEvent motion = SDL_MouseMotionEvent.calloc()) {
            motion.x(Math.max(0, Math.min(mc.displayWidth - 1, x * mc.displayWidth)) / scale);
            motion.y(Math.max(0, Math.min(mc.displayHeight - 1, y * mc.displayHeight)) / scale);
            Mouse.addMoveEvent(motion);
            Mouse.poll();
        }
    }

    @Override public void button(boolean down) { if (down || !TouchInput.mouseButtonDown(0) && !io.github.wdy.easytouch.layout.TouchButtons.instance.mouseHeld(0)) Mouse.addButtonEvent(0, down); }
}
