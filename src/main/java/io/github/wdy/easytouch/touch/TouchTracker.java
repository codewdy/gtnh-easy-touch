package io.github.wdy.easytouch.touch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-thread contact state; mouse-simulated contacts are counted separately. */
public final class TouchTracker {
    private final Map<String, TouchSample> contacts = new LinkedHashMap<String, TouchSample>();
    public long nativeEvents, simulatedEvents;
    public int peakNative;
    public TouchSample last;

    public void accept(TouchSample sample) {
        if (sample.kind == TouchSample.Kind.RESET) {
            contacts.clear();
            return;
        }
        if (!sample.valid()) return;
        last = sample;
        if (sample.simulated()) simulatedEvents++;
        else nativeEvents++;
        String key = sample.deviceId + ":" + sample.fingerId;
        switch (sample.kind) {
            case DOWN:
                if (contacts.size() < 64 || contacts.containsKey(key)) contacts.put(key, sample);
                break;
            case MOVE:
                // No orphan MOVE may recreate a contact canceled by focus loss or queue overflow.
                if (contacts.containsKey(key)) contacts.put(key, sample);
                break;
            case UP:
            case CANCEL:
                contacts.remove(key);
                break;
            default: break;
        }
        peakNative = Math.max(peakNative, nativeCount());
    }

    public List<TouchSample> contacts() { return new ArrayList<TouchSample>(contacts.values()); }

    public int nativeCount() {
        int count = 0;
        for (TouchSample sample : contacts.values()) if (!sample.simulated()) count++;
        return count;
    }

    public void resetStats() {
        nativeEvents = simulatedEvents = 0;
        peakNative = nativeCount();
        last = null;
    }
}
