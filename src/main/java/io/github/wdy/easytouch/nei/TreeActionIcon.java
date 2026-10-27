package io.github.wdy.easytouch.nei;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Original 14px pixel icons, drawn at native GUI scale inside NEI's 16px toolbar buttons. */
public enum TreeActionIcon {
    CRAFT(
        "..............",
        ".KKKKKKKKKKKK.",
        ".KWWDWWDWWWWK.",
        ".KTTDTTDTTTTK.",
        ".KDDDDDDDDDDK.",
        ".KTTDTTDTTTTK.",
        ".KTTDTTDTTTTK.",
        ".KSSSSSSSSSSK.",
        ".KBBBBBBBBBBK.",
        ".KBBKBBBBKBBK.",
        ".KBBKBBBBKBBK.",
        ".KBBKBBBBKBBK.",
        ".KKKKKKKKKKKK.",
        ".............."),
    TRY_CRAFT(
        "..............",
        "..............",
        "...KK.........",
        "...KCK........",
        "...KCCK.......",
        "...KCCCK......",
        "...KCCCCK.....",
        "...KCCCCK.....",
        "...KCCCK......",
        "...KCCK.......",
        "...KCK........",
        "...KK.........",
        "..............",
        "..............");

    public static final int SIZE = 14;
    private final int[] normal = new int[SIZE * SIZE];
    private final int[] hovered = new int[SIZE * SIZE];
    private final int[] disabled = new int[SIZE * SIZE];

    TreeActionIcon(String... rows) {
        if (rows.length != SIZE) throw new IllegalArgumentException("Icon height must be 14");
        for (int y = 0; y < SIZE; y++) {
            if (rows[y].length() != SIZE) throw new IllegalArgumentException("Icon width must be 14");
            for (int x = 0; x < SIZE; x++) {
                int color = color(rows[y].charAt(x));
                int i = y * SIZE + x;
                normal[i] = color;
                if (color < 0) {
                    hovered[i] = disabled[i] = -1;
                } else {
                    int r = color >> 16, g = color >> 8 & 255, b = color & 255;
                    hovered[i] = Math.min(255, r + 20) << 16 | Math.min(255, g + 20) << 8 | Math.min(255, b + 20);
                    int gray = (r * 30 + g * 59 + b * 11) / 200 + 20;
                    disabled[i] = gray << 16 | gray << 8 | gray;
                }
            }
        }
    }

    private static int color(char pixel) {
        switch (pixel) {
            case '.': return -1;
            case 'K': return 0x22272E;
            case 'W': return 0xFFE19B;
            case 'T': return 0xDDA258;
            case 'D': return 0x704227;
            case 'B': return 0x96572D;
            case 'S': return 0xC07A3E;
            case 'R': return 0xF46F67;
            case 'r': return 0xB43C42;
            case 'X': return 0xF1F8FF;
            case 'H': return 0xCAF8FF;
            case 'C': return 0x52BDD8;
            default: throw new IllegalArgumentException("Unknown icon pixel: " + pixel);
        }
    }

    public void draw(int left, int top, boolean enabled, boolean mouseOver) {
        int[] pixels = !enabled ? disabled : mouseOver ? hovered : normal;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE;) {
                    int color = pixels[y * SIZE + x];
                    int start = x++;
                    while (x < SIZE && pixels[y * SIZE + x] == color) x++;
                    if (color < 0) continue;
                    tessellator.setColorRGBA_I(color, 255);
                    tessellator.addVertex(left + start, top + y + 1, 0);
                    tessellator.addVertex(left + x, top + y + 1, 0);
                    tessellator.addVertex(left + x, top + y, 0);
                    tessellator.addVertex(left + start, top + y, 0);
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
