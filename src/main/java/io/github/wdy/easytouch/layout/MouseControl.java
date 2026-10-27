package io.github.wdy.easytouch.layout;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import io.github.wdy.easytouch.input.MinecraftInput;
import io.github.wdy.easytouch.input.TouchpadGestures;
import io.github.wdy.easytouch.touch.GuiTouchMouse;
import io.github.wdy.easytouch.touch.TouchSample;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.lwjglx.input.Mouse;

/** Full-screen touchpad, with only a pointer overlay and no on-screen controller. */
@Lwjgl3Aware
public final class MouseControl {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final GuiTouchMouse pointer = new GuiTouchMouse(mc);
    private final MinecraftInput world = new MinecraftInput(mc);
    private final TouchpadGestures gestures;
    private boolean open;
    private float x, y;

    public MouseControl(BiConsumer<Integer, Boolean> button, Consumer<Runnable> motion) {
        gestures=new TouchpadGestures(new TouchpadGestures.Port() {
            @Override public void move(float dx,float dy) {
                // Preserve press -> movement -> release order in the mouse event queue.
                motion.accept(() -> {
                    if (mc.currentScreen == null) world.turn(dx,dy);
                    else {
                        syncPointer();
                        x=TouchLayout.clamp(x+dx); y=TouchLayout.clamp(y+dy);
                        pointer.move(x,y);
                    }
                });
            }
            @Override public void click(int index) {
                button.accept(index-100,true);
                button.accept(index-100,false);
            }
            @Override public void button(int index,boolean down) { button.accept(index-100,down); }
        });
    }
    public boolean isOpen() { return open; }
    public void open() { reset(); open=true; syncPointer(); }
    public void releaseInputs() { gestures.cancel(); }
    public void reset() { releaseInputs(); open=false; }
    private void syncPointer() {
        x=TouchLayout.clamp((float)Mouse.getX()/Math.max(1,mc.displayWidth));
        y=TouchLayout.clamp(1f-(float)Mouse.getY()/Math.max(1,mc.displayHeight));
    }
    public boolean accept(TouchSample s,int w,int h) {
        if (!open) return false;
        if (s.kind==TouchSample.Kind.DOWN && !gestures.hasContacts()) syncPointer();
        gestures.accept(s,w,h);
        return true;
    }
    public void draw(int w,int h) {
        if (mc.currentScreen == null) return;
        // Match GuiScreen.handleMouseInput exactly: the arrow tip is the click position.
        int px=Mouse.getX()*w/Math.max(1,mc.displayWidth);
        int py=h-Mouse.getY()*h/Math.max(1,mc.displayHeight)-1;
        PointerCursor.draw(mc,px,py);
    }
}
