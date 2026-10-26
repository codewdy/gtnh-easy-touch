package io.github.wdy.easytouch.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import io.github.wdy.easytouch.touch.TouchSample;

/** A modal overlay: capture complete contacts and activate one option only on release. */
public final class MappingMenu {
    public static final int ROW = GlobalTouchMenu.ROW;
    private final Map<String, Integer> contacts = new HashMap<String, Integer>();
    private final Consumer<TouchLayout.Button> action;
    private TouchLayout.Button button;
    private int page, panelWidth = 32;
    public void panelWidth(int value) { panelWidth = value; }
    public int panelWidth() { return panelWidth; }
    public MappingMenu(Consumer<TouchLayout.Button> action) { this.action = action; }
    public boolean isOpen() { return button != null; }
    public TouchLayout.Button button() { return button; }
    public int page() { return page; }
    public int rows(int height) {
        int capacity=Math.max(1,(height-12)/ROW);
        return button != null && button.options.size() > capacity ? Math.max(1,capacity-1) : capacity;
    }
    public int panelHeight(int height) { return (visibleRows(height)+(pages(height)>1 ? 1 : 0))*ROW; }
    public int pages(int height) { return button == null ? 1 : Math.max(1,(button.options.size()+rows(height)-1)/rows(height)); }
    public int left(int width) {
        int anchor=button == null ? width-6 : button.left(width)+button.size;
        return Math.max(6,Math.min(width-6-panelWidth,anchor-panelWidth));
    }
    public int visibleRows(int height) {
        return button == null ? 1 : Math.max(1,Math.min(rows(height),button.options.size()-page*rows(height)));
    }
    public int top(int height) {
        int panelHeight=panelHeight(height);
        int anchor=button == null ? height-6 : button.top(height);
        int y=anchor-4-panelHeight;
        if (y < 6) y=anchor+(button == null ? 0 : button.size)+4;
        return Math.max(6,Math.min(height-6-panelHeight,y));
    }
    public void open(TouchLayout.Button value) { button=value; page=0; contacts.clear(); }
    public void reset() { button=null; contacts.clear(); }
    private int hit(int x, int y, int width, int height) {
        if (button == null || x < left(width) || x >= Math.min(width-6,left(width)+panelWidth)) return -1;
        int row=(y-top(height))/ROW;
        if (y >= top(height) && row < visibleRows(height)) {
            int index=page*rows(height)+row;
            return index < button.options.size() ? index : -4;
        }
        int footer=top(height)+visibleRows(height)*ROW;
        if (pages(height) > 1 && y >= footer && y < footer+ROW) {
            return x < left(width)+panelWidth/2 ? -2 : -3;
        }
        return -1;
    }
    public boolean accept(TouchSample sample, int width, int height) {
        if (sample.kind == TouchSample.Kind.RESET) { reset(); return false; }
        if (sample.simulated() || !sample.valid()) return false;
        String id=sample.deviceId+":"+sample.fingerId;
        Integer captured=contacts.get(id);
        int hit=hit((int)(sample.x*width),(int)(sample.y*height),width,height);
        if (captured != null) {
            if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) {
                contacts.remove(id);
                if (sample.kind == TouchSample.Kind.UP && isOpen() && captured == hit) {
                    if (hit >= 0) {
                        TouchLayout.Button selected=button.options.get(hit);
                        button=null;
                        action.accept(selected);
                    } else if (hit == -2 || hit == -3) {
                        page=Math.max(0,Math.min(pages(height)-1,page+(hit == -2 ? -1 : 1)));
                        // Contacts begun on the previous page cannot activate the next page.
                        contacts.replaceAll((key,value) -> -1);
                    }
                }
            }
            return true;
        }
        if (!isOpen()) return false;
        if (sample.kind == TouchSample.Kind.DOWN) {
            contacts.put(id,hit);
            if (hit == -1) button=null;
        }
        return true;
    }
}
