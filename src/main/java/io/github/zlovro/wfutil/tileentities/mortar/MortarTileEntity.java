package io.github.zlovro.wfutil.tileentities.mortar;

import com.tac.guns.init.ModItems;
import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.WFNetwork;
import io.github.zlovro.wfutil.WFSound;
import io.github.zlovro.wfutil.network.MortarTileEntityUpdateMessage;
import io.github.zlovro.wfutil.registry.WFBlocks;
import io.github.zlovro.wfutil.registry.WFTileEntities;
import io.github.zlovro.wfutil.util.WFInventory;
import io.github.zlovro.wfutil.util.WFMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;

public class MortarTileEntity extends TileEntity implements ITickableTileEntity {
    public int   ticksAlive;
    public float pitch, yaw, velocity;
    public BlockPos targetPos  = null;
    public String   waypointId = null;

    private boolean needsUpdate       = true;
    private float   timeSinceLastShot = 0;

    private final ArrayList<Runnable> queue = new ArrayList<>();

    public MortarTileEntity() {
        this(WFTileEntities.MORTAR_TYPE.get());
    }

    public MortarTileEntity(TileEntityType<?> type) {
        super(type);
    }

    @Override
    public void setWorldAndPos(World world, BlockPos pos) {
        super.setWorldAndPos(world, pos);

        if (targetPos == null) {
            targetPos = pos;
        }
    }

    public Item getRoundItem()
    {
        return ModItems.RPG7_MISSILE.get();
    }

    private boolean isClientSide() {
        return !isServerSide();
    }

    private boolean isServerSide() {
        return world instanceof ServerWorld;
    }

    private boolean isPowered() {
        return world.isBlockPowered(pos);
    }

    @Override
    public void tick() {
        if (isClientSide()) {
            return;
        }

        while (!queue.isEmpty()) {
            queue.get(0).run();
            queue.remove(0);
        }

        timeSinceLastShot += 1.0F / 20;

        if (ticksAlive % 5 == 0) {
            canShoot();
        }

        if (isPowered()) {
            if (!needsUpdate) {
                needsUpdate = true;
                shoot();
            }
        } else {
            needsUpdate = false;
        }
    }

    public boolean canShoot() {
        if (WFMod.configCommon.debug) {
            return true;
        }

        if (timeSinceLastShot < WFMod.configServer.mortarReloadTime) {
            return false;
        }

        TileEntity containerBelow = world.getTileEntity(pos.down());
        if (!(containerBelow instanceof IInventory)) {
            return false;
        }

        IInventory inventory = (IInventory) containerBelow;
        return !isServerSide() || WFInventory.contains(inventory, getRoundItem());
    }

    public Vector3d getProjectilePos() {
        return new Vector3d(pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5);
    }

    public boolean targetInRange() {
        return getPos().distanceSq(targetPos) <= WFMod.configServer.mortarRange * WFMod.configServer.mortarRange;
    }

    public void predict() {
        new Thread(() -> {
            double[] ret = calculateYawPitchVelocity();

            queue.add(() -> {
                yaw      = (float) ret[0];
                pitch    = (float) ret[1];
                velocity = (float) ret[2];

                sendToAllClients();
            });
        }).start();
    }

    private static Vector3d blockPosToVector3d(BlockPos pos) {
        return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
    }

    public double[] calculateYawPitchVelocity() {
        long t0 = System.nanoTime();

        Vector3d startPos   = getProjectilePos();
        double   yawRadians = Math.atan2(targetPos.getZ() - startPos.getZ(), targetPos.getX() - startPos.getX()) - Math.PI / 2;

        BlockPos nearest         = BlockPos.fromLong(Long.MAX_VALUE);
        double   nearestVelocity = 0;
        int      nearestPitch    = 0;

        double lastDistance = Double.MAX_VALUE;

        parentLoop:
        for (double tmpVelocity = 2; tmpVelocity < 10; tmpVelocity += 0.3) {
            for (int pitchDegrees = -3; pitchDegrees > -90; pitchDegrees -= 3) {
                BlockPos hit = getNearestBlock(world, startPos, blockPosToVector3d(targetPos), Math.toRadians(pitchDegrees), yawRadians, tmpVelocity);
                if (hit == null) {
                    continue;
                }

                if (pitchDegrees == -45) {
                    double distance = Math.sqrt(WFMath.horizontalDistanceSq(hit, targetPos));
                    if (distance > lastDistance) {
                        break parentLoop;
                    }
                    lastDistance = distance;
                }

                if (WFMath.horizontalDistanceSq(hit, targetPos) < WFMath.horizontalDistanceSq(nearest, targetPos)) {
                    nearest         = hit;
                    nearestVelocity = tmpVelocity;
                    nearestPitch    = pitchDegrees;

                    if (nearest.equals(targetPos)) {
                        break parentLoop;
                    }
                }
            }
        }

        nearestVelocity /= 2;

        double yawDegrees = Math.toDegrees(yawRadians);

        // WFMod.LOGGER.log(Level.DEBUG, "{} ms, Nearest block {} ({}), distance from start to target: {}, pitch {}, yaw {}, velocity {}", (System.nanoTime() - t0) / 1_000_000.0, nearest, Math.sqrt(nearest.distanceSq(targetPos)), startPos.distanceTo(blockPosToVector3d(targetPos)), nearestPitch, yawDegrees, nearestVelocity);

        return new double[]{yawDegrees, nearestPitch, nearestVelocity};
    }

    private static Vector3d copyVector3d(Vector3d original) {
        return new Vector3d(original.getX(), original.getY(), original.getZ());
    }

