package io.github.wdy.easytouch.touch;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.sdl.SDL_TouchFingerEvent;
import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLEvents.*;

/** SDL3 event watch using the LWJGL bindings supplied by lwjgl3ify. */
public final class SdlTouchSource implements AutoCloseable {
    private final TouchQueue queue;
    private final IntSupplier window;
    private SDL_EventFilter callback; // Strong reference for the full native registration lifetime.
    private volatile boolean closed;
    private volatile Throwable failure;
    private final AtomicLong mouseEvents = new AtomicLong();
    private final AtomicLong foreignEvents = new AtomicLong();
    public final String enabledTypes;

    public SdlTouchSource(TouchQueue queue, IntSupplier window) {
        this.queue = queue;
        this.window = window;
        enabledTypes = "DOWN=" + SDL_EventEnabled(SDL_EVENT_FINGER_DOWN)
            + " MOVE=" + SDL_EventEnabled(SDL_EVENT_FINGER_MOTION)
            + " UP=" + SDL_EventEnabled(SDL_EVENT_FINGER_UP)
            + " CANCEL=" + SDL_EventEnabled(SDL_EVENT_FINGER_CANCELED);
        callback = SDL_EventFilter.create(this::onEvent);
        try {
            if (!SDL_AddEventWatch(callback, 0L)) {
                throw new IllegalStateException("SDL_AddEventWatch failed: " + SDL_GetError());
            }
        } catch (RuntimeException | Error error) {
            closed = true;
            callback.free();
            callback = null;
            throw error;
        }
    }

    private boolean ourWindow(int id) {
        int expected = window.getAsInt();
        return expected != 0 && (id == 0 || id == expected);
    }

    /** Native callbacks may run off-thread. Only copy primitive fields into our bounded queue. */
    private boolean onEvent(long userdata, long address) {
        if (closed || failure != null) return true;
        try {
            SDL_Event event = SDL_Event.create(address);
            int type = event.type();
            if (type == SDL_EVENT_FINGER_DOWN || type == SDL_EVENT_FINGER_MOTION
                || type == SDL_EVENT_FINGER_UP || type == SDL_EVENT_FINGER_CANCELED) {
                SDL_TouchFingerEvent value = event.tfinger();
                int targetWindow = value.windowID();
                if (!ourWindow(targetWindow)) {
                    foreignEvents.incrementAndGet();
                    return true;
                }
                TouchSample.Kind kind = type == SDL_EVENT_FINGER_DOWN ? TouchSample.Kind.DOWN
                    : type == SDL_EVENT_FINGER_MOTION ? TouchSample.Kind.MOVE
                    : type == SDL_EVENT_FINGER_UP ? TouchSample.Kind.UP : TouchSample.Kind.CANCEL;
                queue.offer(new TouchSample(kind, value.touchID(), value.fingerID(), value.timestamp(), targetWindow,
                    value.x(), value.y(), value.dx(), value.dy(), value.pressure()));
            } else if (type == SDL_EVENT_WINDOW_FOCUS_LOST || type == SDL_EVENT_WINDOW_FOCUS_GAINED) {
                if (ourWindow(event.window().windowID())) queue.focus(type == SDL_EVENT_WINDOW_FOCUS_GAINED);
            } else if (type == SDL_EVENT_MOUSE_MOTION || type == SDL_EVENT_MOUSE_BUTTON_DOWN
                || type == SDL_EVENT_MOUSE_BUTTON_UP) {
                int targetWindow = type == SDL_EVENT_MOUSE_MOTION ? event.motion().windowID() : event.button().windowID();
                if (ourWindow(targetWindow)) mouseEvents.incrementAndGet();
            }
        } catch (Throwable error) {
            // Never let a Java exception cross the native callback boundary.
            failure = error;
            queue.reset();
        }
        // An event watch does not consume or cancel the event. lwjgl3ify still receives it normally.
        return true;
    }

    public Throwable failure() { return failure; }
    public long mouseEvents() { return mouseEvents.get(); }
    public long foreignEvents() { return foreignEvents.get(); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // Unregister before freeing the native trampoline. No more reads from SDL-owned event memory.
        SDL_RemoveEventWatch(callback, 0L);
        callback.free();
        callback = null;
        queue.reset();
    }
}
