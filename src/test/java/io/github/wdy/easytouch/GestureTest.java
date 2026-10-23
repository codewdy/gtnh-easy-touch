package io.github.wdy.easytouch;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureTest {
    @Test
    public void holdsMappingWhenAimChangesAndButtonsOverlap() {
        Gesture gesture = new Gesture();
        assertEquals(Action.USE, gesture.press(0, Action.USE));
        assertEquals(Action.ATTACK, gesture.press(1, Action.ATTACK));
        assertTrue(gesture.holds(Action.USE));
        assertTrue(gesture.holds(Action.ATTACK));
        gesture.release(0);
        assertFalse(gesture.holds(Action.USE));
        assertTrue(gesture.holds(Action.ATTACK));
        gesture.release(1);
        assertFalse(gesture.active());
        assertEquals(Action.ATTACK, gesture.press(0, Action.ATTACK));
    }

    @Test
    public void secondaryFirstStillUsesOppositeAction() {
        Gesture gesture = new Gesture();
        assertEquals(Action.ATTACK, gesture.press(1, Action.USE));
        assertEquals(Action.USE, gesture.press(0, Action.ATTACK));
        gesture.reset();
        assertFalse(gesture.holds(Action.ATTACK));
        assertFalse(gesture.holds(Action.USE));
        assertNull(gesture.primary());
    }

    @Test
    public void duplicatePressAndReleaseDoNotRetarget() {
        Gesture gesture = new Gesture();
        gesture.press(0, Action.ATTACK);
        assertEquals(Action.ATTACK, gesture.press(0, Action.USE));
        gesture.release(1);
        assertTrue(gesture.holds(Action.ATTACK));
        gesture.release(0);
        gesture.release(0);
        assertEquals(Action.USE, gesture.press(0, Action.USE));
    }
}
