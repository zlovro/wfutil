package io.github.zlovro.wfutil.entities;

import com.mojang.blaze3d.matrix.MatrixStack;
import io.github.zlovro.wfutil.WFMod;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.util.ResourceLocation;

public class SoldierEntityRenderer<T extends SoldierEntity, M extends SoldierEntityRenderer.SoldierModel<T>> extends BipedRenderer<T, M> {
    public static class SoldierModel<T1 extends SoldierEntity> extends PlayerModel<T1> {
        public SoldierModel(float modelSize, boolean smallArmsIn) {
            super(modelSize, smallArmsIn);
        }

        @Override
        public void setRotationAngles(T1 entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setRotationAngles(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            if (entityIn.getCurrentState() == SoldierEntity.SoldierState.SURRENDERING) {
                bipedLeftArm.rotateAngleZ  = bipedLeftArmwear.rotateAngleZ = (float) Math.toRadians(-155);
                bipedRightArm.rotateAngleZ = bipedRightArmwear.rotateAngleZ = (float) Math.toRadians(155);
            }
        }
    }

    public static SoldierEntityRenderer factory(EntityRendererManager renderManager) {
        return new SoldierEntityRenderer<>(renderManager, new SoldierModel<>(0.0F, false), 0.5F);
    }

    public SoldierEntityRenderer(EntityRendererManager renderManagerIn, M modelBipedIn, float shadowSize) {
        super(renderManagerIn, modelBipedIn, shadowSize);
    }

    // public SoldierEntityRenderer(EntityRendererManager p_i232471_1_, M entity, float shadowSize, float p_i232471_4_, float p_i232471_5_, float p_i232471_6_) {
    //     super(p_i232471_1_, entity, shadowSize, p_i232471_4_, p_i232471_5_, p_i232471_6_);
    // }

    @Override
    public ResourceLocation getEntityTexture(T entity) {
        return new ResourceLocation(WFMod.MODID, String.format("textures/entity/soldier/soldier_%d.png", entity.type));
    }

    @Override
    protected boolean canRenderName(T entity) {
        return false;
    }

    @Override
    public void render(T entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }
}
