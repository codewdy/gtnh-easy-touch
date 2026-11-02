package io.github.wdy.easytouch.input;
import io.github.wdy.easytouch.touch.TouchSample;
public final class TouchMouse {
    public interface Port {
        boolean active(); int width(); int height();
        void move(float x,float y);
        void button(int button,boolean down);
    }
    private final Port port;
    private final ClickGesture gesture;
    public TouchMouse(Port port) {
        this.port=port;
        gesture=new ClickGesture(new ClickGesture.Port() {
            public void move(float x,float y,float dx,float dy) { port.move(x,y); }
            public void button(int b,boolean down) { port.button(b,down); }
            public int tapButton() { return 0; }
            public int holdButton() { return 1; }
        },false);
    }
    public boolean held() { return gesture.held(); }
    public boolean awaitingSecond() { return gesture.awaitingSecond(); }
    public void cancel() { gesture.cancel(); }
    public void tick(long now) { if (!port.active()) cancel(); else gesture.tick(now); }
    public void accept(TouchSample s) { if (!port.active()) cancel(); else gesture.accept(s,port.width(),port.height()); }
}
