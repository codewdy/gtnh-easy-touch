package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.touch.TouchSample;

/** One native contact owns a tap, action hold, or look drag; dragging preserves an active hold. Times use SDL's nanosecond clock. */
public final class TouchLook {
    public interface Port {
        boolean inWorld();
        int width();
        int height();
        void turn(float dx, float dy);
        void click();
        void press();
        void release();
    }

    private static final long TAP_NS = 250_000_000L;
    private final Port port;
    private boolean active, dragging, holding;
    private long deviceId, fingerId, started;
    private float startX, startY, x, y, width, height, threshold;

    public TouchLook(Port port) { this.port = port; }

    public void cancel() {
        if (holding) port.release();
        active = dragging = holding = false;
    }

    public void tick(long now) {
        if (!port.inWorld()) cancel();
        else if (active && !dragging && !holding && now - started >= TouchTiming.HOLD_NS) {
            holding = true;
            port.press();
        }
    }

    public void accept(TouchSample sample) {
        if (!port.inWorld() || sample.kind == TouchSample.Kind.RESET) {
            cancel();
            return;
        }
        if (sample.simulated() || !sample.valid()) return;
        if (sample.kind == TouchSample.Kind.DOWN && !active) {
            active = true;
            deviceId = sample.deviceId;
            fingerId = sample.fingerId;
            started = sample.timestamp;
            startX = x = sample.x;
            startY = y = sample.y;
            width = Math.max(1, port.width());
            height = Math.max(1, port.height());
            threshold = Math.min(width, height) * .01f;
            return;
        }
        if (!active || deviceId != sample.deviceId || fingerId != sample.fingerId) return;
        if (sample.kind == TouchSample.Kind.CANCEL) {
            cancel();
            return;
        }
        if (sample.kind != TouchSample.Kind.MOVE && sample.kind != TouchSample.Kind.UP) return;
        float dx = (sample.x - startX) * width;
        float dy = (sample.y - startY) * height;
        if (!dragging && dx * dx + dy * dy > threshold * threshold) {
            dragging = true;
        }
        if (dragging) {
            // Include the initial dead-zone displacement once, then apply incremental motion.
            if (sample.x != x || sample.y != y) port.turn(sample.x - x, sample.y - y);
            x = sample.x;
            y = sample.y;
        }
        if (sample.kind == TouchSample.Kind.UP) {
            long duration = sample.timestamp - started;
            // A whole stationary hold can arrive in one batch after a stalled frame.
            if (!dragging && !holding && duration >= 0) {
                if (duration >= TouchTiming.HOLD_NS) {
                    holding = true;
                    port.press();
                } else if (duration <= TAP_NS) {
                    port.click();
                }
            }
            cancel();
        }
    }
}
