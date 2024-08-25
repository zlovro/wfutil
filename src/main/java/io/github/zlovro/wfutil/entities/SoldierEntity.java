package io.github.zlovro.wfutil.entities;

import com.mrcrayfish.obfuscate.common.data.SyncedPlayerData;
import com.tac.guns.Config;
import com.tac.guns.GunMod;
import com.tac.guns.common.Gun;
import com.tac.guns.common.ProjectileManager;
import com.tac.guns.common.WeaponType;
import com.tac.guns.entity.ProjectileEntity;
import com.tac.guns.init.ModSyncedDataKeys;
import com.tac.guns.interfaces.IProjectileFactory;
import com.tac.guns.item.GunItem;
import com.tac.guns.network.PacketHandler;
import com.tac.guns.network.message.MessageBulletTrail;
import com.tac.guns.network.message.MessageGunSound;
import com.tac.guns.util.GunModifierHelper;
import info.journeymap.shaded.org.jetbrains.annotations.NotNull;
import io.github.zlovro.wfutil.Team;
import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.WFSound;
import io.github.zlovro.wfutil.registry.WFEntities;
import io.github.zlovro.wfutil.util.WFList;
import io.github.zlovro.wfutil.util.WFMath;
import io.github.zlovro.wfutil.util.WFNBT;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.data.ItemTagsProvider;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.GroundPathNavigator;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeTagHandler;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SoldierEntity extends CreatureEntity {
    public static final int SOLDIER_TYPES = SoldierType.values().length;

    private static final DataParameter<String> CURRENT_STATE = EntityDataManager.createKey(SoldierEntity.class, DataSerializers.STRING);
    private static final DataParameter<String> LAST_STATE    = EntityDataManager.createKey(SoldierEntity.class, DataSerializers.STRING);

    public UUID     ownerTeamId = WFMod.UUID_NULL;
    public BlockPos basePos     = BlockPos.ZERO;
    public Integer  type        = -1;

    public UUID     attackTargetUuid = WFMod.UUID_NULL;
    public BlockPos targetPos        = BlockPos.ZERO;

    public int bulletsFired          = 0;
    public int reloadTimer           = 0;
    public int healTimer             = 0;
    public int shotTimer             = 0;
    public int lastSawTargetTimer    = 0;
    public int lookingForTargetTimer = 0;

    public boolean sawTargetLastTick = false;

    public BlockPos lastPathfindPos   = BlockPos.ZERO;
    public BlockPos lookingForBasePos = BlockPos.ZERO;

    private long lastPathindTimestamp;

    private boolean firstTick = true;
    private Random  rng;

    public enum SoldierState {
        IDLING, SURRENDERING, ATTACKING, LOOKING_FOR_TARGET, SHOOTING, RELOADING
    }

    public enum SoldierType {
        PISTOL, SMG, RIFLE, SHOTGUN, HEAVY
    }

    public SoldierEntity(EntityType<? extends SoldierEntity> entityType, World world) {
        super(entityType, world);

        init();
    }

    public static SoldierEntity factory(EntityType<? extends SoldierEntity> entityType, World world) {
        return new SoldierEntity(entityType, world);
    }

    public SoldierEntity(World world, BlockPos basePos, BlockPos pos, int type) {
        super(WFEntities.SOLDIER_ENTITY_TYPE.get(), world);

        this.basePos = basePos;
        this.type    = type;

        setPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    public SoldierState getCurrentState() {
        return SoldierState.valueOf(dataManager.get(CURRENT_STATE));
    }

    public void setCurrentState(SoldierState state) {
        dataManager.set(CURRENT_STATE, state.toString());
    }

    public SoldierState getLastState() {
        return SoldierState.valueOf(dataManager.get(LAST_STATE));
    }

    public void setLastState(SoldierState state) {
        dataManager.set(LAST_STATE, state.toString());
    }

    @Nullable
    public LivingEntity getTarget() {
        if (!(world instanceof ServerWorld)) {
            return null;
        }

        if (attackTargetUuid.equals(WFMod.UUID_NULL)) {
            return null;
        }

        return (LivingEntity) ((ServerWorld) world).getEntityByUuid(attackTargetUuid);
    }

    public void setAttackTarget(@Nullable LivingEntity entity) {
        if (entity == null) {
            attackTargetUuid = WFMod.UUID_NULL;
            return;
        }

        attackTargetUuid = entity.getUniqueID();
    }

    private GroundPathNavigator getGroundNavigator() {
        return (GroundPathNavigator) getNavigator();
    }

    @Override
    protected void registerData() {
        super.registerData();

        dataManager.register(CURRENT_STATE, SoldierState.IDLING.toString());
        dataManager.register(LAST_STATE, SoldierState.IDLING.toString());
    }

    private void setState(SoldierState state) {
        switch (state) {
            case IDLING: {
                lastSawTargetTimer = 0;
                targetPos          = null;
                setAttackTarget(null);
                break;
            }
            case LOOKING_FOR_TARGET: {
                lastSawTargetTimer = 0;
                break;
            }
            case SHOOTING: {
                shotTimer = 0;
                break;
            }
        }

        setLastState(getCurrentState());
        setCurrentState(state);
    }

    private void init() {
        getGroundNavigator().setCanSwim(true);
        getGroundNavigator().setBreakDoors(true);

        type = WFMod.RANDOM.nextInt(SOLDIER_TYPES);

        setState(SoldierState.IDLING);

        setItemStackToSlot(EquipmentSlotType.MAINHAND, getRandomGun().getDefaultInstance());
    }

    public static AttributeModifierMap getAttributes() {
        return CreatureEntity.registerAttributes().createMutableAttribute(Attributes.FOLLOW_RANGE, WFMod.configServer.soldierFollowRange).createMutableAttribute(Attributes.MAX_HEALTH, WFMod.configServer.soldierMaxHealth).createMutableAttribute(Attributes.MOVEMENT_SPEED, WFMod.configServer.soldierMoveSpeed).createMutableAttribute(Attributes.ATTACK_DAMAGE, 1).createMutableAttribute(Attributes.ATTACK_KNOCKBACK, 1).create();
    }

    private boolean isItemValuable(ItemStack stack) {
        Item item = stack.getItem();
        return item.isIn(Tags.Items.GEMS) || item.isIn(Tags.Items.INGOTS);
    }

    @Override
    public boolean canPickUpItem(ItemStack itemstackIn) {
        return super.canPickUpItem(itemstackIn) && (isItemValuable(itemstackIn) || isItemGun(itemstackIn));
    }

    @Override
    public void onItemPickup(Entity entityIn, int quantity) {
        super.onItemPickup(entityIn, quantity);

        if (entityIn instanceof ItemEntity) {
            ItemEntity itemEntity = (ItemEntity) entityIn;

            UUID    throwerId   = itemEntity.getThrowerId();
            Team    throwerTeam = Team.getTeamById(throwerId);
            boolean ownedByTeam = isOwnedByTeam(throwerTeam);

            ItemStack item = itemEntity.getItem();

            if (isItemGun(item) && ownedByTeam) {
                dropGun();
                setGun(item);
            } else if (isItemValuable(item) && getCurrentState() == SoldierState.SURRENDERING && !ownedByTeam) {
                setCustomName(new StringTextComponent(TextFormatting.GREEN + "Owned by " + TextFormatting.BOLD + throwerTeam.name));
            }
        }
    }

    private void setGun(ItemStack stack) {
        setItemStackToSlot(EquipmentSlotType.MAINHAND, stack);
    }

    private void dropGun() {
        entityDropItem(getItemStackFromSlot(EquipmentSlotType.MAINHAND));
        setGun(ItemStack.EMPTY);
    }

    private static boolean isItemGun(ItemStack item) {
        return item.getItem() instanceof GunItem;
    }

    @Nullable
    private Team getOwnerTeam() {
        return Team.getTeamById(ownerTeamId);
    }

    public GunItem getRandomGun() {
        WFList<GunItem> guns = new WFList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof GunItem) {
                GunItem gun = (GunItem) item;
                if (gun.getGroup() == GunMod.HEAVY_MATERIAL || gun.getGroup() == GunMod.SNIPER) {
                    continue;
                }

                guns.add(gun);
            }
        }

        return guns.getRandom();
    }

    @Nullable
    public Gun getGun() {
        Item item = getHeldItemMainhand().getItem();
        if (item instanceof GunItem) {
            return ((GunItem) item).getGun();
        }

        return null;
    }

    public boolean isLookingAt(MobEntity source, Entity target, float yawRange, float pitchRange) {
        if (target == null) {
            return false;
        }

        boolean canSee = source.getEntitySenses().canSee(target);
        if (!canSee) {
            return false;
        }

        Vector2f angle = WFMath.angleBetweenTwoVectors(source.getEyePosition(1), target.getEyePosition(1));

        float yaw   = WFMath.clampYaw(source.rotationYawHead);
        float pitch = WFMath.clampPitch(source.rotationPitch);

        double yawDelta   = Math.abs(yaw - angle.y);
        double pitchDelta = Math.abs(pitch - angle.x);
        return yawDelta < yawRange && pitchDelta < pitchRange;
    }

    public boolean isLookingAt(Entity entity) {
        return isLookingAt(this, entity, WFMod.configServer.soldierVisionYawRange, WFMod.configServer.soldierVisionPitchRange);
    }

    public boolean isLookedAtByPlayer(PlayerEntity source) {
        if (source == null) {
            return false;
        }

        if (!source.world.equals(world)) {
            return false;
        }

        Vector3d             origin = source.getEyePosition(1);
        Vector3d             dst    = origin.add(source.getLookVec().mul(WFMod.configServer.soldierMaxVisibleTargetRadius, WFMod.configServer.soldierMaxVisibleTargetRadius, WFMod.configServer.soldierMaxVisibleTargetRadius));
        EntityRayTraceResult result = ProjectileHelper.rayTraceEntities(world, null, origin, dst, new AxisAlignedBB(origin, dst), e -> true);
        if (result == null) {
            return false;
        }

        Entity entity = result.getEntity();
        return entity.equals(this);
    }

    private void onFirstTick() {
        rng = new Random(System.nanoTime() + getEntityId());

        if (basePos.equals(BlockPos.ZERO)) {
            basePos = new BlockPos(getPosition());
        }
    }

    public static <T extends Entity> List<T> getEntitiesWithinRadius(World world, Vector3d origin, Class<T> clazz, float radius, Predicate<T> predicate) {
        Vector3d sizeVec = new Vector3d(radius, radius, radius);
        return world.getEntitiesWithinAABB(clazz, new AxisAlignedBB(origin.subtract(sizeVec), origin.add(sizeVec)), e -> {
            boolean spectator = !e.isSpectator();
            return spectator && Math.sqrt(e.getDistanceSq(origin)) <= radius && predicate.test(e);
        }).stream().sorted(Comparator.comparingDouble(e -> e.getDistanceSq(origin))).collect(Collectors.toList());
    }

    private boolean pathfind(BlockPos dst, double speed) {
        long time = System.currentTimeMillis();

        boolean passedEnoughTime = time - lastPathindTimestamp >= WFMod.configServer.minPathfindCooldownMs;
        lastPathindTimestamp = time;

        if (!passedEnoughTime) {
            return false;
        }

        if (lastPathfindPos == null || (!dst.equals(lastPathfindPos) && dst.distanceSq(lastPathfindPos) >= 3)) {
            long t0 = System.nanoTime();
            rotateTowardsTarget(dst);
            getGroundNavigator().setPath(getGroundNavigator().getPathToPos(dst, 1), speed);
            return true;
        }
        lastPathfindPos = dst;
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (!(world instanceof ServerWorld)) {
            return;
        }

        if (firstTick) {
            onFirstTick();
            firstTick = false;
        }

        if (getHealth() <= WFMod.configServer.soldierMinHealth) {
            if (getHealth() < getMaxHealth() && healTimer % 20 == 0) {
                heal(1);
            }

            if (getHealth() >= getMaxHealth() * WFMod.configServer.soldierHealRatio) {
                setState(getLastState());
            }

            healTimer++;
        }

        if (isInWater() && ((int) getPosY() - getPosY()) < 0.4F) {
            jumpController.setJumping();
        }

        Path path = getGroundNavigator().getPath();
        if (path != null && !path.isFinished()) {
            checkForClosedDoors(path.func_242950_i());
            checkForClosedDoors(path.getCurrentPoint());
        }

        checkForSurrender();

        if (getCurrentState() == SoldierState.ATTACKING) {
            if (getGun() == null) {
                setState(SoldierState.IDLING);
            } else if (getTarget() == null) {
                lookForTarget();
            } else if (getTarget().isSpectator()) {
                setState(SoldierState.IDLING);
            }
        }

        if (getCurrentState() != SoldierState.SURRENDERING) {
            boolean canSeeTarget = getTarget() != null && getEntitySenses().canSee(getTarget());
            if (!canSeeTarget) {
                if (getCurrentState() != SoldierState.IDLING) {
                    if (lastSawTargetTimer >= WFMod.configServer.soldierCooldownTicks && getGroundNavigator().noPath()) {
                        setState(SoldierState.IDLING);
                    }

                    lastSawTargetTimer++;

                    if (sawTargetLastTick) {
                        lookForTarget();
                    }
                }
            } else {
                lastSawTargetTimer = 0;
                if (!sawTargetLastTick) {
                    onSpottedTarget();
                }

                if (getCurrentState() == SoldierState.ATTACKING) {
                    targetPos = getTarget().getPosition();
                }
            }

            sawTargetLastTick = canSeeTarget;

            if (getCurrentState() == SoldierState.LOOKING_FOR_TARGET) {
                if (getGroundNavigator().noPath()) {
                    if (lookingForTargetTimer % 5 == 0) {
                        int addPitch = (int) (WFMod.RANDOM.nextInt((int) (WFMod.configServer.soldierVisionPitchRange * 2)) - WFMod.configServer.soldierVisionPitchRange);
                        int addYaw   = (int) (WFMod.RANDOM.nextInt((int) (WFMod.configServer.soldierVisionYawRange * 2)) - WFMod.configServer.soldierVisionYawRange);

                        rotationPitch = WFMath.clampPitch(addPitch);
                        rotationYaw   = rotationYawHead = WFMath.clampYaw(addYaw);
                    }

                    lookingForTargetTimer++;
                }
            }

            if (getCurrentState() == SoldierState.LOOKING_FOR_TARGET && (getLastState() != SoldierState.LOOKING_FOR_TARGET || lookingForBasePos == null)) {
                lookingForBasePos = targetPos == null || targetPos.equals(BlockPos.ZERO) ? getPosition() : targetPos;
            }
        }

        switch (getCurrentState()) {
            case LOOKING_FOR_TARGET:
            case IDLING: {
                if (getCurrentState() == SoldierState.IDLING) {
                    int minWanderIntervalTicks = 100;
                    if (getGroundNavigator().noPath() && rng.nextInt(minWanderIntervalTicks) == 0) {
                        pathfind(basePos.add(3 + rng.nextInt(20) - 10, 0, 3 + rng.nextInt(20) - 10), WFMod.configServer.soldierWanderSpeed);
                    }
                } else {
                    int minWanderIntervalTicks = 40;
                    if (getGroundNavigator().noPath() && rng.nextInt(minWanderIntervalTicks) == 0) {
                        pathfind(lookingForBasePos.add(10 + rng.nextInt(50) - 25, 0, 10 + rng.nextInt(50) - 25), WFMod.configServer.soldierLookForTargetSpeed);
                    }
                }

                checkAttackingCapabilities();

                break;
            }

            case RELOADING: {
                if (reloadTimer <= 0) {
                    setState(getLastState());
                }
                reloadTimer--;
                break;
            }

            case ATTACKING: {
                rotateTowardsTarget(targetPos);

                if (targetPos != null) {
                    pathfind(targetPos, WFMod.configServer.soldierAttackSpeed);
                }

                if (isLookingAt(getTarget())) {
                    int burstRate           = getGun().getGeneral().getBurstRate();
                    int bulletDurationTicks = Math.max(1, (int) (20.0 / burstRate)) * 2;

                    if (shotTimer % bulletDurationTicks == 0) {
                        shoot();
                    }
                    shotTimer++;
                }

                break;
            }

            case SURRENDERING: {
                Vector3d targetVec = null;
                if (getTarget() != null) {
                    targetVec = getTarget().getPositionVec();
                }

                if (targetVec == null) {
                    setState(SoldierState.IDLING);
                    break;
                }
                //
                // if (getTarget() instanceof PlayerEntity) {
                //     PlayerEntity player = (PlayerEntity) getTarget();
                //
                //     if (SyncedPlayerData.instance().get(player, ModSyncedDataKeys.AIMING) && isLookedAtByPlayer(player)) {
                //         for (int i = 0; i < EquipmentSlotType.values().length; i++) {
                //             EquipmentSlotType slot = EquipmentSlotType.values()[i];
                //             entityDropItem(getItemStackFromSlot(slot), getEyeHeight());
                //             setItemStackToSlot(slot, ItemStack.EMPTY);
                //         }
                //     }
                // }

                getGroundNavigator().setPath(null, 0);
                rotateTowardsTarget(targetVec);

                break;
            }

            case SHOOTING: {


                break;
            }
        }
    }

    private void checkAttackingCapabilities() {
        getEntitiesWithinRadius(world, getPositionVec(), PlayerEntity.class, WFMod.configServer.soldierMaxVisibleTargetRadius, p -> true).forEach(player -> {
            if (canAttackPlayer(player)) {
                return;
            }

            List<SoldierEntity> nearbySoldiers = getNearbySoldiersForAttacking(null);
            nearbySoldiers.forEach(s -> s.startAttacking(player));
        });
    }

    private void checkForSurrender() {
        getEntitiesWithinRadius(world, getPositionVec(), PlayerEntity.class, WFMod.configServer.soldierMaxVisibleTargetRadius, p -> true).forEach(this::checkForSurrender);
    }


    private void checkForSurrender(PlayerEntity player) {
        checkForSurrender(player, getNearbySoldiersForSurrendering());
    }

    private static void checkForSurrender(PlayerEntity player, List<SoldierEntity> nearbySoldiers) {
        if (nearbySoldiers.size() < WFMod.configServer.soldierMinHelpers && isItemGun(player.getHeldItemMainhand())) {
            nearbySoldiers.forEach(s -> {
                if (s.getCurrentState() != SoldierState.SURRENDERING) {
                    s.surrenderTo(player);
                }
            });
        }
    }

    private boolean isOwnedByPlayer(UUID player) {
        if (player == null) {
            return false;
        }

        Team team = Team.getPlayerTeam(player);
        return isOwnedByTeam(team);
    }

    private boolean isOwnedByTeam(@Nullable Team team) {
        return team != null && team.teamId.equals(ownerTeamId);
    }

    private boolean isOwnedByPlayer(@NotNull PlayerEntity player) {
        return isOwnedByPlayer(player.getUniqueID());
    }

    private boolean canAttackPlayer(PlayerEntity player) {
        if (!isLookingAt(player)) {
            return true;
        }

        return !isOwnedByPlayer(player);
    }

    private void rotateTowardsTarget(BlockPos pos) {
        rotateTowardsTarget(pos.getX(), pos.getY(), pos.getZ());
    }

    private void rotateTowardsTarget(double x, double y, double z) {
        rotateTowardsTarget(new Vector3d(x, y, z));
    }

    private void rotateTowardsTarget(Vector3d targetPos) {
        Vector2f rotation = WFMath.angleBetweenTwoVectors(getPositionVec(), targetPos);
        rotationYaw   = rotationYawHead = rotation.y;
        rotationPitch = rotation.x;
    }

    private void lookForTarget() {
        if (targetPos == null) {
            setState(SoldierState.IDLING);
            return;
        }

        if (pathfind(targetPos, WFMod.configServer.soldierLookForTargetSpeed)) {
            setState(SoldierState.LOOKING_FOR_TARGET);
        }
    }

    private void onSpottedTarget() {
        checkAttackingCapabilities();
    }

    public void alertThis() {
        SoldierState state = getCurrentState();
        if (state != SoldierState.SURRENDERING && state != SoldierState.ATTACKING && state != SoldierState.RELOADING) {
            lookForTarget();
        }
    }

    public void surrenderTo(LivingEntity entity) {
        setAttackTarget(entity);
        targetPos = entity.getPosition();
        setState(SoldierState.SURRENDERING);
    }

    public void startAttacking(LivingEntity target) {
        if (target == null) {
            return;
        }

        if (target.isSpectator()) {
            return;
        }

        setState(SoldierState.ATTACKING);
        setAttackTarget(target);
        targetPos = getTarget().getPosition();
    }

    public static List<SoldierEntity> getNearbySoldiersForAlerting(World world, Vector3d pos) {
        return getEntitiesWithinRadius(world, pos, SoldierEntity.class, WFMod.configServer.soldierAlertRadius, s -> true);
    }

    private List<SoldierEntity> getNearbySoldiers(float radius) {
        return getEntitiesWithinRadius(world, getPositionVec(), SoldierEntity.class, radius, s -> true);
    }

    private List<SoldierEntity> getNearbySoldiersForAlerting() {
        return getNearbySoldiers(WFMod.configServer.soldierAlertRadius).stream().limit(WFMod.configServer.soldierMaxHelpers).collect(Collectors.toList());
    }

    private List<SoldierEntity> getNearbySoldiersForAttacking(SoldierEntity exclude) {
        return getNearbySoldiersForAlerting().stream().filter(s -> !s.equals(exclude)).collect(Collectors.toList());
    }

    private List<SoldierEntity> getNearbySoldiersForSurrendering() {
        return getNearbySoldiers(WFMod.configServer.soldierSurrenderRadius);
    }

    private void checkForClosedDoors(PathPoint pathPoint) {
        if (pathPoint == null) {
            return;
        }

        BlockPos   blockPos = pathPoint.func_224759_a();
        BlockState state    = world.getBlockState(blockPos);

        if (state.isIn(BlockTags.WOODEN_DOORS)) {
            DoorBlock doorBlock = (DoorBlock) state.getBlock();
            if (!doorBlock.isOpen(state)) {
                doorBlock.openDoor(world, state, blockPos, true);
            }
        }
    }

    public void shoot() {
        ItemStack heldItem = getHeldItemMainhand();

        if (isItemGun(heldItem)) {
            GunItem item        = (GunItem) heldItem.getItem();
            Gun     modifiedGun = item.getModifiedGun(heldItem);

            if (modifiedGun != null) {
                int magSize = modifiedGun.getReloads().getMaxAmmo();
                if (++bulletsFired >= magSize) {
                    bulletsFired = 0;
                    reloadTimer  = modifiedGun.getReloads().getReloadMagTimer();
                    setState(SoldierState.RELOADING);
                    return;
                }

                int count;
                if (modifiedGun.getDisplay().getWeaponType() != WeaponType.SG || !modifiedGun.getProjectile().isHasBlastDamage() && GunModifierHelper.getHeWeight(heldItem) <= -1) {
                    count = modifiedGun.getGeneral().getProjectileAmount();
                } else {
                    count = 1;
                }

                Gun.Projectile     projectileProps    = modifiedGun.getProjectile();
                ProjectileEntity[] spawnedProjectiles = new ProjectileEntity[count];

                double posX = getPosX();
                double posY = getPosY() + (double) getEyeHeight();
                double posZ = getPosZ();

                Vector3d origin = new Vector3d(posX, posY, posZ);

                for (int i = 0; i < count; ++i) {
                    float tgtX = (float) getTarget().getPosX();
                    float tgtY = (float) getTarget().getPosY();
                    float tgtZ = (float) getTarget().getPosZ();

                    if (shotTimer <= WFMod.configServer.soldierMissTimeSeconds) {
                        tgtX += (2.0F + WFMod.RANDOM.nextFloat() * 2) * (WFMod.RANDOM.nextBoolean() ? -1 : 1);
                        tgtY += (2.0F + WFMod.RANDOM.nextFloat() * 2) * (WFMod.RANDOM.nextBoolean() ? -1 : 1);
                        tgtZ += (2.0F + WFMod.RANDOM.nextFloat() * 2) * (WFMod.RANDOM.nextBoolean() ? -1 : 1);
                    } else if (WFMod.RANDOM.nextInt(2) != 0) {
                        tgtX += (WFMod.RANDOM.nextFloat() / 2) * -0.25F;
                        tgtY += (WFMod.RANDOM.nextFloat() / 2) * -0.25F;
                        tgtZ += (WFMod.RANDOM.nextFloat() / 2) * -0.25F;
                    }

                    rotateTowardsTarget(tgtX, tgtY, tgtZ);

                    if (!isLookingAt(getTarget())) {
                        return;
                    }

                    IProjectileFactory factory          = ProjectileManager.getInstance().getFactory(projectileProps.getItem());
                    ProjectileEntity   projectileEntity = factory.create(world, this, heldItem, item, modifiedGun, 0, 0);

                    projectileEntity.setWeapon(heldItem);
                    projectileEntity.setAdditionalDamage(GunModifierHelper.getAdditionalDamage(heldItem));

                    world.addEntity(projectileEntity);

                    spawnedProjectiles[i] = projectileEntity;
                    projectileEntity.tick();
                }

                alertNearby();

                if (!projectileProps.isVisible()) {
                    MessageBulletTrail messageBulletTrail = new MessageBulletTrail(spawnedProjectiles, projectileProps, getEntityId(), projectileProps.getSize());
                    PacketHandler.getPlayChannel().send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(getPosX(), getPosY(), getPosZ(), (Double) Config.COMMON.network.projectileTrackingRange.get(), world.getDimensionKey())), messageBulletTrail);
                }

                boolean          silenced  = GunModifierHelper.isSilencedFire(heldItem);
                ResourceLocation fireSound = silenced ? modifiedGun.getSounds().getSilencedFire() : modifiedGun.getSounds().getFire();
                if (fireSound != null) {
                    float   pitch  = 0.9F + world.rand.nextFloat() * 0.125F;
                    boolean muzzle = modifiedGun.getDisplay().getFlash() != null;

                    for (ServerPlayerEntity player : ((ServerWorld) world).getPlayers()) {
                        WFSound.SoundInfo info = WFSound.getSoundInfo(origin, player.getPositionVec(), WFMod.configServer.gunSoundRange, pitch);
                        if (info == null) {
                            continue;
                        }

                        MessageGunSound messageSound = new MessageGunSound(fireSound, SoundCategory.PLAYERS, (float) info.position.x, (float) info.position.y, (float) info.position.z, info.volume, info.pitch, getEntityId(), muzzle, false);

                        PacketHandler.getPlayChannel().send(PacketDistributor.PLAYER.with(() -> player), messageSound);
                    }
                }
            }
        } else {
            world.playSound(null, getPosX(), getPosY(), getPosZ(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3F, 0.8F);
        }

    }

    public static void alertNearby(World world, Vector3d pos) {
        getNearbySoldiersForAlerting(world, pos).stream().sorted(Comparator.comparingDouble(o -> o.getDistanceSq(pos))).limit(WFMod.configServer.soldierMaxHelpers).forEach(SoldierEntity::alertThis);
    }

    public void alertNearby() {
        getNearbySoldiersForAlerting().forEach(SoldierEntity::alertThis);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity trueSrc = source.getTrueSource();
        if (trueSrc instanceof SoldierEntity) {
            return false;
        }

        if (getCurrentState() != SoldierState.SURRENDERING && trueSrc instanceof LivingEntity) {
            startAttacking((LivingEntity) trueSrc);
            alertNearby();
        }

        return super.attackEntityFrom(source, amount);
    }

    @Override
    public boolean canDespawn(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);

        compound.putInt("type", type);

        compound.put("basePos", WFNBT.writeBlockPos(basePos));
        compound.put("attackPos", WFNBT.writeBlockPos(targetPos));

        compound.putString("currentState", getCurrentState().toString());
        compound.putString("lastState", getLastState().toString());

        compound.putUniqueId("ownerTeamUuid", ownerTeamId);
        compound.putUniqueId("targetUuid", attackTargetUuid);
    }

    @Override
    public void read(CompoundNBT compound) {
        super.read(compound);

        type = compound.getInt("type");

        basePos   = WFNBT.readBlockPos(compound.get("basePos"));
        targetPos = WFNBT.readBlockPos(compound.get("attackPos"));

        setCurrentState(SoldierState.valueOf(compound.getString("currentState")));
        setLastState(SoldierState.valueOf(compound.getString("lastState")));

        ownerTeamId      = compound.getUniqueId("ownerTeamUuid");
        attackTargetUuid = compound.getUniqueId("targetUuid");
    }
}