package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.TouchMouse;
import io.github.wdy.easytouch.touch.TouchSample;
import org.junit.Test;
import static io.github.wdy.easytouch.touch.TouchSample.Kind.*;
import static org.junit.Assert.*;

public class TouchMouseTest {
    private static final class Port implements TouchMouse.Port {
        boolean active = true, held;
        int presses, releases, moves;
        float x, y;
        public boolean active() { return active; }
        public void move(float x, float y) { this.x = x; this.y = y; moves++; }
        public void button(boolean down) { held = down; if (down) presses++; else releases++; }
    }
    private TouchSample sample(TouchSample.Kind kind, long device, long finger, float x) {
        return new TouchSample(kind, device, finger, 0, 42, x, .5f, 0, 0, 1);
    }
    @Test public void guiPressIsImmediateAndDragKeepsLeftHeld() {
        Port port = new Port();
        TouchMouse mouse = new TouchMouse(port);
        mouse.accept(sample(DOWN, 1, 1, .1f));
        assertEquals(1, port.presses);
        assertTrue(port.held);
        assertEquals(.1f, port.x, 0);
        mouse.accept(sample(MOVE, 1, 1, .8f));
        assertTrue(port.held);
        assertEquals(.8f, port.x, 0);
        mouse.accept(sample(UP, 1, 1, .9f));
        assertFalse(port.held);
        assertEquals(1, port.releases);
        assertEquals(.9f, port.x, 0);
    }
    @Test public void secondFingerAndSimulatedMouseCannotTakeOwnership() {
        Port port = new Port();
        TouchMouse mouse = new TouchMouse(port);
        mouse.accept(sample(DOWN, -1, 1, .1f));
        assertEquals(0, port.presses);
        mouse.accept(sample(DOWN, 1, 1, .1f));
        mouse.accept(sample(DOWN, 2, 1, .2f));
        mouse.accept(sample(MOVE, 2, 1, .8f));
        mouse.accept(sample(UP, 2, 1, .8f));
        assertEquals(.1f, port.x, 0);
        assertTrue(port.held);
        mouse.accept(sample(UP, 1, 1, .1f));
        mouse.accept(sample(MOVE, 2, 1, .8f));
        assertEquals(1, port.presses);
    }
    @Test public void boundariesReleaseAndRequireFreshPress() {
        for (TouchSample.Kind kind : new TouchSample.Kind[] { CANCEL, RESET }) {
            Port port = new Port();
            TouchMouse mouse = new TouchMouse(port);
            mouse.accept(sample(DOWN, 1, 1, .1f));
            mouse.accept(sample(kind, 1, 1, .1f));
            mouse.accept(sample(MOVE, 1, 1, .8f));
            assertFalse(port.held);
            assertEquals(1, port.releases);
        }
        Port port = new Port();
        TouchMouse mouse = new TouchMouse(port);
        mouse.accept(sample(DOWN, 1, 1, .1f));
        port.active = false;
        mouse.accept(sample(MOVE, 1, 1, .2f));
        port.active = true;
        mouse.accept(sample(MOVE, 1, 1, .8f));
        assertFalse(port.held);
        assertEquals(1, port.presses);
    }
}
