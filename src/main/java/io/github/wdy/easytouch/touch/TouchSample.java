package io.github.wdy.easytouch.touch;

/** An owned copy of a native event. Never retain SDL pointers beyond the callback. */
public final class TouchSample {
    public enum Kind { DOWN, MOVE, UP, CANCEL, RESET }
    public final Kind kind;
    public final long deviceId, fingerId, timestamp;
    public final int windowId;
    public final float x, y, dx, dy, pressure;

    public TouchSample(Kind kind, long deviceId, long fingerId, long timestamp, int windowId,
        float x, float y, float dx, float dy, float pressure) {
        this.kind = kind;
        this.deviceId = deviceId;
        this.fingerId = fingerId;
        this.timestamp = timestamp;
        this.windowId = windowId;
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.pressure = pressure;
    }

    /** SDL_MOUSE_TOUCHID: a mouse converted to touch is not evidence of native multitouch. */
    public boolean simulated() { return deviceId == -1L; }

    public boolean valid() {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(dx)
            && Float.isFinite(dy) && Float.isFinite(pressure);
    }

    public static TouchSample reset() {
        return new TouchSample(Kind.RESET, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
