package io.github.wdy.easytouch.input;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import net.minecraft.util.AxisAlignedBB;

/** Plans a supported step from actual collision surfaces without moving the player. */
final class StepGeometry {
    static float height(AxisAlignedBB body, List<?> collisions, double dx, double dz, float base, float maximum) {
        AxisAlignedBB path = body.addCoord(dx, 0, dz);
        SortedSet<Float> candidates = new TreeSet<Float>();
        for (Object value : collisions) {
            AxisAlignedBB obstacle = (AxisAlignedBB) value;
            if (!obstacle.intersectsWith(path)) continue;
            double rise = obstacle.maxY - body.minY;
            if (rise <= base || rise > maximum) continue;
            // Rounding down could leave the feet inside the destination surface.
            float height = (float) rise;
            if (height < rise) height = Math.nextUp(height);
            if (height <= maximum) candidates.add(height);
        }
        AxisAlignedBB flat = horizontal(body.copy(), collisions, dx, dz);
        double flatDistance = distanceSquared(body, flat);
        for (float height : candidates) {
            if (!clear(body.addCoord(0, height, 0), collisions)) continue;
            AxisAlignedBB raised = body.getOffsetBoundingBox(0, height, 0);
            AxisAlignedBB landing = horizontal(raised, collisions, dx, dz);
            if (distanceSquared(body, landing) <= flatDistance + 1.0E-10) continue;
            double down = -height;
            for (Object value : collisions)
                down = ((AxisAlignedBB) value).calculateYOffset(landing, down);
            landing.offset(0, down, 0);
            // Require a higher supporting surface, not a speculative step into open air.
            if (down > -height && landing.minY > body.minY && clear(landing, collisions)) return height;
        }
        return base;
    }

    private static AxisAlignedBB horizontal(AxisAlignedBB box, List<?> collisions, double dx, double dz) {
        for (Object value : collisions) dx = ((AxisAlignedBB) value).calculateXOffset(box, dx);
        box.offset(dx, 0, 0);
        for (Object value : collisions) dz = ((AxisAlignedBB) value).calculateZOffset(box, dz);
        return box.offset(0, 0, dz);
    }

    private static double distanceSquared(AxisAlignedBB start, AxisAlignedBB end) {
        double x = end.minX - start.minX, z = end.minZ - start.minZ;
        return x * x + z * z;
    }

    private static boolean clear(AxisAlignedBB box, List<?> collisions) {
        for (Object value : collisions) if (((AxisAlignedBB) value).intersectsWith(box)) return false;
        return true;
    }
}
