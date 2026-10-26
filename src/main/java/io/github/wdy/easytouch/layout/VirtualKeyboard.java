package io.github.wdy.easytouch.layout;

import java.nio.ByteBuffer;
import java.util.*;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.KeyCodes;
import static org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyboardState;

/** Merge into a separate state snapshot; never write SDL's physical keyboard memory. */
@Lwjgl3Aware
public final class VirtualKeyboard {
    private final Set<Integer> touched = new HashSet<Integer>(), down = new HashSet<Integer>();
    private final Set<Keyboard.KeyEvent> pending = Collections.newSetFromMap(new IdentityHashMap<Keyboard.KeyEvent, Boolean>());
    private Queue<Keyboard.KeyEvent> original;
    private ByteBuffer merged;
    private final Set<Integer> queuedDown = new HashSet<Integer>();
    private final Set<Integer> mouseKeys = new HashSet<Integer>();
    public Set<Integer> queuedKeys() { return new HashSet<Integer>(queuedDown); }
    public void mouseModifiers(Set<Integer> keys) { mouseKeys.clear(); mouseKeys.addAll(keys); if (!keys.isEmpty()) install(); refresh(); }

    public static boolean physicalDown(int code) {
        ByteBuffer physical = SDL_GetKeyboardState();
        int scan = KeyCodes.lwjglToSdlScancode(code);
        return physical != null && scan > 0 && scan < physical.limit() && physical.get(scan) != 0;
    }
    private void install() {
        if (original != null) return;
        original = Keyboard.eventQueue;
        // Keyboard.next removes the previous head, then getters peek at the new current event.
        Keyboard.eventQueue = new AbstractQueue<Keyboard.KeyEvent>() {
            public Iterator<Keyboard.KeyEvent> iterator() { return original.iterator(); }
            public int size() { return original.size(); }
            public boolean offer(Keyboard.KeyEvent e) { return original.offer(e); }
            public Keyboard.KeyEvent peek() { Keyboard.KeyEvent e=original.peek(); apply(e); return e; }
            public Keyboard.KeyEvent poll() { Keyboard.KeyEvent e=original.poll(); apply(original.peek()); return e; }
        };
    }
    private void apply(Keyboard.KeyEvent e) {
        if (e != null && pending.remove(e)) {
            if (e.state.isPressed) down.add(e.key); else down.remove(e.key);
        }
        refresh();
    }
    public void refresh() {
        if (original == null) return;
        ByteBuffer physical = SDL_GetKeyboardState();
        if (physical == null) return;
        if (down.isEmpty() && mouseKeys.isEmpty()) { Keyboard.sdlKeyPressedArray = physical; return; }
        if (merged == null || merged.capacity() != physical.limit()) merged = ByteBuffer.allocateDirect(physical.limit());
        for (int i=0;i<physical.limit();i++) merged.put(i,physical.get(i));
        Set<Integer> visible = new HashSet<Integer>(down); visible.addAll(mouseKeys);
        for (int code : visible) {
            int scan=KeyCodes.lwjglToSdlScancode(code);
            if (scan > 0 && scan < merged.limit()) merged.put(scan,(byte)1);
        }
        Keyboard.sdlKeyPressedArray = merged;
    }
    public void event(int code, boolean pressed) {
        install(); touched.add(code);
        if (pressed) queuedDown.add(code); else queuedDown.remove(code);
        String name = Keyboard.getKeyName(code);
        boolean shift=queuedDown.contains(42)||queuedDown.contains(54)||physicalDown(42)||physicalDown(54);
        boolean control=queuedDown.contains(29)||queuedDown.contains(157)||queuedDown.contains(56)||queuedDown.contains(184)
            ||physicalDown(29)||physicalDown(157)||physicalDown(56)||physicalDown(184);
        int character = name != null && name.length() == 1 ? (shift ? Character.toUpperCase(name.charAt(0)) : Character.toLowerCase(name.charAt(0))) : code == 57 ? ' ' : 0;
        Keyboard.KeyEvent e = new Keyboard.KeyEvent(code, code, pressed && !control ? character : 0,
            pressed ? Keyboard.KeyState.PRESS : Keyboard.KeyState.RELEASE, System.nanoTime());
        pending.add(e); Keyboard.addRawKeyEvent(e);
    }
    public void syncBindings() {
        for (int code : touched) if (down.contains(code) || physicalDown(code))
            net.minecraft.client.settings.KeyBinding.setKeyBindState(code, true);
    }
    public Set<Integer> cancel() {
        // Keep the current head: next() expects to discard it, not the next real input event.
        if (original != null) {
            Keyboard.KeyEvent current=original.peek();
            original.removeIf(e -> e != current && pending.contains(e));
        }
        pending.clear(); down.clear(); queuedDown.clear(); mouseKeys.clear(); refresh();
        Set<Integer> result = new HashSet<Integer>(touched); touched.clear(); return result;
    }
}
