package io.github.wdy.easytouch;

import java.util.EnumMap;
import java.util.EnumSet;
import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.input.KeyStateMerger.Key;
import io.github.wdy.easytouch.input.SneakUseGesture;
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
    public void releasingMouseDoesNotReleaseAUsingShortcut() {
        FakePort port = new FakePort();
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        KeyStateMerger.Source shortcut = merger.newSource();
        mouse.override(Key.USE, true);
        shortcut.hold(Key.USE, true);
        shortcut.hold(Key.SNEAK, true);
        merger.sync(true);
        mouse.override(Key.USE, false);
        merger.sync(true);
        assertTrue(port.down.contains(Key.USE));
        assertTrue(port.down.contains(Key.SNEAK));
        shortcut.clear();
        merger.sync(true);
        assertFalse(port.down.contains(Key.USE));
        assertFalse(port.down.contains(Key.SNEAK));
    }

    @Test
    public void releasingShortcutDoesNotReleaseAMouseGesture() {
        FakePort port = new FakePort();
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        KeyStateMerger.Source shortcut = merger.newSource();
        mouse.override(Key.USE, true);
        shortcut.hold(Key.USE, true);
        shortcut.hold(Key.SNEAK, true);
        merger.sync(true);
        shortcut.clear();
        merger.sync(true);
        assertTrue(port.down.contains(Key.USE));
        assertFalse(port.down.contains(Key.SNEAK));
        mouse.override(Key.USE, false);
        merger.sync(true);
        assertFalse(port.down.contains(Key.USE));
    }

    @Test
    public void releaseRestoresCurrentPhysicalKeysInsteadOfAnOldSnapshot() {
        for (boolean physicallyDownAtRelease : new boolean[] {false, true}) {
            FakePort port = new FakePort();
            if (!physicallyDownAtRelease) port.physical.add(Key.SNEAK);
            KeyStateMerger merger = new KeyStateMerger(port);
            KeyStateMerger.Source shortcut = merger.newSource();
            shortcut.hold(Key.SNEAK, true);
            shortcut.hold(Key.USE, true);
            merger.sync(true);
            if (physicallyDownAtRelease) port.physical.addAll(EnumSet.of(Key.SNEAK, Key.USE));
            else port.physical.clear();
            shortcut.clear();
            merger.sync(true);
            assertEquals(physicallyDownAtRelease, port.down.contains(Key.SNEAK));
            assertEquals(physicallyDownAtRelease, port.down.contains(Key.USE));
        }
    }

    @Test
    public void cancellingOneSourceDoesNotDiscardAnotherSourcesPendingClick() {
        FakePort port = new FakePort();
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        KeyStateMerger.Source shortcut = merger.newSource();
        mouse.override(Key.ATTACK, true);
        mouse.click(Key.ATTACK);
        shortcut.hold(Key.USE, true);
        shortcut.click(Key.USE);
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
        KeyStateMerger.Source shortcut = merger.newSource();
        mouse.override(Key.ATTACK, true);
        mouse.click(Key.ATTACK);
        shortcut.hold(Key.SNEAK, true);
        shortcut.hold(Key.USE, true);
        shortcut.click(Key.USE);
        merger.sync(true);
        assertEquals(1, port.clicks(Key.USE));
        mouse.clear();
        shortcut.clear();
        merger.discardClicks();
        merger.sync(false);
        assertTrue(port.down.isEmpty());
        assertEquals(0, port.clicks(Key.ATTACK));
        assertEquals(0, port.clicks(Key.USE));
    }

    @Test
    public void unownedKeysAreLeftToVanillaAndOtherMods() {
        FakePort port = new FakePort();
        port.down.add(Key.SNEAK); // Another input source set this without a physical key.
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        mouse.override(Key.ATTACK, true);
        merger.sync(true);
        assertTrue(port.down.contains(Key.SNEAK));
        assertFalse(port.written.contains(Key.SNEAK));
    }

    @Test
    public void delayedNativeUseWorksAlongsideASwappedMouseWithoutChangingItsMapping() {
        FakePort port = new FakePort();
        port.physical.add(Key.USE); // Physical RMB is mapped to attack by the mouse feature.
        KeyStateMerger merger = new KeyStateMerger(port);
        KeyStateMerger.Source mouse = merger.newSource();
        KeyStateMerger.Source shortcut = merger.newSource();
        mouse.override(Key.ATTACK, true);
        mouse.override(Key.USE, false);
        SneakUseGesture gesture = new SneakUseGesture();
        gesture.updateKey(true, false); // A quick tap from the independent shortcut.
        shortcut.hold(Key.SNEAK, gesture.holdsSneak());
        shortcut.hold(Key.USE, gesture.holdsUse());
        merger.sync(true);
        assertTrue(port.down.contains(Key.ATTACK));
        assertTrue(port.down.contains(Key.SNEAK));
        assertFalse(port.down.contains(Key.USE));

        gesture.endTick(true);
        assertTrue(gesture.beginTick(true));
        shortcut.hold(Key.USE, gesture.holdsUse());
        shortcut.click(Key.USE);
        merger.sync(true);
        assertEquals(1, port.clicks(Key.USE));
        assertTrue(port.down.contains(Key.ATTACK));

        gesture.endTick(true);
        shortcut.hold(Key.SNEAK, gesture.holdsSneak());
        shortcut.hold(Key.USE, gesture.holdsUse());
        merger.sync(true);
        assertTrue(port.down.contains(Key.ATTACK));
        assertFalse(port.down.contains(Key.USE));
        assertFalse(port.down.contains(Key.SNEAK));
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
