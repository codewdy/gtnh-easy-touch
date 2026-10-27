package io.github.wdy.easytouch.input;

import java.util.function.Supplier;
import io.github.wdy.easytouch.Action;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;

/** Smart taps select an action; long presses always hold attack until release. */
public final class TouchActions {
    private final KeyStateMerger.Source keys;
    private final Supplier<Action> primary;

    public TouchActions(KeyStateMerger.Source keys, Supplier<Action> primary) {
        this.keys = keys;
        this.primary = primary;
    }

    private Key key() { return primary.get() == Action.ATTACK ? Key.ATTACK : Key.USE; }
    public void click() { keys.click(key()); }
    public void press() {
        keys.hold(Key.ATTACK);
        keys.click(Key.ATTACK);
    }
    public void release() {
        keys.release(Key.ATTACK);
        keys.release(Key.USE);
    }
}
