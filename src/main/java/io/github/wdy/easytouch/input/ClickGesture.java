package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.touch.TouchSample;

/** Shared single-contact clicks and holds. Extra contacts never select a mouse button. */
public final class ClickGesture {
    public interface Port {
        void move(float x, float y, float dx, float dy);
        void button(int button, boolean down);
        default boolean immediate() { return false; }
    }
    public static final long DOUBLE_NS = 250_000_000L;
    private final Port port;
    private final boolean relative;
    private TouchSample down, pending;
    private int width = 1, height = 1, button;
    private float x, y;
    private boolean held, moved, immediate;

    public ClickGesture(Port port, boolean relative) { this.port = port; this.relative = relative; }
    public boolean held() { return held; }
    public boolean active() { return down != null; }
    public boolean awaitingSecond() { return pending != null; }
    private boolean near(TouchSample a, TouchSample b) {
        float dx = (a.x-b.x)*width, dy = (a.y-b.y)*height;
        float slop = Math.max(6, Math.min(width,height)*.015f);
        return dx*dx+dy*dy <= slop*slop;
    }
    private void press() { held = true; port.button(button,true); }
    private void release() { if (held) port.button(button,false); held = false; }
    private void flush() {
        TouchSample p = pending; pending = null;
        if (p != null) {
            if (!relative) port.move(p.x,p.y,0,0);
            port.button(0,true); port.button(0,false);
        }
    }
    public void cancel() { release(); down = pending = null; moved = immediate = false; }
    public void tick(long now) {
        if (pending != null && now-pending.timestamp >= DOUBLE_NS) flush();
        if (down != null && !held && !moved && now-down.timestamp >= TouchTiming.HOLD_NS) press();
    }
    public void accept(TouchSample s, int w, int h) {
        if (s.kind == TouchSample.Kind.RESET) { cancel(); return; }
        if (s.simulated() || !s.valid()) return;
        width = Math.max(1,w); height = Math.max(1,h);
        if (s.kind == TouchSample.Kind.DOWN) {
            if (down != null) return;
            tick(s.timestamp);
            boolean twice = pending != null && s.deviceId == pending.deviceId && near(s,pending);
            if (!twice) flush(); else pending = null;
            down = s; x = s.x; y = s.y; moved = false; button = twice ? 1 : 0;
            immediate = port.immediate();
            if (!relative) port.move(x,y,0,0);
            if (immediate) { button = 0; port.button(0,true); port.button(0,false); }
            return;
        }
        if (down == null || s.deviceId != down.deviceId || s.fingerId != down.fingerId) return;
        if (s.kind == TouchSample.Kind.CANCEL) { cancel(); return; }
        if (s.kind != TouchSample.Kind.MOVE && s.kind != TouchSample.Kind.UP) return;
        tick(s.timestamp);
        if (!near(s,down)) {
            if (!moved && !relative && !held) press();
            moved = true;
        }
        if (moved) port.move(s.x,s.y,s.x-x,s.y-y);
        x = s.x; y = s.y;
        if (s.kind == TouchSample.Kind.UP) {
            if (!held && !moved && !immediate) {
                if (button == 1) { port.button(1,true); port.button(1,false); }
                else pending = s;
            }
            release(); down = null;
        }
    }
}
