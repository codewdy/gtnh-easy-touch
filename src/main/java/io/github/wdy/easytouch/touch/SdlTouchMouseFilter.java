package io.github.wdy.easytouch.touch;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLTouch.SDL_TOUCH_MOUSEID;

/** Filters only SDL's touch-generated mouse events, before lwjgl3ify loses the device identity. */
@Lwjgl3Aware
public final class SdlTouchMouseFilter implements AutoCloseable {
    private final IntSupplier window;
    private final SDL_EventFilter previous;
    private final long previousData;
    private final SDL_EventFilter callback;
    private final Map<Integer, Integer> physicalButtons = new HashMap<>();
    private volatile boolean closed;
    private volatile Throwable failure;

    public SdlTouchMouseFilter(IntSupplier window) {
        this.window = window;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filter = stack.callocPointer(1), data = stack.callocPointer(1);
            SDL_GetEventFilter(filter, data);
            previous = SDL_EventFilter.createSafe(filter.get(0));
            previousData = data.get(0);
        }
        callback = SDL_EventFilter.create(this::filter);
        SDL_SetEventFilter(callback, 0);
    }

    public Throwable failure() { return failure; }

    private boolean ourWindow(int id) {
        int expected = window.getAsInt();
        return expected != 0 && (id == 0 || id == expected);
    }

    private boolean filter(long userdata, long address) {
        try {
            boolean accepted = closed || failure != null || accept(SDL_Event.create(address));
            // Observe real releases even when another filter rejects their delivery.
            boolean previousAccepted = previous == null || previous.invoke(previousData, address);
            return accepted && previousAccepted;
        } catch (Throwable error) {
            // Never unwind across the native callback. TouchInput disables the pipeline on the client thread.
            failure = error;
            return true;
        }
    }

    private synchronized boolean accept(SDL_Event event) {
        int type = event.type();
        if (type == SDL_EVENT_WINDOW_FOCUS_LOST && ourWindow(event.window().windowID())) {
            physicalButtons.clear();
        } else if (type == SDL_EVENT_MOUSE_MOTION && ourWindow(event.motion().windowID())) {
            if (event.motion().which() == SDL_TOUCH_MOUSEID) return false;
        } else if ((type == SDL_EVENT_MOUSE_BUTTON_DOWN || type == SDL_EVENT_MOUSE_BUTTON_UP)
                && ourWindow(event.button().windowID())) {
            int device = event.button().which();
            int button = event.button().button() & 0xff;
            boolean down = type == SDL_EVENT_MOUSE_BUTTON_DOWN;
            if (device == SDL_TOUCH_MOUSEID) return false;
            if (button >= 1 && button <= 32) {
                int mask = 1 << (button - 1);
                int state = physicalButtons.containsKey(device) ? physicalButtons.get(device) : 0;
                physicalButtons.put(device, down ? state | mask : state & ~mask);
            }
        }
        if (type == SDL_EVENT_MOUSE_WHEEL && ourWindow(event.wheel().windowID())
                && event.wheel().which() == SDL_TOUCH_MOUSEID) return false;
        return true;
    }

    public synchronized int physicalButtons() {
        int result = 0;
        for (int state : physicalButtons.values()) result |= state;
        return result;
    }

    /** SDL's aggregate mouse state includes filtered synthetic presses; use real devices for key restoration. */
    public synchronized boolean physicalButtonDown(int lwjglButton) {
        int sdlButton = lwjglButton == 0 ? 1 : lwjglButton == 1 ? 3 : lwjglButton == 2 ? 2 : lwjglButton + 1;
        if (sdlButton < 1 || sdlButton > 32) return false;
        int mask = 1 << (sdlButton - 1);
        for (int state : physicalButtons.values()) if ((state & mask) != 0) return true;
        return false;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filter = stack.callocPointer(1), data = stack.callocPointer(1);
            SDL_GetEventFilter(filter, data);
            if (filter.get(0) == callback.address()) {
                SDL_SetEventFilter(previous, previousData);
                callback.free();
            }
            // A later filter may have chained ours. Leave its trampoline alive as a pass-through in that case.
        }
    }
}
