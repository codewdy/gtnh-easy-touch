package io.github.wdy.easytouch.touch;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLTouch.SDL_TOUCH_MOUSEID;
import static org.junit.Assert.*;

public class SdlTouchMouseFilterTest {
    private SdlTouchMouseFilter filter;
    @BeforeClass public static void init() { assertTrue(SDL_Init(SDL_INIT_EVENTS)); }
    @AfterClass public static void quit() { SDL_Quit(); }
    @Before public void open() {
        SDL_FlushEvents(SDL_EVENT_FIRST, SDL_EVENT_LAST);
        filter = new SdlTouchMouseFilter(() -> 42);
    }
    @After public void close() { filter.close(); }

    private boolean button(int device, int window, int button, boolean down) {
        try (SDL_Event event = SDL_Event.calloc()) {
            event.button().type(down ? SDL_EVENT_MOUSE_BUTTON_DOWN : SDL_EVENT_MOUSE_BUTTON_UP)
                .which(device).windowID(window).button((byte) button).down(down);
            return SDL_PushEvent(event);
        }
    }

    @Test public void syntheticMouseIsDroppedInAllModesWhileRealMouseAndTouchPass() {
        assertFalse(button(SDL_TOUCH_MOUSEID, 42, 1, true));
        assertFalse(button(SDL_TOUCH_MOUSEID, 42, 1, false));
        assertEquals(0, filter.physicalButtons());
        try (SDL_Event event = SDL_Event.calloc()) {
            event.motion().type(SDL_EVENT_MOUSE_MOTION).which(SDL_TOUCH_MOUSEID).windowID(42).x(20).y(30);
            assertFalse(SDL_PushEvent(event));
            event.tfinger().type(SDL_EVENT_FINGER_DOWN).touchID(17).fingerID(1).windowID(42).x(.2f).y(.3f);
            assertTrue(SDL_PushEvent(event));
        }
        assertTrue(button(7, 42, 1, true));
        assertTrue(filter.physicalButtonDown(0));
        assertFalse(filter.physicalButtonDown(1));
        assertTrue(button(7, 42, 1, false));
        assertFalse(filter.physicalButtonDown(0));
        assertNull(filter.failure());
        try (SDL_Event.Buffer events = SDL_Event.calloc(16)) {
            assertEquals(3, SDL_PeepEvents(events, SDL_GETEVENT, SDL_EVENT_FIRST, SDL_EVENT_LAST));
        }
    }

    @Test public void realDevicesMergeAndOtherWindowsAreUntouched() {
        assertTrue(button(SDL_TOUCH_MOUSEID, 43, 1, true));
        assertTrue(button(7, 42, 1, true));
        assertTrue(button(8, 42, 1, true));
        assertTrue(button(7, 42, 1, false));
        assertTrue(filter.physicalButtonDown(0));
        assertTrue(button(8, 42, 1, false));
        assertFalse(filter.physicalButtonDown(0));
        assertTrue(button(7, 42, 3, true));
        assertTrue(filter.physicalButtonDown(1));
        assertTrue(button(7, 42, 2, true));
        assertTrue(filter.physicalButtonDown(2));
        try (SDL_Event event = SDL_Event.calloc()) {
            event.window().type(SDL_EVENT_WINDOW_FOCUS_LOST).windowID(42);
            assertTrue(SDL_PushEvent(event));
        }
        assertEquals(0, filter.physicalButtons());
    }

    @Test public void previousFilterIsChainedAndRestoredWithItsUserdata() {
        filter.close();
        final int[] calls = { 0 };
        try (SDL_EventFilter previous = SDL_EventFilter.create((data, address) -> {
            assertEquals(123, data);
            calls[0]++;
            return SDL_Event.create(address).type() != SDL_EVENT_MOUSE_BUTTON_UP;
        })) {
            SDL_SetEventFilter(previous, 123);
            filter = new SdlTouchMouseFilter(() -> 42);
            assertTrue(button(7, 42, 1, true));
            assertFalse(button(7, 42, 1, false));
            assertEquals(2, calls[0]);
            filter.close();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer callback = stack.callocPointer(1), data = stack.callocPointer(1);
                assertTrue(SDL_GetEventFilter(callback, data));
                assertEquals(previous.address(), callback.get(0));
                assertEquals(123, data.get(0));
            }
            assertTrue(button(SDL_TOUCH_MOUSEID, 42, 1, true));
        } finally {
            SDL_SetEventFilter(null, 0);
        }
    }

    @Test public void closeRestoresNativeEventsAndCallbackFailureCannotEscape() {
        filter.close();
        assertTrue(button(SDL_TOUCH_MOUSEID, 42, 1, true));
        filter = new SdlTouchMouseFilter(() -> { throw new IllegalStateException("fixture"); });
        assertTrue(button(7, 42, 1, true));
        assertNotNull(filter.failure());
        assertTrue(button(SDL_TOUCH_MOUSEID, 42, 1, true));
    }
}
