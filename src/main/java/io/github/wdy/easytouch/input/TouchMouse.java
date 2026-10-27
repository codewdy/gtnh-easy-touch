package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.touch.TouchSample;

/** Non-world touch behaves like one selected mouse button, with immediate press and normal GUI dragging. */
public final class TouchMouse {
    public interface Port {
        boolean active();
        void move(float x, float y);
        void button(boolean down);
    }

    private final Port port;
    private boolean held;
    private long device, finger;

    public TouchMouse(Port port) { this.port = port; }
    public boolean held() { return held; }

    public void cancel() {
        if (held) port.button(false);
        held = false;
    }

    public void accept(TouchSample sample) {
        if (!port.active() || sample.kind == TouchSample.Kind.RESET) {
            cancel();
            return;
        }
        if (sample.simulated() || !sample.valid()) return;
        if (sample.kind == TouchSample.Kind.DOWN && !held) {
            held = true;
            device = sample.deviceId;
            finger = sample.fingerId;
            port.move(sample.x, sample.y);
            port.button(true);
        } else if (held && device == sample.deviceId && finger == sample.fingerId) {
            if (sample.kind == TouchSample.Kind.MOVE) port.move(sample.x, sample.y);
            else if (sample.kind == TouchSample.Kind.UP) {
                port.move(sample.x, sample.y);
                cancel();
            } else if (sample.kind == TouchSample.Kind.CANCEL) cancel();
        }
    }
}
