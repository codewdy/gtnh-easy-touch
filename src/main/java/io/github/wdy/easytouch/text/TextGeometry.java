package io.github.wdy.easytouch.text;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** GUI coordinates, before GUI scale, client-to-screen translation and Windows DPI conversion. */
final class TextGeometry {
    final double left, top, right, bottom, caretX, caretY, caretHeight;

    TextGeometry(double left, double top, double right, double bottom,
            double caretX, double caretY, double caretHeight) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.caretX = caretX;
        this.caretY = caretY;
        this.caretHeight = caretHeight;
    }

    boolean visible() {
        return Double.isFinite(left) && Double.isFinite(top) && Double.isFinite(right)
            && Double.isFinite(bottom) && right > left && bottom > top
            && Double.isFinite(caretX) && Double.isFinite(caretY) && Double.isFinite(caretHeight) && caretHeight > 0;
    }

    TextGeometry transform(AffineTransform matrix) {
        Rectangle2D bounds = matrix.createTransformedShape(new Rectangle2D.Double(left, top, right - left, bottom - top)).getBounds2D();
        Point2D start = matrix.transform(new Point2D.Double(caretX, caretY), null);
        Point2D end = matrix.transform(new Point2D.Double(caretX, caretY + caretHeight), null);
        return new TextGeometry(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY(),
            start.getX(), Math.min(start.getY(), end.getY()), Math.max(1, Math.abs(end.getY() - start.getY())));
    }
}
