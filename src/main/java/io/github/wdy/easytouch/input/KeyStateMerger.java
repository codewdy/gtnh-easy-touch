package io.github.wdy.easytouch.input;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Merges mouse-key overrides and queues clicks independently of Minecraft's input API. */
public final class KeyStateMerger {
    public enum Key { ATTACK, USE, FORWARD, BACK, LEFT, RIGHT }

    public interface Port {
        int code(Key key);
        boolean physicalCodeDown(int code);
        void setCodeDown(int code, boolean down);
        void clearCodeClicks(int code);
        boolean physicalDown(Key key);
        void setDown(Key key, boolean down);
        void click(Key key);
        void clearClicks(Key key);
    }

    private enum Request { OVERRIDE_DOWN, OVERRIDE_UP, HOLD_DOWN }

    private final Port port;
    private final List<Source> sources = new ArrayList<Source>();
    private final Set<Integer> controlled = new HashSet<Integer>();

    public KeyStateMerger(Port port) { this.port = port; }

    public Source newSource() {
        Source source = new Source();
        sources.add(source);
        return source;
    }

    /** Apply held states before clicks so vanilla sees the corresponding key as held during use. */
    public void sync(boolean inWorld) {
        Map<Integer, Boolean> requested = new HashMap<Integer, Boolean>();
        Set<Integer> additive = new HashSet<Integer>();
        for (Source source : sources) {
            for (Map.Entry<Key, Request> entry : source.requests.entrySet()) {
                int code = port.code(entry.getKey());
                requested.put(code, Boolean.TRUE.equals(requested.get(code)) || entry.getValue() != Request.OVERRIDE_UP);
                if (entry.getValue() == Request.HOLD_DOWN) additive.add(code);
            }
            for (int code : source.codes) { requested.put(code, true); additive.add(code); }
        }
        Set<Integer> affected = new HashSet<Integer>(controlled);
        affected.addAll(requested.keySet());
        for (int code : affected) {
            boolean down = requested.containsKey(code) ? requested.get(code) : port.physicalCodeDown(code);
            if (additive.contains(code)) down |= port.physicalCodeDown(code);
            port.setCodeDown(code, inWorld && down);
        }
        controlled.clear();
        controlled.addAll(requested.keySet());
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
        Set<Integer> affected = new HashSet<Integer>(controlled);
        for (Source source : sources) {
            for (Key key : source.requests.keySet()) affected.add(port.code(key));
            for (Key key : source.clicks.keySet()) affected.add(port.code(key));
            affected.addAll(source.codes);
            source.clicks.clear();
        }
        for (int code : affected) port.clearCodeClicks(code);
    }

    public static final class Source {
        private final EnumMap<Key, Request> requests = new EnumMap<Key, Request>(Key.class);
        private final EnumMap<Key, Integer> clicks = new EnumMap<Key, Integer>(Key.class);

        private final Set<Integer> codes = new HashSet<Integer>();

        private Source() { }
        public void holdCode(int code) { codes.add(code); }
        public void releaseCode(int code) { codes.remove(code); }

        /** Replace the physical key's state; other sources may still independently hold this key. */
        public void override(Key key, boolean down) {
            requests.put(key, down ? Request.OVERRIDE_DOWN : Request.OVERRIDE_UP);
        }

        /** Add a held key without overriding the mouse source's mapping. */
        public void hold(Key key) { requests.put(key, Request.HOLD_DOWN); }

        public void release(Key key) { requests.remove(key); }

        public void click(Key key) {
            clicks.put(key, clicks.containsKey(key) ? clicks.get(key) + 1 : 1);
        }

        public void clear() {
            requests.clear();
            codes.clear();
            clicks.clear();
        }
    }
}
