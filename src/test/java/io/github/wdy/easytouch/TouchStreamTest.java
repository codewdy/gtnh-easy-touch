package io.github.wdy.easytouch;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import io.github.wdy.easytouch.touch.SdlTouchSource;
import io.github.wdy.easytouch.touch.TouchQueue;
import io.github.wdy.easytouch.touch.TouchSample;
import io.github.wdy.easytouch.touch.TouchTracker;
import org.junit.Test;
import static io.github.wdy.easytouch.touch.TouchSample.Kind.*;
import static org.junit.Assert.*;

public class TouchStreamTest {
    private TouchSample sample(TouchSample.Kind kind, long device, long finger, float x) {
        return new TouchSample(kind, device, finger, 1234, 42, x, .5f, .1f, 0, .7f);
    }

    @Test
    public void simultaneousContactsHaveIndependentDeviceAndFingerIdentity() {
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, 10, 1, .1f));
        state.accept(sample(DOWN, 10, 2, .2f));
        state.accept(sample(DOWN, 20, 1, .3f));
        state.accept(sample(MOVE, 10, 1, .8f));
        assertEquals(3, state.nativeCount());
        assertEquals(.8f, state.contacts().get(0).x, 0);
        assertEquals(.2f, state.contacts().get(1).x, 0);
        state.accept(sample(UP, 10, 1, .8f));
        assertEquals(2, state.nativeCount());
        state.accept(sample(CANCEL, 20, 1, .3f));
        assertEquals(1, state.nativeCount());
        assertEquals(2, state.contacts().get(0).fingerId);
        assertEquals(3, state.peakNative);
    }

    @Test
    public void mouseSimulatedTouchDoesNotProveNativeMultitouch() {
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, -1L, 1, .1f));
        state.accept(sample(MOVE, -1L, 1, .2f));
        assertEquals(0, state.nativeCount());
        assertEquals(0, state.peakNative);
        assertEquals(0, state.nativeEvents);
        assertEquals(2, state.simulatedEvents);
        state.accept(sample(DOWN, 10, 1, .1f));
        assertEquals(1, state.nativeCount());
        assertEquals(2, state.contacts().size());
    }

    @Test
    public void focusLossDiscardsPendingPressesAndMoveCannotResurrectReleasedContacts() {
        TouchQueue queue = new TouchQueue(8);
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, 10, 1, .1f));
        queue.offer(sample(DOWN, 10, 2, .2f));
        queue.focus(false);
        queue.offer(sample(DOWN, 10, 3, .3f));
        for (TouchSample s : queue.drain()) state.accept(s);
        assertEquals(0, state.nativeCount());
        queue.focus(true);
        queue.offer(sample(MOVE, 10, 1, .8f));
        for (TouchSample s : queue.drain()) state.accept(s);
        assertEquals(0, state.nativeCount());
        queue.offer(sample(DOWN, 10, 4, .4f));
        for (TouchSample s : queue.drain()) state.accept(s);
        assertEquals(1, state.nativeCount());
    }

    @Test
    public void queueOverflowReleasesOldContactsBeforeDeliveringMoreEvents() {
        TouchQueue queue = new TouchQueue(2);
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, 10, 1, .1f));
        queue.offer(sample(MOVE, 10, 1, .2f));
        queue.offer(sample(MOVE, 10, 1, .3f));
        queue.offer(sample(UP, 10, 1, .3f));
        List<TouchSample> batch = queue.drain();
        assertEquals(2, batch.size());
        assertEquals(RESET, batch.get(0).kind);
        for (TouchSample s : batch) state.accept(s);
        assertEquals(0, state.nativeCount());
        assertEquals(2, queue.dropped());
        assertTrue(queue.drain().isEmpty());
    }

    @Test
    public void resetIsDeliveredEvenWithoutAnyFurtherNativeEvents() {
        TouchQueue queue = new TouchQueue(4);
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, 10, 1, .1f));
        queue.reset();
        for (TouchSample s : queue.drain()) state.accept(s);
        assertEquals(0, state.nativeCount());
    }

    @Test
    public void invalidCoordinatesAreRejectedButOffWindowCoordinatesArePreserved() {
        TouchQueue queue = new TouchQueue(8);
        queue.offer(sample(DOWN, 10, 1, Float.NaN));
        queue.offer(sample(DOWN, 10, 2, Float.POSITIVE_INFINITY));
        queue.offer(sample(DOWN, 10, 3, -0.2f));
        List<TouchSample> batch = queue.drain();
        assertEquals(1, batch.size());
        assertEquals(-0.2f, batch.get(0).x, 0);
    }

    @Test
    public void resetStatsKeepsHeldContactsAndTheirCurrentPeak() {
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, 10, 1, .1f));
        state.accept(sample(DOWN, 10, 2, .2f));
        state.accept(sample(UP, 10, 2, .2f));
        state.resetStats();
        assertEquals(1, state.nativeCount());
        assertEquals(1, state.peakNative);
        assertEquals(0, state.nativeEvents);
        assertNull(state.last);
    }

    @Test
    public void nativeThreadCanPublishWhileClientDrainsWithoutExceedingCapacity() throws Exception {
        TouchQueue queue = new TouchQueue(16);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                started.countDown();
                for (int i = 0; i < 5000; i++) queue.offer(sample(MOVE, 10, 1, .1f));
                queue.offer(sample(UP, 10, 1, .1f));
            } catch (Throwable t) { failure.set(t); }
        });
        worker.start();
        started.await();
        TouchTracker state = new TouchTracker();
        state.accept(sample(DOWN, 10, 1, .1f));
        do {
            List<TouchSample> batch = queue.drain();
            assertTrue(batch.size() <= 16);
            for (TouchSample s : batch) state.accept(s);
            worker.join(1);
        } while (worker.isAlive());
        for (TouchSample s : queue.drain()) state.accept(s);
        assertNull(failure.get());
        assertEquals(0, state.nativeCount());
    }

    @Test
    public void missingSdlFailsImmediately() {
        try {
            // Ordinary tests deliberately omit the required SDL runtime; native tests supply it separately.
            new SdlTouchSource(new TouchQueue(8), () -> 42);
            fail("A missing SDL runtime must prevent construction");
        } catch (NoClassDefFoundError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().startsWith("org/lwjgl/"));
        }
    }
}
