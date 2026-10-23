package io.github.wdy.easytouch.input;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/** Merges mouse-key overrides and queues clicks independently of Minecraft's input API. */
public final class KeyStateMerger {
    public enum Key { ATTACK, USE }

    public interface Port {
        boolean physicalDown(Key key);
        void setDown(Key key, boolean down);
        void click(Key key);
        void clearClicks(Key key);
    }

    private enum Request { OVERRIDE_DOWN, OVERRIDE_UP }

    private final Port port;
    private final List<Source> sources = new ArrayList<Source>();
    private final EnumSet<Key> controlled = EnumSet.noneOf(Key.class);

    public KeyStateMerger(Port port) { this.port = port; }

    public Source newSource() {
        Source source = new Source();
        sources.add(source);
        return source;
    }

    /** Apply held states before clicks so vanilla sees the corresponding key as held during use. */
    public void sync(boolean inWorld) {
        for (Key key : Key.values()) {
            boolean requested = false, held = false;
            for (Source source : sources) {
                Request request = source.requests.get(key);
                if (request == null) continue;
                requested = true;
                held |= request == Request.OVERRIDE_DOWN;
            }
            if (requested) {
                port.setDown(key, inWorld && held);
            } else if (controlled.contains(key)) {
                port.setDown(key, inWorld && port.physicalDown(key));
            }
            if (requested) controlled.add(key);
            else controlled.remove(key);
        }
        for (Source source : sources) {
            for (Key key : Key.values()) {
                int count = source.clicks.containsKey(key) ? source.clicks.get(key) : 0;
                if (inWorld) for (int i = 0; i < count; i++) port.click(key);
            }
            source.clicks.clear();
        }
    }

    /** Shared lifecycle boundaries discard emitted clicks as well as clicks not yet sent to Minecraft. */
    public void discardClicks() {
        EnumSet<Key> affected = controlled.clone();
        for (Source source : sources) {
            affected.addAll(source.requests.keySet());
            affected.addAll(source.clicks.keySet());
            source.clicks.clear();
        }
        for (Key key : affected) port.clearClicks(key);
    }

    public static final class Source {
        private final EnumMap<Key, Request> requests = new EnumMap<Key, Request>(Key.class);
        private final EnumMap<Key, Integer> clicks = new EnumMap<Key, Integer>(Key.class);

        private Source() { }

        /** Replace the physical key's state; other sources may still independently hold this key. */
        public void override(Key key, boolean down) {
            requests.put(key, down ? Request.OVERRIDE_DOWN : Request.OVERRIDE_UP);
        }

        public void release(Key key) { requests.remove(key); }

        public void click(Key key) {
            clicks.put(key, clicks.containsKey(key) ? clicks.get(key) + 1 : 1);
        }

        public void clear() {
            requests.clear();
            clicks.clear();
        }
    }
}
