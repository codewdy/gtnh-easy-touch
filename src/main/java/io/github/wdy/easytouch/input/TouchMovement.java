package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.touch.TouchSample;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;

/** A fixed left-hand wheel owns its contact until release, even outside its bounds. */
public final class TouchMovement {
    private final KeyStateMerger.Source keys;
    private boolean active;
    private long device, finger;
    private float centerX, centerY, radius, width, height;
    private int horizontal, vertical;

    public TouchMovement(KeyStateMerger.Source keys) { this.keys = keys; }

    public static float radius(int width, int height) {
        return Math.min(48f, Math.min(width * .18f, height * .22f));
    }
    public static float centerX(int width, int height) { return radius(width, height) + 12; }
    public static float centerY(int width, int height) { return height - radius(width, height) - 36; }
    public int horizontal() { return horizontal; }
    public int vertical() { return vertical; }

    public void cancel() {
        active = false;
        horizontal = vertical = 0;
        keys.clear();
    }

    /** Returns true for contacts reserved for movement; other contacts remain available for looking. */
    public boolean accept(TouchSample sample, int w, int h, boolean enabled) {
        if (!enabled || sample.kind == TouchSample.Kind.RESET) {
            cancel();
            return false;
        }
        if (sample.simulated() || !sample.valid()) return false;
        if (!active && sample.kind == TouchSample.Kind.DOWN) {
            float r = radius(w, h);
            float dx = sample.x * w - centerX(w, h), dy = sample.y * h - centerY(w, h);
            if (dx * dx + dy * dy > r * r) return false;
            active = true;
            device = sample.deviceId;
            finger = sample.fingerId;
            width = w;
            height = h;
            centerX = centerX(w, h);
            centerY = centerY(w, h);
            radius = r;
        }
        if (!active || sample.deviceId != device || sample.fingerId != finger) return false;
        if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) {
            cancel();
            return true;
        }
        float dx = sample.x * width - centerX, dy = sample.y * height - centerY;
        horizontal = vertical = 0;
        if (dx * dx + dy * dy > radius * radius * .04f) {
            // 22.5 degree sector boundaries provide four cardinal and four diagonal directions.
            if (Math.abs(dx) > Math.abs(dy) * .41421356f) horizontal = dx < 0 ? -1 : 1;
            if (Math.abs(dy) > Math.abs(dx) * .41421356f) vertical = dy < 0 ? -1 : 1;
        }
        keys.clear();
        if (horizontal < 0) keys.hold(Key.LEFT);
        if (horizontal > 0) keys.hold(Key.RIGHT);
        if (vertical < 0) keys.hold(Key.FORWARD);
        if (vertical > 0) keys.hold(Key.BACK);
        return true;
    }
}
