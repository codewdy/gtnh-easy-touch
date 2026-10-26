package io.github.wdy.easytouch.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import io.github.wdy.easytouch.touch.TouchSample;

/** A modal overlay: capture complete contacts and activate one option only on release. */
public final class MappingMenu {
    public static final int WIDTH = 220, ROW = 28;
    private final Map<String, Integer> contacts = new HashMap<String, Integer>();
    private final Consumer<TouchLayout.Button> action;
    private TouchLayout.Button button;
    private int page;
    public MappingMenu(Consumer<TouchLayout.Button> action) { this.action = action; }
    public boolean isOpen() { return button != null; }
    public TouchLayout.Button button() { return button; }
    public int page() { return page; }
    public int rows(int height) { return Math.max(1, (height-84)/ROW); }
    public int pages(int height) { return button == null ? 1 : Math.max(1,(button.options.size()+rows(height)-1)/rows(height)); }
    public int left(int width) { return Math.max(4, (width-WIDTH)/2); }
    public int top() { return 36; }
    public void open(TouchLayout.Button value) { button=value; page=0; contacts.clear(); }
    public void reset() { button=null; contacts.clear(); }
    private int hit(int x, int y, int width, int height) {
        if (button == null || x < left(width) || x >= Math.min(width-4,left(width)+WIDTH)) return -1;
        int row=(y-top())/ROW;
        if (y >= top() && row < rows(height)) {
            int index=page*rows(height)+row;
            return index < button.options.size() ? index : -1;
        }
        int footer=top()+rows(height)*ROW;
        if (y >= footer && y < footer+ROW) {
            int third=Math.min(WIDTH,width-8)/3;
            return x < left(width)+third ? -2 : x < left(width)+third*2 ? -3 : -4;
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
                    } else button=null;
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
