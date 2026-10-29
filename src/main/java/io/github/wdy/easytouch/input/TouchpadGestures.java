package io.github.wdy.easytouch.input;
import io.github.wdy.easytouch.touch.TouchSample;
/** Relative pointer shares the single-contact click and hold rules. */
public final class TouchpadGestures {
    public interface Port {
        void move(float dx,float dy);
        void click(int button);
        void button(int button,boolean down);
    }
    private final ClickGesture gesture;
    public TouchpadGestures(Port port) {
        gesture=new ClickGesture(new ClickGesture.Port() {
            public void move(float x,float y,float dx,float dy) { port.move(dx,dy); }
            public void button(int b,boolean down) { port.button(b,down); }
        },true);
    }
    public boolean hasContacts() { return gesture.active(); }
    public void cancel() { gesture.cancel(); }
    public void tick(long now) { gesture.tick(now); }
    public void accept(TouchSample s,int w,int h) { gesture.accept(s,w,h); }
}
