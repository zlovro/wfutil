package io.github.zlovro.wfutil.mixins;

import com.tac.guns.Config;
import com.tac.guns.common.BoundingBoxManager;
import com.tac.guns.common.Gun;
import com.tac.guns.entity.MissileEntity;
import com.tac.guns.entity.ProjectileEntity;
import com.tac.guns.event.GunProjectileHitEvent;
import com.tac.guns.init.ModEffects;
import com.tac.guns.interfaces.IHeadshotBox;
import com.tac.guns.util.math.ExtendedEntityRayTraceResult;
import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.WFSound;
import io.github.zlovro.wfutil.entities.SoldierEntity;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin extends Entity implements IEntityAdditionalSpawnData {
    @Shadow
    public int life;

    @Shadow
    protected LivingEntity shooter;

    @Shadow
    private static BlockRayTraceResult rayTraceBlocks(World world, RayTraceContext context) {
        return null;
    }

    @Shadow protected int pierce;

    @Shadow protected Gun modifiedGun;

    @Shadow protected abstract void onHitBlock(BlockState state, BlockPos pos, Direction face, Vector3d hitVec);

    @Shadow protected int iLevel;

    @Shadow protected int shooterId;

    @Shadow protected Gun.AmmoPlugEffect ammoPlugEffect;

    @Shadow protected abstract void onHitEntity(Entity entity, Vector3d hitVec, Vector3d startVec, Vector3d endVec, boolean headshot);

    public ProjectileEntityMixin(EntityType<?> entityTypeIn, World worldIn) {
        super(entityTypeIn, worldIn);
    }

    @Unique
    private ProjectileEntity wfutil$getThis() {
        return (ProjectileEntity) (Object) this;
    }

    @Unique
    private boolean wfutil$isMissile() {
        return wfutil$getThis() instanceof MissileEntity;
    }

    @Inject(method = "registerData", at = @At("HEAD"))
    private void registerData(CallbackInfo ci) {
        if (wfutil$isMissile()) {
            addTag("forceLoad");
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("TAIL"))
    private void onInit(EntityType<?> entityType, World worldIn, CallbackInfo ci) {
        if (wfutil$isMissile()) {
            Config.SERVER.gameplay.enableExplosionBreak.set(true);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        if (world instanceof ServerWorld) {
            this.life = Integer.MAX_VALUE;
        }
    }

    @Inject(method = "onRemovedFromWorld", at = @At("HEAD"), remap = false)
    public void onRemovedFromWorld(CallbackInfo ci) {
        if (world instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) world;

            if (wfutil$isMissile()) {
                WFSound.playSound(serverWorld, SoundEvents.ENTITY_GENERIC_EXPLODE, getPositionVec(), WFMod.configServer.explosionSoundRange, 0.3F + WFMod.RANDOM.nextFloat() * 0.3F);
            }

            SoldierEntity.alertNearby(serverWorld, getPositionVec());
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"), remap = false, cancellable = true)
    private void onHit(RayTraceResult result, Vector3d startVec, Vector3d endVec, CallbackInfo ci) {
        if (this.pierce <= 0) {
            this.remove();
        } else if (this.modifiedGun != null) {
            if (!MinecraftForge.EVENT_BUS.post(new GunProjectileHitEvent(result, wfutil$getThis()))) {
                if (result instanceof BlockRayTraceResult) {
                    BlockRayTraceResult blockRayTraceResult = (BlockRayTraceResult)result;
                    if (blockRayTraceResult.getType() != RayTraceResult.Type.MISS) {
                        Vector3d   hitVec = result.getHitVec();
                        BlockPos   pos    = blockRayTraceResult.getPos();
                        BlockState state  = this.world.getBlockState(pos);
                        Block      block  = state.getBlock();
                        if (!state.getMaterial().isReplaceable()) {
                            this.remove();
                        }

                        if (!((ResourceLocation) Objects.requireNonNull(block.getRegistryName())).getPath().contains("_button")) {
                            if ((Boolean)Config.SERVER.gameplay.enableGunGriefing.get() && (block instanceof BreakableBlock || block instanceof PaneBlock) && state.getMaterial() == Material.GLASS) {
                                this.world.destroyBlock(blockRayTraceResult.getPos(), false, this.shooter);
                            }

                            this.onHitBlock(state, pos, blockRayTraceResult.getFace(), hitVec);
                            if (block instanceof BellBlock) {
                                BellBlock bell = (BellBlock)block;
                                bell.attemptRing(this.world, state, blockRayTraceResult, null, true);
                            }

                            if (this.iLevel > -1 && (Boolean)Config.SERVER.gameplay.fireStarterCauseFire.get()) {
                                BlockPos offsetPos = pos.offset(blockRayTraceResult.getFace());
                                if (AbstractFireBlock.canLightBlock(this.world, offsetPos, blockRayTraceResult.getFace())) {
                                    BlockState fireState = AbstractFireBlock.getFireForPlacement(this.world, offsetPos);
                                    this.world.setBlockState(offsetPos, fireState, 11);
                                    ((ServerWorld)this.world).spawnParticle(ParticleTypes.LAVA, hitVec.x - 1.0 + this.rand.nextDouble() * 2.0, hitVec.y, hitVec.z - 1.0 + this.rand.nextDouble() * 2.0, 4, 0.0, 0.0, 0.0, 0.0);
                                }
                            }

                        }
                    }
                } else {
                    if (result instanceof ExtendedEntityRayTraceResult) {
                        ExtendedEntityRayTraceResult entityRayTraceResult = (ExtendedEntityRayTraceResult)result;
                        Entity entity = entityRayTraceResult.getEntity();
                        if (entity.getEntityId() == this.shooterId && !(Boolean)Config.SERVER.development.bulletSelfHarm.get()) {
                            ci.cancel();
                        }

                        if (entity instanceof LivingEntity && this.iLevel > -1) {
                            int fireDuration = this.ammoPlugEffect.getIgniteTick()[this.iLevel];
                            fireDuration = ProtectionEnchantment.getFireTimeForEntity((LivingEntity)entity, fireDuration);
                            ((LivingEntity)entity).addPotionEffect(new EffectInstance((Effect) ModEffects.IGNITE.get(), fireDuration, this.ammoPlugEffect.getIgniteDamage()[this.iLevel]));
                        }

                        if (!entity.isAlive()) {
                            entity.hurtResistantTime = 0;
                        } else if (entity.isAlive()) {
                            this.onHitEntity(entity, result.getHitVec(), startVec, endVec, entityRayTraceResult.isHeadshot());
                            entity.hurtResistantTime = 0;
                        }
                    }

                }
            }
        }

        ci.cancel();
    }

    /**
     * @author
     * @reason
     */
    @Nullable
    @Overwrite(remap = false)
    private ProjectileEntity.EntityResult getHitResult(Entity entity, Vector3d startVec, Vector3d endVec) {
        double        expandHeight = entity instanceof PlayerEntity && !entity.isCrouching() ? 0.0625 : 0.0;
        AxisAlignedBB boundingBox  = entity.getBoundingBox();
        if (Config.SERVER.gameplay.improvedHitboxes.get() && entity instanceof ServerPlayerEntity && this.shooter != null) {
            // int ping = (int)Math.floor((double)((ServerPlayerEntity)this.shooter).ping / 1000.0 * 20.0 + 0.5);
            int ping = 0;
            boundingBox = BoundingBoxManager.getBoundingBox((PlayerEntity) entity, ping);
        }

        boundingBox = boundingBox.expand(0.0, expandHeight, 0.0);
        Vector3d velocity = entity.getRidingEntity() != null ? entity.getRidingEntity().getMotion() : entity.getMotion();
        boundingBox = boundingBox.offset(velocity.mul(-1.0, -1.0, -1.0));
        boundingBox = boundingBox.expand(velocity.mul(-1.0, -1.0, -1.0));
        Vector3d hitPos      = (Vector3d) boundingBox.rayTrace(startVec, endVec).orElse(null);
        Vector3d grownHitPos = (Vector3d) boundingBox.grow((Double) Config.SERVER.gameplay.growBoundingBoxAmountV2.get(), 0.0, (Double) Config.SERVER.gameplay.growBoundingBoxAmountV2.get()).rayTrace(startVec, endVec).orElse(null);
        if (hitPos == null && grownHitPos != null) {
            RayTraceResult raytraceresult =  rayTraceBlocks(this.world, new RayTraceContext(startVec, grownHitPos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, this));
            if (raytraceresult.getType() == RayTraceResult.Type.BLOCK) {
                return null;
            }

            hitPos = grownHitPos;
        }

        boolean headshot = false;
        if (Config.SERVER.gameplay.enableHeadShots.get() && entity instanceof LivingEntity) {
            IHeadshotBox<LivingEntity> headshotBox = (IHeadshotBox<LivingEntity>) BoundingBoxManager.getHeadshotBoxes(entity.getType());
            if (headshotBox != null) {
                AxisAlignedBB box = headshotBox.getHeadshotBox((LivingEntity) entity);
                if (box != null) {
                    box = box.offset(boundingBox.getCenter().x, boundingBox.minY, boundingBox.getCenter().z);
                    Optional<Vector3d> headshotHitPos = box.rayTrace(startVec, endVec);
                    if (!headshotHitPos.isPresent()) {
                        box            = box.grow(Config.SERVER.gameplay.growBoundingBoxAmountV2.get(), 0.0, Config.SERVER.gameplay.growBoundingBoxAmountV2.get());
                        headshotHitPos = box.rayTrace(startVec, endVec);
                    }

                    if (headshotHitPos.isPresent() && (hitPos == null || headshotHitPos.get().distanceTo(hitPos) < 0.5)) {
                        hitPos   = headshotHitPos.get();
                        headshot = true;
                    }
                }
            }
        }

        return hitPos == null ? null : new ProjectileEntity.EntityResult(entity, hitPos, headshot);
    }
}
