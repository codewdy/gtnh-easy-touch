package io.github.wdy.easytouch.touch;

import java.util.ArrayList;
import java.util.List;

/** Bounded handoff from SDL callback threads to the Minecraft client thread. */
public final class TouchQueue {
    private final int capacity;
    private final List<TouchSample> pending = new ArrayList<TouchSample>();
    private boolean focused = true;
    private long dropped;
    private volatile boolean nativeTouchSeen;

    public TouchQueue(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity < 2");
        this.capacity = capacity;
    }

    public synchronized void offer(TouchSample sample) {
        if (!focused || !sample.valid()) return;
        if (sample.kind != TouchSample.Kind.RESET && !sample.simulated()) nativeTouchSeen = true;
        if (pending.size() >= capacity) {
            dropped += pending.size();
            pending.clear();
            // A dropped UP must never leave a finger held forever in a downstream consumer.
            pending.add(TouchSample.reset());
        }
        pending.add(sample);
    }

    public synchronized void focus(boolean value) {
        if (focused == value) return;
        focused = value;
        reset();
    }

    public synchronized void reset() {
        pending.clear();
        pending.add(TouchSample.reset());
    }

    public synchronized List<TouchSample> drain() {
        List<TouchSample> result = new ArrayList<TouchSample>(pending);
        pending.clear();
        return result;
    }

    /** Session latch: focus changes and queue resets must not disable touch again. */
    public boolean nativeTouchSeen() { return nativeTouchSeen; }

    public synchronized long dropped() { return dropped; }
}
