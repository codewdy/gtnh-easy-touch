package io.github.wdy.easytouch.input;

import net.minecraft.client.Minecraft;
import io.github.wdy.easytouch.layout.Circle;

/** Uses the same scaled coordinates for drawing and native-touch hit testing. */
public final class MovementWheelHud {
    private MovementWheelHud() { }

    public static void draw(Minecraft mc, TouchMovement movement, int width, int height) {
        if (io.github.wdy.easytouch.layout.TouchButtons.instance.mouseControlActive()) return;
        if (!io.github.wdy.easytouch.layout.TouchButtons.instance.layout.wheelVisible) return;
        int x = Math.round(TouchMovement.centerX(width, height));
        int y = Math.round(TouchMovement.centerY(width, height));
        int r = Math.round(TouchMovement.radius(width, height));
        disk(x, y, r, 0x30708090);
        disk(x, y, r - 2, 0x30202730);
        io.github.wdy.easytouch.layout.Circle.ring(x-r, y-r, r*2, 0x779AB5C8);
        int offset = Math.round(r * .65f);
        label(mc, "W", x, y - offset, movement.vertical() < 0);
        label(mc, "S", x, y + offset, movement.vertical() > 0);
        label(mc, "A", x - offset, y, movement.horizontal() < 0);
        label(mc, "D", x + offset, y, movement.horizontal() > 0);
        disk(x + movement.horizontal() * r / 4, y + movement.vertical() * r / 4,
            Math.max(3, r / 7), 0x60B4C9DD);
    }

    private static void label(Minecraft mc, String label, int x, int y, boolean held) {
        mc.fontRenderer.drawStringWithShadow(label, x - mc.fontRenderer.getStringWidth(label) / 2,
            y - 4, held ? 0x66FFAA : 0xFFFFFF);
    }

    private static void disk(int x, int y, int radius, int color) {
        Circle.draw(x-radius,y-radius,radius*2,color);
    }
}
