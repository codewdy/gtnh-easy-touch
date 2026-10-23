package io.github.wdy.easytouch;

/** One mapping for the entire gesture, including overlapping buttons. */
public final class Gesture {
    private final boolean[] down = new boolean[2];
    private Action primary;

    public Action press(int button, Action suggested) {
        if (!active()) primary = suggested;
        down[button] = true;
        return button == 0 ? primary : primary.opposite();
    }

    public void release(int button) {
        down[button] = false;
        if (!active()) primary = null;
    }

    public boolean isDown(int button) { return down[button]; }
    public boolean active() { return down[0] || down[1]; }
    public Action primary() { return primary; }

    public boolean holds(Action action) {
        return primary != null && ((down[0] && primary == action) || (down[1] && primary.opposite() == action));
    }

    public void reset() {
        down[0] = down[1] = false;
        primary = null;
    }
}
