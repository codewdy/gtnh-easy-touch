package io.github.wdy.easytouch.input;
import io.github.wdy.easytouch.touch.TouchSample;
/** Right-hand world input: short taps click, slides turn, stationary holds retain their button. */
public final class TouchLook {
    public interface Port {
        boolean inWorld(); int width(); int height();
        void turn(float dx,float dy);
        void button(int button,boolean down);
        default long holdNanos() { return 300_000_000L; }
        default Object miningTarget() { return null; }
        default boolean holdingUseItem() { return false; }
    }
    private static final long PREVIEW_NS = 150_000_000L;
    private Object target;
    private final Port port;
    private final ClickGesture gesture;
    public TouchLook(Port port) {
        this.port=port;
        gesture=new ClickGesture(new ClickGesture.Port() {
            public void move(float x,float y,float dx,float dy) { port.turn(dx,dy); }
            public void button(int b,boolean down) { port.button(b,down); }
            public boolean holdReady(long now) {
                updateTarget(now);
                return port.holdingUseItem() || target != null;
            }
            public int holdButton() { return port.holdingUseItem() ? 1 : 0; }
            public long holdNanos() { return port.holdNanos(); }
            public int tapButton() { return 1; }
            public long tapNanos() { return PREVIEW_NS; }
        },true);
    }
    public float holdProgress(long now) {
        if (!port.inWorld() || gesture.held()) return -1;
        float progress = gesture.holdProgress(now);
        return gesture.holdElapsed(now) >= PREVIEW_NS ? progress : -1;
    }
    public boolean held() { return gesture.held(); }
    public boolean mining() { return gesture.heldButton(0); }
    private void updateTarget(long now) {
        if (port.holdingUseItem()) return;
        Object next = port.miningTarget();
        if (!java.util.Objects.equals(target, next)) {
            target = next;
            gesture.restartHold(now);
        }
    }
    public void cancel() { gesture.cancel(); target = null; }
    public void tick(long now) { if (!port.inWorld()) cancel(); else gesture.tick(now); }
    public void accept(TouchSample s) {
        if (!port.inWorld()) { cancel(); return; }
        if (s.kind==TouchSample.Kind.DOWN && s.x<.5f) return;
        if (s.kind == TouchSample.Kind.DOWN && !gesture.active() && s.valid() && !s.simulated()) {
            target = port.miningTarget();
        }
        gesture.accept(s,port.width(),port.height());
    }
}
