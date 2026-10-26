package io.github.wdy.easytouch.touch;

import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.sdl.SDL_Event;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.junit.Assert.*;

/** Uses real SDL event queue + native callback trampoline, with injected events instead of touch hardware. */
public class SdlTouchSourceIntegrationTest {
    private TouchQueue queue;
    private SdlTouchSource source;

    @BeforeClass public static void init() { assertTrue(SDL_Init(SDL_INIT_EVENTS)); }
    @AfterClass public static void quit() { SDL_Quit(); }

    @Before public void open() {
        SDL_FlushEvents(SDL_EVENT_FIRST, SDL_EVENT_LAST);
        queue = new TouchQueue(64);
        source = new SdlTouchSource(queue, () -> 42);
    }

    @After public void close() { source.close(); }

    private void touch(int kind, long device, long finger, int window, float x) {
        try (SDL_Event e = SDL_Event.calloc()) {
            e.tfinger().type(kind).touchID(device).fingerID(finger).windowID(window)
                .x(x).y(.5f).dx(.1f).dy(-.2f).pressure(.7f);
            assertTrue(SDL_PushEvent(e));
            // Mutate and free the native memory immediately: the bridge must own a copy.
            e.tfinger().x(.99f).fingerID(9999);
        }
    }

    private void focus(int kind, int window) {
        try (SDL_Event e = SDL_Event.calloc()) {
            e.window().type(kind).windowID(window);
            assertTrue(SDL_PushEvent(e));
        }
    }

    @Test public void copiesTwoFingersBeforeConsumptionAndLeavesTheNativeQueueIntact() {
        touch(SDL_EVENT_FINGER_DOWN, 17, Long.MAX_VALUE, 42, .1f);
        touch(SDL_EVENT_FINGER_DOWN, 17, 2, 42, .2f);
        touch(SDL_EVENT_FINGER_MOTION, 17, Long.MAX_VALUE, 42, .8f);
        touch(SDL_EVENT_FINGER_UP, 17, Long.MAX_VALUE, 42, .8f);
        touch(SDL_EVENT_FINGER_CANCELED, 17, 2, 42, .2f);
        assertNull(source.failure());
        List<TouchSample> copied = queue.drain();
        assertEquals(5, copied.size());
        assertEquals(Long.MAX_VALUE, copied.get(0).fingerId);
        assertEquals(.1f, copied.get(0).x, 0);
        assertEquals(.1f, copied.get(0).dx, 0);
        assertEquals(-.2f, copied.get(0).dy, 0);
        assertEquals(.7f, copied.get(0).pressure, 0);
        assertTrue(copied.get(0).timestamp > 0);
        TouchTracker tracker = new TouchTracker();
        for (TouchSample sample : copied) tracker.accept(sample);
        assertEquals(2, tracker.peakNative);
        assertEquals(0, tracker.nativeCount());
        try (SDL_Event.Buffer original = SDL_Event.calloc(16)) {
            int count = SDL_PeepEvents(original, SDL_GETEVENT, SDL_EVENT_FINGER_DOWN, SDL_EVENT_FINGER_CANCELED);
            assertEquals("The watch must not steal events from lwjgl3ify", 5, count);
        }
    }

    @Test public void distinguishesMouseSimulationAndFiltersOtherWindows() {
        touch(SDL_EVENT_FINGER_DOWN, 17, 1, 43, .1f);
        touch(SDL_EVENT_FINGER_DOWN, -1L, 1, 42, .2f);
        try (SDL_Event e = SDL_Event.calloc()) {
            e.motion().type(SDL_EVENT_MOUSE_MOTION).windowID(42).x(20).y(10);
            assertTrue(SDL_PushEvent(e));
        }
        List<TouchSample> copied = queue.drain();
        assertEquals(1, copied.size());
        assertTrue(copied.get(0).simulated());
        assertEquals(1, source.mouseEvents());
        assertEquals(1, source.foreignEvents());
        TouchTracker tracker = new TouchTracker();
        tracker.accept(copied.get(0));
        assertEquals(0, tracker.peakNative);
    }

    @Test public void nativeFocusLossResetsContactsAndIgnoresFurtherInputUntilFocusReturns() {
        touch(SDL_EVENT_FINGER_DOWN, 17, 1, 42, .1f);
        TouchTracker tracker = new TouchTracker();
        for (TouchSample sample : queue.drain()) tracker.accept(sample);
        focus(SDL_EVENT_WINDOW_FOCUS_LOST, 42);
        touch(SDL_EVENT_FINGER_MOTION, 17, 1, 42, .2f);
        for (TouchSample sample : queue.drain()) tracker.accept(sample);
        assertEquals(0, tracker.nativeCount());
        focus(SDL_EVENT_WINDOW_FOCUS_GAINED, 42);
        touch(SDL_EVENT_FINGER_DOWN, 17, 2, 42, .3f);
        for (TouchSample sample : queue.drain()) tracker.accept(sample);
        assertEquals(1, tracker.nativeCount());
    }

    @Test public void unregisterStopsDeliveryWithoutDisablingSdlEvents() {
        source.close();
        queue.drain();
        touch(SDL_EVENT_FINGER_DOWN, 17, 1, 42, .1f);
        assertTrue(queue.drain().isEmpty());
        assertTrue(SDL_EventEnabled(SDL_EVENT_FINGER_DOWN));
        try (SDL_Event.Buffer original = SDL_Event.calloc(8)) {
            assertEquals(1, SDL_PeepEvents(original, SDL_GETEVENT, SDL_EVENT_FINGER_DOWN, SDL_EVENT_FINGER_DOWN));
        }
    }

    @Test public void callbackFailureIsContainedAndReleasesContacts() {
        source.close();
        queue.drain();
        source = new SdlTouchSource(queue,
            () -> { throw new IllegalStateException("fixture window failure"); });
        touch(SDL_EVENT_FINGER_DOWN, 17, 1, 42, .1f);
        assertNotNull(source.failure());
        List<TouchSample> copied = queue.drain();
        assertEquals(1, copied.size());
        assertEquals(TouchSample.Kind.RESET, copied.get(0).kind);
        touch(SDL_EVENT_FINGER_DOWN, 17, 2, 42, .2f);
        assertTrue(queue.drain().isEmpty());
    }
}
