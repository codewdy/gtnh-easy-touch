package io.github.wdy.easytouch;

import io.github.wdy.easytouch.input.TouchLook;
import io.github.wdy.easytouch.touch.TouchSample;
import org.junit.Test;
import static io.github.wdy.easytouch.touch.TouchSample.Kind.*;
import static org.junit.Assert.*;

public class TouchLookTest {
    private static final class Port implements TouchLook.Port {
        boolean world = true;
        float dx, dy;
        int turns, clicks, presses, releases;
        boolean held;
        public int width() { return 2000; }
        public int height() { return 1000; }
        public void click() { clicks++; }
        public void press() { presses++; held = true; }
        public void release() { releases++; held = false; }
        public boolean inWorld() { return world; }
        public void turn(float x, float y) { dx += x; dy += y; turns++; }
    }

    private TouchSample sample(TouchSample.Kind kind, long device, long finger, float x, float y) {
        return new TouchSample(kind, device, finger, 0, 42, x, y, 0, 0, 1);
    }

    private TouchSample timed(TouchSample.Kind kind, long ms, float x, float y) {
        return new TouchSample(kind, 1, 1, ms * 1_000_000L, 42, x, y, 0, 0, 1);
    }

    @Test public void tapWaitsForReleaseAndDurationGapDoesNothing() {
        for (long ms : new long[] { 0, 249, 250, 251, 499, 500, 900 }) {
            Port port = new Port();
            TouchLook look = new TouchLook(port);
            look.accept(timed(DOWN, 1000, .5f, .5f));
            assertEquals(0, port.clicks);
            assertEquals(0, port.presses);
            look.accept(timed(UP, 1000 + ms, .5f, .5f));
            assertEquals("duration " + ms, ms <= 250 || ms >= 500 ? 1 : 0, port.clicks);
            assertEquals(0, port.turns);
            assertFalse(port.held);
        }
    }

    @Test public void stationaryHoldStartsAt500msOnceAndReleasesOnDrag() {
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(timed(DOWN, 1000, .5f, .5f));
        look.tick(1499_000_000L);
        assertEquals(0, port.presses);
        look.tick(1500_000_000L);
        look.tick(1700_000_000L);
        assertEquals(1, port.presses);
        assertTrue(port.held);
        look.accept(timed(MOVE, 1800, .6f, .5f));
        assertFalse(port.held);
        assertEquals(1, port.releases);
        look.accept(timed(MOVE, 1900, .5f, .5f));
        look.tick(2400_000_000L);
        look.accept(timed(UP, 2500, .5f, .5f));
        assertEquals(1, port.presses);
        assertEquals(0, port.clicks);
    }

    @Test public void thresholdUsesShortEdgeAndSmallJitterDoesNotRotate() {
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(timed(DOWN, 1000, .5f, .5f));
        look.accept(timed(MOVE, 1050, .504f, .5f)); // 8 px < 10 px threshold
        assertEquals(0, port.turns);
        look.accept(timed(MOVE, 1100, .506f, .5f)); // 12 px > threshold
        assertEquals(1, port.turns);
        assertEquals(.006f, port.dx, .00001f);
        look.accept(timed(MOVE, 1150, .5f, .5f));
        look.accept(timed(UP, 1200, .5f, .5f));
        assertEquals(0, port.clicks); // Returning to origin never turns a drag into a tap.
    }

    @Test public void upDisplacementCannotBypassDragThreshold() {
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(timed(DOWN, 1000, .5f, .5f));
        look.accept(timed(UP, 1100, .6f, .5f));
        assertEquals(0, port.clicks);
    }

    @Test public void cancellationReleasesHoldAndNeverClicks() {
        for (TouchSample.Kind kind : new TouchSample.Kind[] { UP, CANCEL, RESET }) {
            Port port = new Port();
            TouchLook look = new TouchLook(port);
            look.accept(timed(DOWN, 1000, .5f, .5f));
            look.tick(1500_000_000L);
            look.accept(timed(kind, 1800, .5f, .5f));
            assertFalse(port.held);
            assertEquals(1, port.releases);
            assertEquals(0, port.clicks);
        }
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(timed(DOWN, 1000, .5f, .5f));
        look.tick(1500_000_000L);
        port.world = false;
        look.tick(1600_000_000L);
        assertFalse(port.held);
        port.world = true;
        look.accept(timed(UP, 1800, .5f, .5f));
        assertEquals(0, port.clicks);
    }

    @Test public void dragUsesRelativeCoordinatesAndOnlyTheOwningContact() {
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(sample(DOWN, 1, 1, .2f, .3f));
        look.accept(sample(DOWN, 2, 1, .5f, .5f));
        look.accept(sample(MOVE, 2, 1, .9f, .9f));
        look.accept(sample(MOVE, 1, 2, .9f, .9f));
        look.accept(sample(MOVE, 1, 1, .4f, .2f));
        assertEquals(.2f, port.dx, .00001f);
        assertEquals(-.1f, port.dy, .00001f);
        assertEquals(1, port.turns);
        look.accept(sample(UP, 1, 1, .4f, .2f));
        look.accept(sample(MOVE, 2, 1, .8f, .8f));
        assertEquals(1, port.turns);
        look.accept(sample(DOWN, 2, 2, .5f, .5f));
        look.accept(sample(MOVE, 2, 2, .6f, .6f));
        assertEquals(2, port.turns);
    }

    @Test public void guiContactsCannotStartLookAndLeavingWorldCancelsDrag() {
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        port.world = false;
        look.accept(sample(DOWN, 1, 1, .2f, .3f));
        port.world = true;
        look.accept(sample(MOVE, 1, 1, .4f, .5f));
        assertEquals(0, port.turns);
        look.accept(sample(DOWN, 1, 1, .2f, .3f));
        port.world = false;
        look.accept(sample(MOVE, 1, 1, .4f, .5f));
        port.world = true;
        look.accept(sample(MOVE, 1, 1, .6f, .7f));
        assertEquals(0, port.turns);
    }

    @Test public void cancellationAndResetRequireANewPress() {
        for (TouchSample.Kind end : new TouchSample.Kind[] { UP, CANCEL, RESET }) {
            Port port = new Port();
            TouchLook look = new TouchLook(port);
            look.accept(sample(DOWN, 1, 1, .2f, .3f));
            look.accept(sample(end, 1, 1, .2f, .3f));
            look.accept(sample(MOVE, 1, 1, .4f, .5f));
            assertEquals(0, port.turns);
        }
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(sample(DOWN, 1, 1, .2f, .3f));
        look.cancel();
        look.accept(sample(MOVE, 1, 1, .4f, .5f));
        assertEquals(0, port.turns);
    }

    @Test public void simulatedMouseAndOrphanMovesDoNotRotate() {
        Port port = new Port();
        TouchLook look = new TouchLook(port);
        look.accept(sample(DOWN, -1, 1, .2f, .3f));
        look.accept(sample(MOVE, -1, 1, .4f, .5f));
        look.accept(sample(MOVE, 1, 1, .4f, .5f));
        assertEquals(0, port.turns);
        look.accept(sample(DOWN, 1, 1, .2f, .3f));
        look.accept(sample(MOVE, 1, 1, Float.NaN, .5f));
        look.accept(sample(MOVE, 1, 1, .3f, .4f));
        assertEquals(.1f, port.dx, .00001f);
        assertEquals(.1f, port.dy, .00001f);
    }
}
