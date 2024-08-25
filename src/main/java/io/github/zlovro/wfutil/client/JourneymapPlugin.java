package io.github.zlovro.wfutil.client;

import io.github.zlovro.wfutil.Team;
import io.github.zlovro.wfutil.WFMod;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.MarkerOverlay;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.model.MapImage;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;

import static journeymap.client.api.event.ClientEvent.Type.*;

@ClientPlugin
public class JourneymapPlugin implements IClientPlugin
{
    public static ArrayList<Team> teamsToDraw = new ArrayList<>();
    public static IClientAPI      jmAPI       = null;

    @Override
    public void initialize(IClientAPI api)
    {

        jmAPI = api;
        jmAPI.subscribe(getModId(), EnumSet.of(DEATH_WAYPOINT, MAPPING_STARTED, MAPPING_STOPPED, DISPLAY_UPDATE));
    }

    @Override
    public String getModId()
    {
        return WFMod.MODID;
    }

    @Override
    public void onEvent(ClientEvent event)
    {
        switch (event.type)
        {
            case MAPPING_STARTED:
            {
                jmAPI.removeAll(WFMod.MODID);
                drawTeamAreas();

                break;
            }
            case MAPPING_STOPPED:
            {
                break;
            }
        }
    }

    public static void drawTeamAreas()
    {
        if (teamsToDraw == null)
        {
            return;
        }

        for (Team team : teamsToDraw)
        {
            Color color = WFMod.getColorByName(team.nameColor);

            int rgb = 0x10000 * color.getRed() + 0x100 * color.getGreen() + color.getBlue();

            MapImage img = new MapImage(new BufferedImage(1, 1, 10));

            img.centerAnchors();
            img.setRotation(0);

            // bottom left, bottom right, top left, top right
            for (BlockPos flag : team.flags)
            {
                int radius = WFMod.configServer.flagRadius;

                int minX = flag.getX() - radius;
                int maxX = flag.getX() + radius;
                int minZ = flag.getZ() - radius;
                int maxZ = flag.getZ() + radius;

                BlockPos bottomLeft = new BlockPos(minX, 0, maxZ);
                BlockPos bottomRight = new BlockPos(maxX, 0, maxZ);
                BlockPos topRight = new BlockPos(maxX, 0, minZ);
                BlockPos topLeft = new BlockPos(minX, 0, minZ);

                MarkerOverlay marker = new MarkerOverlay(WFMod.MODID, String.format("MARKER %s: %d, %d", team.name, flag.getX(), flag.getZ()), flag, img);
                marker.setLabel(team.name);

                MapPolygon shape = new MapPolygon(bottomLeft, bottomRight, topRight, topLeft);
                ShapeProperties shapeProperties = new ShapeProperties().setStrokeWidth(4).setStrokeColor(rgb).setStrokeOpacity(0.5F).setFillColor(rgb).setStrokeOpacity(0.4F);
                PolygonOverlay polygon = new PolygonOverlay(WFMod.MODID, String.format("AREA %s: %d, %d", team.name, flag.getX(), flag.getZ()), World.OVERWORLD, shapeProperties, shape);
                try
                {
                    jmAPI.show(polygon);
                    jmAPI.show(marker);
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
