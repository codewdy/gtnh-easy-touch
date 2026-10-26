package io.github.wdy.easytouch;

import java.util.EnumSet;
import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;
import io.github.wdy.easytouch.input.TouchActions;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchActionsTest {
    private static final class Port implements KeyStateMerger.Port {
        final EnumSet<Key> physical = EnumSet.noneOf(Key.class);
        final EnumSet<Key> down = EnumSet.noneOf(Key.class);
        int attackClicks, useClicks;
        public boolean physicalDown(Key key) { return physical.contains(key); }
        public void setDown(Key key, boolean value) { if (value) down.add(key); else down.remove(key); }
        public void click(Key key) { if (key == Key.ATTACK) attackClicks++; else useClicks++; }
        public void clearClicks(Key key) { }
    }

    @Test public void smartTouchClickDoesNotSwapOrReleasePhysicalAttack() {
        Port port = new Port();
        port.physical.add(Key.ATTACK);
        port.down.add(Key.ATTACK); // Vanilla's response to a real left click.
        KeyStateMerger merger = new KeyStateMerger(port);
        TouchActions actions = new TouchActions(merger.newSource(), () -> Action.USE);
        merger.sync(true);
        assertEquals(EnumSet.of(Key.ATTACK), port.down);
        assertEquals(0, port.useClicks);
        actions.click();
        merger.sync(true);
        assertEquals(EnumSet.of(Key.ATTACK), port.down);
        assertEquals(1, port.useClicks);
        assertEquals(0, port.attackClicks);
    }

    @Test public void touchHoldMergesWithPhysicalButtonsAndRestoresThemOnRelease() {
        Port port = new Port();
        port.physical.addAll(EnumSet.of(Key.ATTACK, Key.USE));
        port.down.addAll(port.physical);
        KeyStateMerger merger = new KeyStateMerger(port);
        TouchActions actions = new TouchActions(merger.newSource(), () -> Action.USE);
        actions.press();
        merger.sync(true);
        assertEquals(EnumSet.of(Key.ATTACK, Key.USE), port.down);
        assertEquals(1, port.useClicks);
        actions.release();
        merger.sync(true);
        assertEquals(EnumSet.of(Key.ATTACK, Key.USE), port.down);
        port.physical.remove(Key.USE);
        port.down.remove(Key.USE); // Vanilla real right release.
        actions.press();
        merger.sync(true);
        actions.release();
        merger.sync(true);
        assertEquals(EnumSet.of(Key.ATTACK), port.down);
        assertEquals(2, port.useClicks);
    }
}
