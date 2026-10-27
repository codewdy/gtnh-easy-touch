package io.github.wdy.easytouch.layout;

import java.util.*;
import io.github.wdy.easytouch.touch.TouchInput;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjglx.input.Mouse;

/** Serializes mouse events with their modifier snapshot, before vanilla's mouse loop. */
@Lwjgl3Aware
public final class VirtualMouse {
    private static final class Request {
        final int button; final boolean down; final Set<Integer> modifiers; final Runnable motion;
        Request(int button, boolean down, Set<Integer> modifiers) { this.button=button; this.down=down; this.modifiers=modifiers; this.motion=null; }
        Request(Runnable motion,Set<Integer> modifiers) { this.button=-1; this.down=false; this.modifiers=modifiers; this.motion=motion; }
    }
    private final Deque<Request> pending = new ArrayDeque<Request>();
    private final Set<Integer> held = new HashSet<Integer>();
    private final VirtualKeyboard keyboard;
    public VirtualMouse(VirtualKeyboard keyboard) { this.keyboard=keyboard; }
    public void event(int code, boolean down) { pending.add(new Request(code+100,down,keyboard.queuedKeys())); }
    public void motion(Runnable motion) { pending.add(new Request(motion,keyboard.queuedKeys())); }
    public int flags() {
        int flags=0;
        for(int button:held) flags |= 1 << (Mouse.lwjglToSdlMouseButton(button)-1);
        return flags;
    }
    public boolean held(int button) { return held.contains(button); }
    /** Render-time pointer updates must not cross a button edge awaiting vanilla's input tick. */
    public void flushMotion() {
        while (!pending.isEmpty() && pending.peek().motion!=null) {
            Request event=pending.remove();
            keyboard.mouseModifiers(event.modifiers);
            event.motion.run();
        }
    }
    public void flush() {
        keyboard.mouseModifiers(Collections.emptySet());
        boolean sentButton=false;
        while (!pending.isEmpty()) {
            Request event=pending.peek();
            // Keep clicks visible for a tick, but execute adjacent motion in event order.
            if (event.motion==null && sentButton) break;
            pending.remove();
            keyboard.mouseModifiers(event.modifiers);
            if (event.motion!=null) { event.motion.run(); continue; }
            sentButton=true;
            if(event.down)held.add(event.button); else held.remove(event.button);
            if(event.down || !TouchInput.mouseButtonDown(event.button) && !(event.button==0 && TouchInput.guiMouseHeld()))
                Mouse.addButtonEvent(event.button,event.down);
        }
    }

    public void cancel() {
        pending.clear();
        for(int button:held) if(!TouchInput.mouseButtonDown(button) && !(button==0 && TouchInput.guiMouseHeld())) Mouse.addButtonEvent(button,false);
        held.clear(); keyboard.mouseModifiers(Collections.emptySet());
    }
}
