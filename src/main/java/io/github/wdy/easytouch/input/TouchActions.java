package io.github.wdy.easytouch.input;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;
public final class TouchActions {
    private final KeyStateMerger.Source keys;
    public TouchActions(KeyStateMerger.Source keys) { this.keys=keys; }
    public void button(int button,boolean down) {
        Key key=button==1 ? Key.USE : Key.ATTACK;
        if (down) { keys.hold(key); keys.click(key); } else keys.release(key);
    }
}