    private static BlockPos getNearestBlock(World world, Vector3d startPos, Vector3d target, double pitchRadians, double yawRadians, double velocity) {
        double mt_x = -Math.sin(yawRadians) * Math.cos(pitchRadians);
        double mt_y = -Math.sin(pitchRadians);
        double mt_z = Math.cos(yawRadians) * Math.cos(pitchRadians);

        Vector3d motion = new Vector3d(mt_x, mt_y, mt_z).normalize().scale(velocity);
        // WFMod.LOGGER.log(Level.DEBUG, "{} -> {}, Motion: {}, velocity: {}, pitch: {}", startPos, target, motion, velocity, Math.toDegrees(pitchRadians));

        Vector3d pos     = copyVector3d(startPos);
        Vector3d nearest = new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);

        while (true) {
            Vector3d oldMotion = copyVector3d(motion);

            BlockPos blockPos      = new BlockPos(pos);
            BlockPos startBlockPos = new BlockPos(startPos);

            double eta = pos.distanceTo(target) / oldMotion.length();
            // WFMod.LOGGER.log(Level.DEBUG, "    ETA: {}, distance: {}, magnitude: {}, motion: {}", eta, pos.distanceTo(target), oldMotion.length(), oldMotion);

            if (eta > 60 * 20) {
                return null;
            }

            if (pos.y <= 0) {
                return null;
            }

            if (WFMath.horizontalDistanceSq(pos, target) < WFMath.horizontalDistanceSq(nearest, target)) {
                nearest = copyVector3d(pos);
            }

            if (!blockPos.equals(startBlockPos) && world.getBlockState(blockPos).getMaterial().blocksMovement()) {
                return new BlockPos(nearest);
            }

            pos    = pos.add(oldMotion);
            motion = oldMotion.scale(0.99).add(0, -0.05, 0);
        }
    }

    public void shoot() {
        if (!canShoot()) return;

        timeSinceLastShot = 0;

        if (!WFMod.configCommon.debug) {
            IInventory inventory = (IInventory) world.getTileEntity(pos.down());
            WFInventory.take(inventory, ModItems.RPG7_MISSILE.get(), 1);
        }

        // Vector3d anchor = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        // ((ServerWorld) world).spawnParticle(ParticleTypes.SMOKE, anchor.x, anchor.y, anchor.z, 60, 0.1, 0.1, 0.1, 0);

        WFMod.LOGGER.debug("Launching rocket {} -> {}", getProjectilePos(), targetPos);

        ArrowEntity missileEntity = new ArrowEntity(world, getProjectilePos().x, getProjectilePos().y, getProjectilePos().z) {
            @Override
            protected void registerData() {
                super.registerData();

                addTag("forceLoad");
            }

            @Override
            protected void onImpact(RayTraceResult result) {
                WFMod.LOGGER.log(Level.DEBUG, "Exploding {}, {} blocks away from target {}", result.getHitVec(), result.getHitVec().distanceTo(blockPosToVector3d(targetPos)), targetPos);
                world.createExplosion(null, getPosX(), getPosY(), getPosZ(), WFMod.configServer.mortarRoundExplosionRadius, Explosion.Mode.BREAK);

                WFSound.playSound((ServerWorld) world, SoundEvents.ENTITY_GENERIC_EXPLODE, result.getHitVec(), WFMod.configServer.explosionSoundRange, 0.4F + WFMod.RANDOM.nextFloat() * 0.2F);

                remove();
            }

            @Override
            public void tick() {
                super.tick();

                ((ServerWorld) world).spawnParticle(ParticleTypes.FLAME, getPosX(), getPosY(), getPosZ(), 4, 0.1, 0.1, 0.1, 0);
            }

            @Override
            public boolean isGlowing() {
                return true;
            }

            @Override
            public boolean isBurning() {
                return true;
            }
        };

        missileEntity.rotationYaw   = yaw;
        missileEntity.rotationPitch = pitch;

        missileEntity.setDirectionAndMovement(missileEntity, missileEntity.rotationPitch, missileEntity.rotationYaw, 0, velocity, 0);

        world.addEntity(missileEntity);
    }

    public void sendToAllClients() {
        WFNetwork.INSTANCE.send(PacketDistributor.ALL.noArg(), new MortarTileEntityUpdateMessage(this));
    }

    public void sendToServer() {
        WFNetwork.INSTANCE.sendToServer(new MortarTileEntityUpdateMessage(this));
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return getData();
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        readData(tag);
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(getPos(), 0, getData());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket packet) {
        read(WFBlocks.MORTAR.get().getDefaultState(), packet.getNbtCompound());
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        return getData();
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    public CompoundNBT getData() {
        CompoundNBT input = super.write(new CompoundNBT());

        try {
            input.putFloat("yaw", yaw);
            input.putFloat("pitch", pitch);
            input.put("target", NBTUtil.writeBlockPos(targetPos));
            input.putString("waypoint", waypointId == null ? "null" : waypointId);
        } catch (Exception e) {
            WFMod.LOGGER.warn(e.getMessage());
        }

        return input;
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        readData(nbt);
    }

    private void readData(CompoundNBT nbt) {
        try {
            yaw        = nbt.getFloat("yaw");
            pitch      = nbt.getFloat("pitch");
            targetPos  = NBTUtil.readBlockPos(nbt.getCompound("target"));
            waypointId = nbt.getString("waypoint");
        } catch (Exception e) {
            WFMod.LOGGER.warn(e.getMessage());
        }
    }
}
