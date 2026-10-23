package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.SneakUseGesture;
import org.junit.Test;
import static org.junit.Assert.*;

public class SneakUseGestureTest {
    @Test
    public void firstUseWaitsForANormalSneakUpdateAndTheNextTick() {
        SneakUseGesture shortcut = new SneakUseGesture();
        shortcut.updateKey(true, true);
        assertTrue(shortcut.holdsSneak());
        assertFalse(shortcut.holdsUse());
        assertFalse(shortcut.beginTick(false));
        shortcut.endTick(false);
        assertFalse(shortcut.beginTick(true)); // Local state alone is not proof of a completed player update.
        shortcut.endTick(true);
        assertFalse(shortcut.holdsUse()); // Never enqueue use from ClientTick END.
        assertTrue(shortcut.beginTick(true));
        assertTrue(shortcut.holdsSneak());
        assertTrue(shortcut.holdsUse());
    }

    @Test
    public void aQuickTapStillPerformsOneUseAfterSneaking() {
        SneakUseGesture shortcut = new SneakUseGesture();
        shortcut.updateKey(true, false); // Press and release both arrived between ticks.
        assertTrue(shortcut.holdsSneak());
        shortcut.endTick(true);
        assertTrue(shortcut.beginTick(true));
        shortcut.updateKey(false, false);
        assertTrue(shortcut.holdsUse()); // Keep the first click alive until vanilla processes it.
        shortcut.endTick(true);
        assertFalse(shortcut.holdsSneak());
        assertFalse(shortcut.holdsUse());
        assertFalse(shortcut.beginTick(true));
    }

    @Test
    public void holdingUsesVanillaRepeatAndReleasingStopsBothSyntheticKeys() {
        SneakUseGesture shortcut = readyShortcut();
        assertTrue(shortcut.beginTick(true));
        shortcut.endTick(true);
        for (int i = 0; i < 20; i++) {
            shortcut.updateKey(true, true); // Keyboard repeat must not generate extra use clicks.
            assertFalse(shortcut.beginTick(true));
            assertTrue(shortcut.holdsSneak());
            assertTrue(shortcut.holdsUse());
            shortcut.endTick(true);
        }
        shortcut.updateKey(false, false);
        assertFalse(shortcut.holdsSneak());
        assertFalse(shortcut.holdsUse());
        shortcut.updateKey(true, true);
        assertFalse(shortcut.beginTick(true)); // A new press goes through sneak synchronization again.
    }

    @Test
    public void lossOfSneakingBeforeFirstUseRequiresAnotherUpdate() {
        SneakUseGesture shortcut = readyShortcut();
        assertFalse(shortcut.beginTick(false));
        assertFalse(shortcut.holdsUse());
        shortcut.endTick(false);
        assertFalse(shortcut.beginTick(true));
        shortcut.endTick(true);
        assertTrue(shortcut.beginTick(true));
    }

    @Test
    public void lossOfSneakingWhileHeldStopsUseUntilItIsRestored() {
        SneakUseGesture shortcut = readyShortcut();
        shortcut.beginTick(true);
        shortcut.endTick(false);
        assertFalse(shortcut.holdsUse());
        assertTrue(shortcut.holdsSneak());
        assertFalse(shortcut.beginTick(false));
        shortcut.endTick(true);
        assertTrue(shortcut.beginTick(true));
    }

    @Test
    public void aBoundaryDiscardsPendingAndActiveUsesUntilRelease() {
        for (int stage = 0; stage < 3; stage++) {
            SneakUseGesture shortcut = new SneakUseGesture();
            shortcut.updateKey(true, true);
            if (stage >= 1) shortcut.endTick(true);
            if (stage >= 2) shortcut.beginTick(true);
            shortcut.cancel();
            assertFalse(shortcut.holdsSneak());
            assertFalse(shortcut.holdsUse());
            shortcut.updateKey(true, true); // Still held when returning from a GUI or focus loss.
            shortcut.endTick(true);
            assertFalse(shortcut.beginTick(true));
            assertFalse(shortcut.holdsSneak());
            shortcut.updateKey(false, false);
            shortcut.updateKey(true, true);
            shortcut.endTick(true);
            assertTrue(shortcut.beginTick(true));
        }
    }

    @Test
    public void inabilityToEnterSneakDoesNotLeaveATapStuckOrProduceAStandingUse() {
        SneakUseGesture shortcut = new SneakUseGesture();
        shortcut.updateKey(true, false);
        for (int i = 0; i < 10; i++) {
            assertFalse(shortcut.beginTick(false));
            shortcut.endTick(false);
        }
        assertFalse(shortcut.holdsSneak());
        assertFalse(shortcut.holdsUse());
        shortcut.endTick(true);
        assertFalse(shortcut.beginTick(true));
    }

    private static SneakUseGesture readyShortcut() {
        SneakUseGesture shortcut = new SneakUseGesture();
        shortcut.updateKey(true, true);
        shortcut.endTick(true);
        return shortcut;
    }
}
