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
        void secondaryClick();
        void press();
        void release();
    }

    private static final long TAP_NS = 250_000_000L;
    private final Port port;
    private boolean active, dragging, holding, paired, secondActive, secondMoved;
    private long secondDevice, secondFinger, secondStarted;
    private float secondX, secondY;
    private long deviceId, fingerId, started;
    private float startX, startY, x, y, width, height, threshold;

    public TouchLook(Port port) { this.port = port; }

    public void cancel() {
        if (holding) port.release();
        active = dragging = holding = paired = secondActive = false;
    }

    public void tick(long now) {
        if (!port.inWorld()) cancel();
        else if (active && !paired && !dragging && !holding && now - started >= TouchTiming.HOLD_NS) {
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
        if (secondActive && sample.deviceId == secondDevice && sample.fingerId == secondFinger) {
            float dx = (sample.x - secondX) * width, dy = (sample.y - secondY) * height;
            if (dx * dx + dy * dy > threshold * threshold) secondMoved = true;
            if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) {
                long duration = sample.timestamp - secondStarted;
                secondActive = false;
                if (sample.kind == TouchSample.Kind.UP && !secondMoved && duration >= 0 && duration <= TAP_NS)
                    port.secondaryClick();
            }
            return;
        }
        if (sample.kind == TouchSample.Kind.DOWN && active
                && (sample.deviceId != deviceId || sample.fingerId != fingerId)) {
            if (!secondActive) {
                secondActive = true;
                secondMoved = false;
                paired = true;
                secondDevice = sample.deviceId;
                secondFinger = sample.fingerId;
                secondStarted = sample.timestamp;
                secondX = sample.x;
                secondY = sample.y;
            }
            return;
        }
        // Wait for the remaining contact before starting another gesture.
        if (!active && secondActive) return;
        if (sample.kind == TouchSample.Kind.DOWN && !active) {
            paired = false;
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
            if (!paired && !dragging && !holding && duration >= 0) {
                if (duration >= TouchTiming.HOLD_NS) {
                    holding = true;
                    port.press();
                } else if (duration <= TAP_NS) {
                    port.click();
                }
            }
            if (holding) port.release();
            active = dragging = holding = false;
        }
    }
}
