package io.github.wdy.easytouch.input;

import java.util.HashMap;
import java.util.Map;
import io.github.wdy.easytouch.touch.TouchSample;

/** Explicit touchpad states. A gesture owns at most one mouse button, and never scrolls. */
public final class TouchpadGestures {
    public interface Port {
        void move(float dx, float dy);
        void click(int button);
        void button(int button, boolean down);
    }
    public enum State { IDLE, SINGLE_PENDING, POINTER, LEFT_DRAG, RIGHT_DRAG, RIGHT_HELD, WAIT_CLEAR }
    private static final long TAP_NS = 300_000_000L;
    private static final float SLOP = 6;
    private static final class Contact {
        final float startX, startY;
        float x, y;
        Contact(TouchSample s) { startX=x=s.x; startY=y=s.y; }
        boolean moved(int w,int h) {
            float dx=(x-startX)*w, dy=(y-startY)*h;
            return dx*dx+dy*dy>SLOP*SLOP;
        }
    }
    private final Port port;
    private final Map<String, Contact> contacts = new HashMap<String, Contact>();
    private State state=State.IDLE;
    private long started;
    private int held=-1;

    public TouchpadGestures(Port port) { this.port=port; }
    public State state() { return state; }
    public boolean hasContacts() { return !contacts.isEmpty(); }
    private void release() { if (held>=0) { port.button(held,false); held=-1; } }
    public void cancel() { release(); contacts.clear(); state=State.IDLE; }
    private void waitClear() { release(); state=State.WAIT_CLEAR; }
    private void drag(int button,State next) { held=button; state=next; port.button(button,true); }
    private boolean heldLongEnough(long now) { return now>=started && now-started>=TouchTiming.HOLD_NS; }
    private boolean quick(long now) { return now>=started && now-started<=TAP_NS; }

    public void accept(TouchSample s, int width, int height) {
        if (s.kind==TouchSample.Kind.RESET) { cancel(); return; }
        if (s.simulated() || !s.valid()) return;
        String id=s.deviceId+":"+s.fingerId;
        if (s.kind==TouchSample.Kind.DOWN) {
            if (contacts.containsKey(id)) return;
            contacts.put(id,new Contact(s));
            if (state==State.IDLE) {
                started=s.timestamp; state=State.SINGLE_PENDING;
            } else if (contacts.size()==2 && (state==State.SINGLE_PENDING
                    || state==State.POINTER || state==State.LEFT_DRAG)) {
                // A second finger immediately takes over with right drag, never both buttons.
                release();
                drag(1,State.RIGHT_DRAG);
            } else if (state==State.RIGHT_HELD && contacts.size()==2) {
                // Either finger can be replaced. DOWN only establishes its movement origin.
                state=State.RIGHT_DRAG;
            } else waitClear(); // Third fingers cancel rather than inventing another gesture.
            return;
        }
        Contact c=contacts.get(id);
        if (c==null) return;
        float dx=s.x-c.x, dy=s.y-c.y;
        c.x=s.x; c.y=s.y;
        boolean moved=c.moved(width,height);
        if (s.kind==TouchSample.Kind.CANCEL) waitClear();
        else if (s.kind==TouchSample.Kind.MOVE) {
            switch (state) {
                case SINGLE_PENDING:
                    if (moved) {
                        if (heldLongEnough(s.timestamp)) drag(0,State.LEFT_DRAG);
                        else state=State.POINTER;
                        port.move(c.x-c.startX,c.y-c.startY);
                    }
                    break;
                case POINTER:
                case LEFT_DRAG:
                    port.move(dx,dy);
                    break;
                case RIGHT_DRAG:
                    // Both contacts independently contribute relative motion while two are down.
                    port.move(dx,dy);
                    break;
                default: break;
            }
        }
        if (s.kind==TouchSample.Kind.UP || s.kind==TouchSample.Kind.CANCEL) {
            if (s.kind==TouchSample.Kind.UP) {
                if (state==State.SINGLE_PENDING && !moved && quick(s.timestamp)) port.click(0);
                if (state==State.LEFT_DRAG) waitClear();
            }
            contacts.remove(id);
            if (contacts.isEmpty()) cancel();
            else if (state==State.RIGHT_DRAG) state=State.RIGHT_HELD;
        }
    }
}
