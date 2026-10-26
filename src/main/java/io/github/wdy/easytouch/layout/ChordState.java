package io.github.wdy.easytouch.layout;

import java.util.*;
import java.util.function.BiConsumer;

/** Reference counts keep shared modifiers held until their last button releases them. */
public final class ChordState {
    private final Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
    private final BiConsumer<Integer, Boolean> output;
    public ChordState(BiConsumer<Integer, Boolean> output) { this.output = output; }
    public static boolean valid(int code) { return code > 0 && code < 256 || code >= -100 && code <= -98; }
    public static boolean modifier(int key) { return key == 29 || key == 157 || key == 42 || key == 54 || key == 56 || key == 184 || key == 219 || key == 220; }
    public static List<Integer> normalize(Collection<Integer> keys) {
        List<Integer> result = new ArrayList<Integer>();
        if (keys != null) for (Integer key : keys) if (key != null && valid(key) && !result.contains(key)) result.add(key);
        Collections.sort(result, (a,b) -> Boolean.compare(!modifier(a), !modifier(b)));
        return result;
    }
    public void press(List<Integer> keys) {
        for (int key : keys) {
            int count = counts.containsKey(key) ? counts.get(key) : 0;
            counts.put(key, count+1);
            if (count == 0) output.accept(key, true);
        }
    }
    public void release(List<Integer> keys) {
        for (int i=keys.size()-1; i>=0; i--) {
            int key=keys.get(i), count=counts.containsKey(key) ? counts.get(key) : 0;
            if (count == 1) { counts.remove(key); output.accept(key, false); }
            else if (count > 1) counts.put(key,count-1);
        }
    }
    public void clear() { counts.clear(); }
}
