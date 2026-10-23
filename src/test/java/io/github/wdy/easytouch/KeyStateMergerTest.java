package io.github.wdy.easytouch;

import java.util.EnumMap;
import java.util.EnumSet;
import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyStateMergerTest {
    @Test
    public void swappedRightButtonDoesNotAlsoHoldNativeUse() {
        FakePort port = new FakePort();
        port.physical.add(Key.USE);
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        mouse.override(Key.ATTACK, true);
        mouse.override(Key.USE, false);
        merger.sync(true);
        assertTrue(port.down.contains(Key.ATTACK));
        assertFalse(port.down.contains(Key.USE));
    }

    @Test
    public void releaseRestoresCurrentPhysicalKeysInsteadOfAnOldSnapshot() {
        for (boolean physicallyDownAtRelease : new boolean[] {false, true}) {
            FakePort port = new FakePort();
            if (!physicallyDownAtRelease) port.physical.addAll(EnumSet.allOf(Key.class));
            KeyStateMerger merger = new KeyStateMerger(port);
            KeyStateMerger.Source mouse = merger.newSource();
            mouse.override(Key.ATTACK, false);
            mouse.override(Key.USE, true);
            merger.sync(true);
            if (physicallyDownAtRelease) port.physical.addAll(EnumSet.allOf(Key.class));
            else port.physical.clear();
            mouse.clear();
            merger.sync(true);
            assertEquals(physicallyDownAtRelease, port.down.contains(Key.ATTACK));
            assertEquals(physicallyDownAtRelease, port.down.contains(Key.USE));
        }
    }

    @Test
    public void cancellingOneSourceDoesNotDiscardAnotherSourcesPendingClick() {
        FakePort port = new FakePort();
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        KeyStateMerger.Source other = merger.newSource();
        mouse.override(Key.ATTACK, true);
        mouse.click(Key.ATTACK);
        other.override(Key.USE, true);
        other.click(Key.USE);
        mouse.clear();
        merger.sync(true);
        assertEquals(0, port.clicks(Key.ATTACK));
        assertEquals(1, port.clicks(Key.USE));
        merger.sync(true);
        assertEquals(1, port.clicks(Key.USE)); // Held state does not repeatedly enqueue clicks.
    }

    @Test
    public void aSharedBoundaryClearsQueuedClicksAndDoesNotRestoreHeldPhysicalKeysInAGui() {
        FakePort port = new FakePort();
        port.physical.addAll(EnumSet.allOf(Key.class));
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        mouse.override(Key.ATTACK, true);
        mouse.override(Key.USE, true);
        mouse.click(Key.ATTACK);
        mouse.click(Key.USE);
        merger.sync(true);
        assertEquals(1, port.clicks(Key.USE));
        mouse.clear();
        merger.discardClicks();
        merger.sync(false);
        assertTrue(port.down.isEmpty());
        assertEquals(0, port.clicks(Key.ATTACK));
        assertEquals(0, port.clicks(Key.USE));
    }

    @Test
    public void unownedKeysAreLeftToVanillaAndOtherMods() {
        FakePort port = new FakePort();
        port.down.add(Key.USE); // Another input source set this without a physical key.
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        mouse.override(Key.ATTACK, true);
        merger.sync(true);
        assertTrue(port.down.contains(Key.USE));
        assertFalse(port.written.contains(Key.USE));
    }

    private static final class FakePort implements KeyStateMerger.Port {
        final EnumSet<Key> physical = EnumSet.noneOf(Key.class);
        final EnumSet<Key> down = EnumSet.noneOf(Key.class);
        final EnumSet<Key> written = EnumSet.noneOf(Key.class);
        final EnumMap<Key, Integer> queued = new EnumMap<Key, Integer>(Key.class);

        public boolean physicalDown(Key key) { return physical.contains(key); }
        public void setDown(Key key, boolean held) {
            written.add(key);
            if (held) down.add(key);
            else down.remove(key);
        }
        public void click(Key key) {
            assertTrue("Set held state before clicking " + key, down.contains(key));
            queued.put(key, clicks(key) + 1);
        }
        public void clearClicks(Key key) { queued.remove(key); }
        int clicks(Key key) { return queued.containsKey(key) ? queued.get(key) : 0; }
    }
}
