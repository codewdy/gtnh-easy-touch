package io.github.wdy.easytouch.layout;

import java.util.*;
import io.github.wdy.easytouch.touch.TouchInput;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjglx.input.Mouse;

/** Serializes mouse events with their modifier snapshot, before vanilla's mouse loop. */
@Lwjgl3Aware
public final class VirtualMouse {
    private static final class Request {
        final int button; final boolean down; final Set<Integer> modifiers;
        Request(int button, boolean down, Set<Integer> modifiers) { this.button=button; this.down=down; this.modifiers=modifiers; }
    }
    private final Deque<Request> pending = new ArrayDeque<Request>();
    private final Set<Integer> held = new HashSet<Integer>();
    private final VirtualKeyboard keyboard;
    public VirtualMouse(VirtualKeyboard keyboard) { this.keyboard=keyboard; }
    public void event(int code, boolean down) { pending.add(new Request(code+100,down,keyboard.queuedKeys())); }
    public int flags() {
        int flags=0;
        for(int button:held) flags |= 1 << (Mouse.lwjglToSdlMouseButton(button)-1);
        return flags;
    }
    public boolean held(int button) { return held.contains(button); }
    public void flush() {
        keyboard.mouseModifiers(Collections.emptySet());
        Request event=pending.poll();
        if(event==null)return;
        keyboard.mouseModifiers(event.modifiers);
        if(event.down)held.add(event.button); else held.remove(event.button);
        if(event.down || !TouchInput.mouseButtonDown(event.button) && !(event.button==0 && TouchInput.guiMouseHeld()))
            Mouse.addButtonEvent(event.button,event.down);
    }
    public void cancel() {
        pending.clear();
        for(int button:held) if(!TouchInput.mouseButtonDown(button) && !(button==0 && TouchInput.guiMouseHeld())) Mouse.addButtonEvent(button,false);
        held.clear(); keyboard.mouseModifiers(Collections.emptySet());
    }
}
