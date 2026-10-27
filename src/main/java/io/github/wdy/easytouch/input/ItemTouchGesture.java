package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.touch.TouchSample;

/** One item contact becomes either hover, a stationary menu hold, or a native GUI drag. */
public final class ItemTouchGesture {
    public interface Port {
        void hover(float x, float y);
        void drag(TouchSample sample);
        void cancelDrag();
        void menu();
        default void tap(TouchSample down, TouchSample up) { }
    }

    private final Port port;
    private TouchSample down;
    private float width, height, threshold;
    private boolean dragging, holding;

    public ItemTouchGesture(Port port) { this.port = port; }
    public boolean dragging() { return dragging; }
    public boolean active() { return down != null; }

    public void start(TouchSample sample, int width, int height) {
        cancel();
        down = sample;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        threshold = Math.min(this.width, this.height) * .01f;
        port.hover(sample.x, sample.y);
    }

    public void tick(long now) {
        if (down != null && !dragging && !holding && now - down.timestamp >= TouchTiming.HOLD_NS) {
            holding = true;
            port.menu();
        }
    }

    public void accept(TouchSample sample) {
        if (sample.kind == TouchSample.Kind.RESET) { cancel(); return; }
        if (down == null || sample.simulated() || !sample.valid()
                || sample.deviceId != down.deviceId || sample.fingerId != down.fingerId) return;
        if (sample.kind == TouchSample.Kind.CANCEL) { cancel(); return; }
        if (sample.kind != TouchSample.Kind.MOVE && sample.kind != TouchSample.Kind.UP) return;
        float dx = (sample.x - down.x) * width, dy = (sample.y - down.y) * height;
        if (!holding && !dragging && dx * dx + dy * dy > threshold * threshold) {
            dragging = true;
            // Replay the press at the original item only once movement establishes a drag.
            port.drag(down);
        }
        if (dragging) port.drag(sample);
        else if (!holding) tick(sample.timestamp); // Handles a complete hold queued in one frame.
        if (sample.kind == TouchSample.Kind.UP) {
            if (down != null && !dragging && !holding) port.tap(down, sample);
            cancel();
        }
    }

    public void cancel() {
        boolean release = dragging;
        down = null;
        dragging = holding = false;
        if (release) port.cancelDrag();
    }
}
