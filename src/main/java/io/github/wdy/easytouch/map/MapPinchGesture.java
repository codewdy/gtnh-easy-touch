package io.github.wdy.easytouch.map;

import io.github.wdy.easytouch.touch.TouchSample;
import java.util.LinkedHashMap;
import java.util.Map;

/** Consumes the entire multi-contact sequence, including its final remaining finger. */
public final class MapPinchGesture {
    public interface Port { void cancelMouse(); void zoom(boolean in); }
    private final Port port;
    private final Map<String, TouchSample> contacts = new LinkedHashMap<>();
    private boolean captured;
    private double baseline;
    public MapPinchGesture(Port port) { this.port = port; }
    public void reset() { contacts.clear(); captured = false; baseline = 0; }
    public boolean accept(TouchSample s, int width, int height) {
        if (s.kind == TouchSample.Kind.RESET) { reset(); return false; }
        if (s.simulated() || !s.valid()) return false;
        String key = s.deviceId + ":" + s.fingerId;
        boolean consume = captured;
        if (s.kind == TouchSample.Kind.DOWN) contacts.put(key, s);
        else if (s.kind == TouchSample.Kind.MOVE && contacts.containsKey(key)) contacts.put(key, s);
        else if (s.kind == TouchSample.Kind.UP || s.kind == TouchSample.Kind.CANCEL) {
            contacts.remove(key); baseline = 0;
            if (contacts.isEmpty()) reset();
            return consume;
        }
        if (contacts.size() >= 2 && !captured) {
            captured = true; port.cancelMouse();
        }
        if (contacts.size() != 2) { baseline = 0; return captured; }
        TouchSample[] pair = contacts.values().toArray(new TouchSample[0]);
        if (pair[0].deviceId != pair[1].deviceId) { baseline = 0; return captured; }
        double distance = Math.hypot((pair[0].x-pair[1].x)*width, (pair[0].y-pair[1].y)*height);
        if (distance < 12) { baseline = 0; return captured; }
        if (baseline == 0) baseline = distance;
        double ratio = distance / baseline;
        if (ratio >= 1.25 || ratio <= 0.8) {
            port.zoom(ratio > 1); baseline = distance;
        }
        return captured;
    }
}
