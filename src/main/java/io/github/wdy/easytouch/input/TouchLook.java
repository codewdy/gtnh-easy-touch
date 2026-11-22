package io.github.wdy.easytouch.input;
import io.github.wdy.easytouch.touch.TouchSample;
/** Right-hand world input: short taps click, slides turn, stationary holds retain their button. */
public final class TouchLook {
    public interface Port {
        boolean inWorld(); int width(); int height();
        void turn(float dx,float dy);
        void button(int button,boolean down);
        default boolean holdingUseItem() { return false; }
    }
    private final Port port;
    private final ClickGesture gesture;
    public TouchLook(Port port) {
        this.port=port;
        gesture=new ClickGesture(new ClickGesture.Port() {
            public void move(float x,float y,float dx,float dy) { port.turn(dx,dy); }
            public void button(int b,boolean down) { port.button(b,down); }
            public int holdButton() { return port.holdingUseItem() ? 1 : 0; }
            public long holdNanos() { return 300_000_000L; }
            public int tapButton() { return 1; }
        },true);
    }
    public float holdProgress(long now) {
        if (!port.inWorld()) return -1;
        float progress = gesture.holdProgress(now);
        return progress > .5f ? progress : -1;
    }
    public boolean held() { return gesture.held(); }
    public void cancel() { gesture.cancel(); }
    public void tick(long now) { if (!port.inWorld()) cancel(); else gesture.tick(now); }
    public void accept(TouchSample s) {
        if (!port.inWorld()) { cancel(); return; }
        if (s.kind==TouchSample.Kind.DOWN && s.x<.5f) return;
        gesture.accept(s,port.width(),port.height());
    }
}
