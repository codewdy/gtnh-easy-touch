package io.github.wdy.easytouch.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** Subpixel geometry with a screen-pixel alpha fringe, independent of GUI scaling. */
public final class Circle {
    private Circle() { }
    public static boolean hit(int px,int py,int x,int y,int size) {
        double r=size/2.0, dx=px-x-r, dy=py-y-r;
        return dx*dx+dy*dy <= r*r;
    }
    private static int scale() {
        Minecraft mc=Minecraft.getMinecraft();
        return new ScaledResolution(mc,mc.displayWidth,mc.displayHeight).getScaleFactor();
    }
    static int segments(double radius,int scale) { return Math.max(64,Math.min(256,(int)Math.ceil(radius*scale*4))); }
    private static void begin() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }
    private static void color(int color,float coverage) {
        GL11.glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f*coverage);
    }
    private static void band(double cx,double cy,double inner,double outer,int steps,int color,float inside,float outside) {
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for(int i=0;i<=steps;i++) {
            double angle=i*Math.PI*2/steps, dx=Math.cos(angle),dy=Math.sin(angle);
            color(color,inside); GL11.glVertex2d(cx+dx*inner,cy+dy*inner);
            color(color,outside); GL11.glVertex2d(cx+dx*outer,cy+dy*outer);
        }
        GL11.glEnd();
    }
    public static void draw(int x,int y,int size,int color) {
        if(size<=0)return;
        int scale=scale(),steps=segments(size/2.0,scale);
        double r=size/2.0,cx=x+r,cy=y+r,fringe=Math.min(r,.85/scale);
        begin();
        try {
            color(color,1);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(cx,cy);
            for(int i=0;i<=steps;i++) {
                double a=i*Math.PI*2/steps;
                GL11.glVertex2d(cx+Math.cos(a)*(r-fringe),cy+Math.sin(a)*(r-fringe));
            }
            GL11.glEnd();
            band(cx,cy,r-fringe,r,steps,color,1,0);
        } finally { GL11.glPopAttrib(); }
    }
    public static void ring(int x,int y,int size,int color) {
        if(size<=0)return;
        int scale=scale(),steps=segments(size/2.0,scale);
        double r=size/2.0,inner=Math.max(0,r-1.2),fringe=Math.min((r-inner)/2,.85/scale);
        begin();
        try {
            band(x+r,y+r,inner,inner+fringe,steps,color,0,1);
            band(x+r,y+r,inner+fringe,r-fringe,steps,color,1,1);
            band(x+r,y+r,r-fringe,r,steps,color,1,0);
        } finally { GL11.glPopAttrib(); }
    }
    public static void button(int x,int y,int size,int fill,boolean active,boolean selected) {
        draw(x,y,size,fill);
        ring(x,y,size,selected ? 0xDDEACB7B : active ? 0xCC72DFB5 : 0x779AB5C8);
    }
    public static void text(Minecraft mc,String label,float x,float y,int width,boolean centered,int color) {
        float scale=.8f;
        String fitted=mc.fontRenderer.trimStringToWidth(label,(int)(width/scale));
        // Snap the final text origin to a real display pixel, rather than a scaled GUI pixel.
        int pixels=scale();
        float left=centered ? x-mc.fontRenderer.getStringWidth(fitted)*scale/2 : x;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(Math.round(left*pixels)/(float)pixels,Math.round(y*pixels)/(float)pixels,0);
            GL11.glScalef(scale,scale,1);
            mc.fontRenderer.drawStringWithShadow(fitted,0,0,color);
        } finally { GL11.glPopMatrix(); }
    }
}
