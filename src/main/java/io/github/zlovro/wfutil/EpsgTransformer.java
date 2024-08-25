package io.github.zlovro.wfutil;

import java.awt.geom.Point2D;

public class EpsgTransformer {
    private static final float EARTH_RADIUS = 6378137.0F;

    public static Point2D.Double to4326(Point2D.Double wgs84) {
        float lat = (float) Math.toDegrees(Math.atan(Math.exp(wgs84.getY() / EARTH_RADIUS)) * 2 - Math.PI / 2);
        float lng = (float) Math.toDegrees(wgs84.getX() / EARTH_RADIUS);
        return new Point2D.Double(lng, lat);
    }

    public static Point2D.Double to3857(Point2D.Double latLng) {
        float x = (float) (Math.toRadians(latLng.x) * EARTH_RADIUS);
        float y = (float) (Math.log(Math.tan(Math.PI / 4 + Math.toRadians(latLng.y) / 2)) * EARTH_RADIUS);
        return new Point2D.Double(x, y);
    }
}
