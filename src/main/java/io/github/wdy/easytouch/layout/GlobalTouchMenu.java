package io.github.wdy.easytouch.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import io.github.wdy.easytouch.touch.TouchSample;

/** Captures complete contacts, including outside-dismiss taps, before underlying controls. */
public final class GlobalTouchMenu {
    public static final int SIZE = 24, MARGIN = 6, ROW = 22;
    private final Map<String, Integer> contacts = new HashMap<String, Integer>();
    private final Runnable opened;
    private final IntConsumer action;
    private boolean open, touchpadAvailable;
    private int panelWidth = 32;
    public void panelWidth(int value) { panelWidth = value; }
    public int panelWidth() { return panelWidth; }

    public GlobalTouchMenu(Runnable opened, IntConsumer action) { this.opened = opened; this.action = action; }
    public void touchpadAvailable(boolean value) { touchpadAvailable=value; }
    public int touchpadLeft(int width) { return left(width)-SIZE-4; }
    public boolean isOpen() { return open; }
    public int left(int width) { return width - MARGIN - SIZE; }
    public int top(int height) { return height - MARGIN - SIZE; }
    public int panelLeft(int width) { return width - MARGIN - panelWidth; }
    public int panelTop(int height) { return top(height) - 4 - ROW * 3; }
    public int hit(int x, int y, int width, int height) {
        if (Circle.hit(x,y,left(width),top(height),SIZE)) return 0;
        if (touchpadAvailable && Circle.hit(x,y,touchpadLeft(width),top(height),SIZE)) return 4;
        if (open) for (int row=0; row<3; row++)
            if (x >= panelLeft(width) && x < width-MARGIN
                    && y >= panelTop(height)+row*ROW && y < panelTop(height)+(row+1)*ROW) return row+1;
        return -1;
    }
    public void reset() { open = false; contacts.clear(); }
    public boolean accept(TouchSample sample, int width, int height) {
        if (sample.kind == TouchSample.Kind.RESET) { reset(); return false; }
        if (sample.simulated() || !sample.valid()) return false;
        String id = sample.deviceId + ":" + sample.fingerId;
        int hit = hit((int)(sample.x*width), (int)(sample.y*height), width, height);
        Integer captured = contacts.get(id);
        if (captured != null) {
            if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) {
                contacts.remove(id);
                if (sample.kind == TouchSample.Kind.UP && captured == hit) {
                    if (hit == 0) { open = !open; if (open) opened.run(); }
                    else if (hit == 4) { open = false; action.accept(hit); }
                    else if (hit > 0 && open) { open = false; action.accept(hit); }
                }
            }
            return true;
        }
        if (sample.kind == TouchSample.Kind.DOWN && (hit >= 0 || open)) {
            contacts.put(id, hit);
            if (hit < 0) open = false;
            return true;
        }
        return open;
    }
}
