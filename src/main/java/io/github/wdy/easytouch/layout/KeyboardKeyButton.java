package io.github.wdy.easytouch.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Round keycaps with the same circular hit region as their rendering. */
public final class KeyboardKeyButton extends GuiButton {
    private final boolean selected;
    public KeyboardKeyButton(int code,int x,int y,int size,String label,boolean selected) {
        super(1000+code,x,y,size,size,label); this.selected=selected;
    }
    @Override public boolean mousePressed(Minecraft mc,int x,int y) {
        return enabled && visible && Circle.hit(x,y,xPosition,yPosition,width);
    }
    @Override public void drawButton(Minecraft mc,int x,int y) {
        if (!visible) return;
        Circle.button(xPosition,yPosition,width,selected ? 0x66408060 : 0x40374755,selected,false);
        Circle.text(mc,displayString,xPosition+width/2f,yPosition+height/2f-3,width-3,true,0xECF4FA);

    }
}
