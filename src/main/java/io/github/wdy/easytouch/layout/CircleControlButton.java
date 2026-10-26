package io.github.wdy.easytouch.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Circular editor action with its descriptive label beside the control. */
public final class CircleControlButton extends GuiButton {
    public CircleControlButton(int id,int x,int y,int width,String text) { super(id,x,y,width,20,text); }
    @Override public boolean mousePressed(Minecraft mc,int x,int y) {
        return enabled && visible && Circle.hit(x,y,xPosition,yPosition,height);
    }
    @Override public void drawButton(Minecraft mc,int x,int y) {
        if (!visible) return;
        Circle.button(xPosition,yPosition,height,enabled ? 0x40374755 : 0x22374755,false,false);
        Circle.text(mc,"•",xPosition+10,yPosition+7,16,true,enabled ? 0xECF4FA : 0x777777);
        Circle.text(mc,displayString,xPosition+24,yPosition+7,width-24,false,enabled ? 0xECF4FA : 0x777777);
    }
}
