package io.github.wdy.easytouch.input;

import net.minecraft.client.Minecraft;
import io.github.wdy.easytouch.layout.Circle;

/** Uses the same scaled coordinates for drawing and native-touch hit testing. */
public final class MovementWheelHud {
    private MovementWheelHud() { }

    public static void draw(Minecraft mc, TouchMovement movement, int width, int height) {
        if (io.github.wdy.easytouch.layout.TouchButtons.instance.mouseControlActive()) return;
        if (!movement.active()) return;
        int x = Math.round(movement.centerX());
        int y = Math.round(movement.centerY());
        int r = Math.round(TouchMovement.radius(width, height));
        disk(x, y, r, 0x30708090);
        disk(x, y, r - 2, 0x30202730);
        io.github.wdy.easytouch.layout.Circle.ring(x-r, y-r, r*2, 0x779AB5C8);
        disk(x + movement.horizontal() * r / 4, y + movement.vertical() * r / 4,
            Math.max(3, r / 7), 0x60B4C9DD);
    }

    private static void disk(int x, int y, int radius, int color) {
        Circle.draw(x-radius,y-radius,radius*2,color);
    }
}
