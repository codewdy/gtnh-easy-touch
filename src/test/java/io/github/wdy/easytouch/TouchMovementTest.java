package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.*;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;
import io.github.wdy.easytouch.touch.TouchSample;
import java.util.EnumSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchMovementTest {
    private final EnumSet<Key> down = EnumSet.noneOf(Key.class);
    private final KeyStateMerger merger = new KeyStateMerger(new KeyStateMerger.Port() {
        public boolean physicalDown(Key key) { return false; }
        public void setDown(Key key, boolean held) { if (held) down.add(key); else down.remove(key); }
        public void click(Key key) { fail("Movement must not queue clicks"); }
        public void clearClicks(Key key) { }
    });
    private final TouchMovement movement = new TouchMovement(merger.newSource());
    private boolean send(TouchSample.Kind kind, long device, long finger, float dx, float dy) {
        boolean consumed = movement.accept(new TouchSample(kind, device, finger, 0, 1,
            (TouchMovement.centerX(400, 300) + dx) / 400,
            (TouchMovement.centerY(400, 300) + dy) / 300, 0, 0, 1), 400, 300, true);
        merger.sync(true);
        return consumed;
    }
    @Test public void diagonalCenterAndRelease() {
        assertTrue(send(TouchSample.Kind.DOWN, 1, 1, -24, -24));
        assertEquals(EnumSet.of(Key.LEFT, Key.FORWARD), down);
        send(TouchSample.Kind.MOVE, 1, 1, 0, 0);
        assertTrue(down.isEmpty());
        send(TouchSample.Kind.MOVE, 1, 1, 180, 0);
        assertEquals(EnumSet.of(Key.RIGHT), down);
        assertTrue(send(TouchSample.Kind.UP, 1, 1, 180, 0));
        assertTrue(down.isEmpty());
    }
    @Test public void ownerUsesBothIdsAndDoesNotStealLookContacts() {
        assertFalse(send(TouchSample.Kind.DOWN, 1, 2, 150, 0));
        assertFalse(send(TouchSample.Kind.MOVE, 1, 2, 0, -24));
        assertTrue(send(TouchSample.Kind.DOWN, 1, 1, 0, -24));
        assertFalse(send(TouchSample.Kind.UP, 2, 1, 0, 24));
        assertFalse(send(TouchSample.Kind.MOVE, 1, 2, 0, 24));
        assertEquals(EnumSet.of(Key.FORWARD), down);
        assertTrue(send(TouchSample.Kind.CANCEL, 1, 1, 0, 0));
        assertTrue(down.isEmpty());
    }
    @Test public void resetAndDisabledStateReleaseMovement() {
        send(TouchSample.Kind.DOWN, 1, 1, 0, 24);
        movement.accept(TouchSample.reset(), 400, 300, true);
        merger.sync(true);
        assertTrue(down.isEmpty());
        send(TouchSample.Kind.DOWN, 1, 1, 0, 24);
        movement.accept(TouchSample.reset(), 400, 300, false);
        merger.sync(false);
        assertTrue(down.isEmpty());
        assertFalse(send(TouchSample.Kind.DOWN, -1, 1, 0, 24));
    }
    @Test public void wheelAndLookCanBeUsedTogether() {
        final int[] turns = {0};
        TouchLook look = new TouchLook(new TouchLook.Port() {
            public boolean inWorld() { return true; }
            public int width() { return 400; }
            public int height() { return 300; }
            public void turn(float x, float y) { turns[0]++; }
            public void click() { fail("Wheel must not attack"); }
            public void press() { fail("Wheel must not attack"); }
            public void release() { }
        });
        TouchSample[] samples = {
            new TouchSample(TouchSample.Kind.DOWN, 1, 1, 0, 1, .15f, .64f, 0, 0, 1),
            new TouchSample(TouchSample.Kind.DOWN, 1, 2, 0, 1, .8f, .5f, 0, 0, 1),
            new TouchSample(TouchSample.Kind.MOVE, 1, 2, 100, 1, .9f, .5f, 0, 0, 1),
            new TouchSample(TouchSample.Kind.UP, 1, 1, 200, 1, .15f, .64f, 0, 0, 1),
            new TouchSample(TouchSample.Kind.UP, 1, 2, 200, 1, .9f, .5f, 0, 0, 1)
        };
        for (TouchSample sample : samples) {
            if (!movement.accept(sample, 400, 300, true)) look.accept(sample);
            merger.sync(true);
        }
        assertEquals(1, turns[0]);
        assertTrue(down.isEmpty());
    }
}
