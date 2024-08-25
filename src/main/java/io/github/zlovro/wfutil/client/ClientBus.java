package io.github.zlovro.wfutil.client;

import com.mojang.authlib.minecraft.SocialInteractionsService;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import io.github.zlovro.wfutil.Team;
import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.tileentities.landmine.LandmineTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static io.github.zlovro.wfutil.client.JourneymapPlugin.jmAPI;

@Mod.EventBusSubscriber(modid = WFMod.MODID, value = Dist.CLIENT)
public class ClientBus {
    public static World getClientWorld()
    {
        return Minecraft.getInstance().world;
    }

    public static final int MAP_REFRESH_TIME        = 5 * 20;
    public static       int timeSinceLastMapRefresh = 0;

    public static void enableMultiplayer() {
        ObfuscationReflectionHelper.setPrivateValue(Minecraft.class, Minecraft.getInstance(), new SocialInteractionsService() {
            @Override
            public boolean serversAllowed() {
                return true;
            }

            @Override
            public boolean realmsAllowed() {
                return true;
            }

            @Override
            public boolean chatAllowed() {
                return true;
            }

            @Override
            public boolean isBlockedPlayer(UUID playerID) {
                return false;
            }
        }, "field_244734_au");
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.TEXT) {
            if (++timeSinceLastMapRefresh > MAP_REFRESH_TIME) {
                timeSinceLastMapRefresh = 0;

                jmAPI.removeAll(WFMod.MODID);
                JourneymapPlugin.drawTeamAreas();
            }
            LocationRenderer.drawLocationText(event);
        }
    }

    @SubscribeEvent
    public synchronized void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();

        Team playerTeam = Team.getPlayerTeam(mc.player.getUniqueID());
        if (playerTeam == null) {
            return;
        }

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof LandmineTileEntity)) {
                continue;
            }

            LandmineTileEntity landmine = (LandmineTileEntity) te;
            if (!landmine.teamName.equals(playerTeam.name)) {
                continue;
            }

            ActiveRenderInfo       renderInfo = Minecraft.getInstance().gameRenderer.getActiveRenderInfo();
            IRenderTypeBuffer.Impl buffer     = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();
            IVertexBuilder         builder    = buffer.getBuffer(RenderType.LINES);

            MatrixStack matrixStack = event.getMatrixStack();
            matrixStack.push();

            Vector3d pv = renderInfo.getProjectedView();
            matrixStack.translate(-pv.getX(), -pv.getY(), -pv.getZ());

            BlockPos pos = te.getPos();
            float    x   = pos.getX();
            float    y   = pos.getY();
            float    z   = pos.getZ();

            AxisAlignedBB aabb = VoxelShapes.fullCube().withOffset(x, y, z).getBoundingBox();
            WorldRenderer.drawBoundingBox(matrixStack, builder, aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ, 1, 0, 0, 1);

            buffer.finish(RenderType.LINES);
            matrixStack.pop();
        }
    }
}
