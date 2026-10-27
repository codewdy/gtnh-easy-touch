package io.github.wdy.easytouch.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** Standard arrow silhouette, with its tip anchored to the actual GUI mouse hotspot. */
public final class PointerCursor {
    private static final float SIZE_SCALE = .8f;
    private static final float[][] POINTS = {
        {0,0}, {0,16}, {4,12}, {7,18}, {10,16.5f}, {7,10.5f}, {13,10.5f}
    };
    // Explicit triangles keep the concave arrow notch correct on all drivers.
    private static final int[] TRIANGLES = {0,1,2, 0,2,5, 0,5,6, 2,3,4, 2,4,5};
    private PointerCursor() { }

    private static void fill(float x,float y) {
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int index : TRIANGLES) GL11.glVertex2f(x+POINTS[index][0]*SIZE_SCALE,y+POINTS[index][1]*SIZE_SCALE);
        GL11.glEnd();
    }
    public static void draw(Minecraft mc,int x,int y) {
        int scale=new ScaledResolution(mc,mc.displayWidth,mc.displayHeight).getScaleFactor();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_HINT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0,0,0,.35f);
            fill(x+SIZE_SCALE,y+SIZE_SCALE);
            GL11.glColor4f(1,1,1,1);
            fill(x,y);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT,GL11.GL_NICEST);
            GL11.glLineWidth(Math.max(1f,scale*SIZE_SCALE));
            GL11.glColor4f(.08f,.08f,.08f,1);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (float[] point : POINTS) GL11.glVertex2f(x+point[0]*SIZE_SCALE,y+point[1]*SIZE_SCALE);
            GL11.glEnd();
        } finally { GL11.glPopAttrib(); }
    }
}
