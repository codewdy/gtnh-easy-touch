package io.github.wdy.easytouch.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Editor action whose entire text panel is clickable. */
public final class TextControlButton extends GuiButton {
    public TextControlButton(int id,int x,int y,int width,String text) { super(id,x,y,width,20,text); }
    @Override public void drawButton(Minecraft mc,int x,int y) {
        if (!visible) return;
        boolean hovered=mousePressed(mc,x,y);
        int border=!enabled ? 0xFF48515B : hovered ? 0xFFB0D9EA : 0xFF71879A;
        drawRect(xPosition,yPosition,xPosition+width,yPosition+height,border);
        drawRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,
            !enabled ? 0xFF252B32 : hovered ? 0xFF3B5265 : 0xFF263542);
        Circle.text(mc,displayString,xPosition+width/2f,yPosition+7,width-8,true,
            !enabled ? 0x777777 : hovered ? 0xFFFFFF : 0xECF4FA);
    }
}
