package io.github.zlovro.wfutil.tileentities.mortar;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.util.WFList;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;

public class MortarRenderer extends TileEntityRenderer<MortarTileEntity> {

    public static final RenderMaterial TEXTURE  = new RenderMaterial(AtlasTexture.LOCATION_BLOCKS_TEXTURE, new ResourceLocation(WFMod.MODID, "block/mortar"));
    private final       ModelRenderer  renderer = new ModelRenderer(64, 64, 0, 0);

    public MortarRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
        initModel();
    }

    public ModelRenderer osovina, drzacCijevi, cijev0, cijev1, cijev2, cijev3, element6, element7, rotatable;
    public WFList<ModelRenderer> rotatableChildren;

    public void initModel() {
        osovina     = new ModelRenderer(64, 64, 0, 0).addBox(1, 1, 7, 14, 2, 2);
        drzacCijevi = new ModelRenderer(64, 64, 0, 0).addBox(6, 0, 6, 4, 4, 4);
        cijev0      = new ModelRenderer(64, 64, 0, 0).addBox(7, 4, 9, 2, 12, 1);
        cijev1      = new ModelRenderer(64, 64, 0, 0).addBox(7, 4, 6, 2, 12, 1);
        cijev2      = new ModelRenderer(64, 64, 0, 0).addBox(6, 4, 7, 1, 12, 2);
        cijev3      = new ModelRenderer(64, 64, 0, 0).addBox(9, 4, 7, 1, 12, 2);
        element6    = new ModelRenderer(64, 64, 0, 0).addBox(14, 0, 6, 2, 4, 4);
        element7    = new ModelRenderer(64, 64, 0, 0).addBox(0, 0, 6, 2, 4, 4);

        rotatableChildren = new WFList<>(osovina, drzacCijevi, cijev0, cijev1, cijev2, cijev3);
        rotatable         = new ModelRenderer(64, 64, 0, 0);
        rotatableChildren.forEach(r -> rotatable.addChild(r));

        renderer.addChild(element6);
        renderer.addChild(element7);
    }

    @Override
    public void render(MortarTileEntity te, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn) {
        float yaw = 180 - te.yaw;

        matrixStackIn.push();

        matrixStackIn.translate(0.5, 1.0 / 16, 0.5);
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(yaw));
        matrixStackIn.rotate(Vector3f.XP.rotationDegrees(te.pitch));
        matrixStackIn.translate(-0.5, -(1.0 / 16), -0.5);

        rotatable.render(matrixStackIn, TEXTURE.getBuffer(bufferIn, RenderType::getEntityCutout), combinedLightIn, combinedOverlayIn);

        matrixStackIn.pop();


        matrixStackIn.push();

        matrixStackIn.translate(0.5, 0, 0.5);
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(yaw));
        matrixStackIn.translate(-0.5, 0, -0.5);

        renderer.render(matrixStackIn, TEXTURE.getBuffer(bufferIn, RenderType::getEntityCutout), combinedLightIn, combinedOverlayIn);

        matrixStackIn.pop();
    }
}
