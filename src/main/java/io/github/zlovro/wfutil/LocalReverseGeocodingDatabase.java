package io.github.zlovro.wfutil;

import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class LocalReverseGeocodingDatabase
{
    public static Raster map;
    public static HashMap<Integer, String> placeNames = new HashMap<>();

    public static void loadData()
    {
        try
        {
            map = ImageIO.read(WFMod.readFromResources("/colormap.png")).getRaster();
            String[] lines = IOUtils.toString(WFMod.readFromResources("/indexmap.txt"), StandardCharsets.UTF_8).split("\n");
            for (String line : lines)
            {
                String[] parts = line.split(":");
                placeNames.put(Integer.parseInt(parts[0]), parts[1]);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static String reverseGeocode(int x, int y)
    {
        if (x < 0 || x >= 8573 || y < 0 || y >= 8579)
        {
            return "Location out of bounds";
        }
        int rgb = map.getSample(x, y, 0);
        return placeNames.get(rgb);
    }


    // returns epsg 4326
    public static Point2D.Double mc2rl(int x, int z)
    {
        float topLeftX = 1907868.225997999F;
        float topLeftY = 5696698.844037617F;

        float size = 14675.9094307541F;

        float xProj = (x / 8573.0F) * size + topLeftX;
        float yProj = topLeftY - ((z / 8579.0F) * size);

        return EpsgTransformer.to4326(new Point2D.Double(xProj, yProj));
    }

    public static Point rl2mc(float lat, float lon)
    {
        Point2D.Double proj = EpsgTransformer.to3857(new Point2D.Double(lon, lat));

        float topLeftX = 1907868.225997999F;
        float topLeftY = 5696698.844037617F;

        float size = 14675.9094307541F;

        float x = (float) (((proj.x - topLeftX) / size) * 8573.0F);
        float z = (float) (((topLeftY - proj.y) / size) * 8579.0F);

        return new Point((int) x, (int) z);
    }
}
