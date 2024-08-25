package io.github.zlovro.wfutil;

import com.mojang.authlib.GameProfile;
import io.github.zlovro.wfutil.blocks.LandmineBlock;
import io.github.zlovro.wfutil.events.EntityTickEvent;
import io.github.zlovro.wfutil.network.LocationChangeMessage;
import io.github.zlovro.wfutil.network.TeamAreaUpdateMessage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.extensions.IForgeWorldServer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.ServerLifecycleEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.codehaus.plexus.util.StringUtils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import static io.github.zlovro.wfutil.blocks.LandmineBlock.BURIED;

@Mod.EventBusSubscriber(modid = WFMod.MODID)
public class ServerBus {
    public static final int AUTOSAVE_INTERVAL     = 10 * 20;
    public static       int timeSinceLastAutosave = 0;

    public static final int LOCATION_UPDATE_COOLDOWN_TICKS = 5 * 20;
    public static       int timeSinceLastLocationUpdate    = 0;

    public static final int TEAM_AREA_UPDATE_COOLDOWN_TICKS = 5 * 20;
    public static       int timeSinceLastTeamAreaUpdate     = 0;

    public static HashMap<UUID, Team> playerLastAreas = new HashMap<>();

    public void onPlayerMoved(ServerPlayerEntity player) {
        Team team = Team.getPlayerTeam(player.getUniqueID());
        if (team != null) {
            Team areaOwner = Team.getOwnerOfAreaAroundBlock(player.getPosition());
            if (areaOwner != null && playerLastAreas.get(player.getUniqueID()) != areaOwner && !areaOwner.members.contains(player.getUniqueID())) {
                for (UUID member : areaOwner.members) {
                    ServerPlayerEntity memberPlayer = player.server.getPlayerList().getPlayerByUUID(member);
                    if (memberPlayer != null) {
                        memberPlayer.sendMessage(new StringTextComponent(String.format("%s%s%s%s entered your territory at %s (%s%s%d%s blocks away)", TextFormatting.RED, TextFormatting.BOLD, player.getName(), TextFormatting.RED, player.getPosition(), TextFormatting.RED, TextFormatting.BOLD, (int) Math.sqrt(player.getPosition().distanceSq(memberPlayer.getPosition())), TextFormatting.RED)), new UUID(0, 0));
                    }
                }
            }
            playerLastAreas.put(player.getUniqueID(), areaOwner);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.side == LogicalSide.CLIENT) {
            return;
        }

        ServerWorld              world       = (ServerWorld) event.world;
        MinecraftServer          server      = event.world.getServer();
        PlayerList               playerList  = server.getPlayerList();
        List<ServerPlayerEntity> players     = playerList.getPlayers();
        int                      playerCount = players.size();

        if (++timeSinceLastLocationUpdate > LOCATION_UPDATE_COOLDOWN_TICKS) {
            timeSinceLastLocationUpdate = 0;

            for (ServerPlayerEntity player : playerList.getPlayers()) {
                if (player.getPosX() != player.prevPosX || player.getPosY() != player.prevPosY || player.getPosZ() != player.prevPosZ) {
                    onPlayerMoved(player);
                }

                BlockPos pos = player.getPosition();

                int x = pos.getX();
                int z = pos.getZ();

                String location = LocalReverseGeocodingDatabase.reverseGeocode(x, z);

                WFNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new LocationChangeMessage(location));
            }
        }

        if (++timeSinceLastAutosave > AUTOSAVE_INTERVAL) {
            timeSinceLastAutosave = 0;

            String json = WFMod.GSON.toJson(Team.teams);
            try {
                Files.write(Paths.get("config/wfutil/teams.json"), json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (++timeSinceLastTeamAreaUpdate > TEAM_AREA_UPDATE_COOLDOWN_TICKS) {
            timeSinceLastTeamAreaUpdate = 0;

            WFNetwork.INSTANCE.send(PacketDistributor.ALL.noArg(), new TeamAreaUpdateMessage(Team.teams));
        }
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player     = (ServerPlayerEntity) event.getPlayer();
            Team               playerTeam = Team.getPlayerTeam(player.getUniqueID());
            Block              block      = event.getState().getBlock();

            ResourceLocation blockId = block.getRegistryName();

            boolean playerOwnsFlag = playerTeam != null && blockId.equals(new ResourceLocation(playerTeam.flagBlock));
            if (playerOwnsFlag) {
                if (!blockId.equals(new ResourceLocation(playerTeam.flagBlock))) {
                    return;
                }

                BlockPos pos = new BlockPos(event.getPos().getX(), 0, event.getPos().getZ());

                playerTeam.flags.remove(pos);
                player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Abandoned everything within " + TextFormatting.BOLD + WFMod.configServer.flagRadius + TextFormatting.GREEN + " blocks"), WFMod.UUID_NULL);
                return;
            }

            Team landOwnerTeam = Team.getFlagOwner(event.getPos());
            if (landOwnerTeam == null) {
                return;
            }

            landOwnerTeam.flags.remove(event.getPos());
            return;
        }
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player     = (ServerPlayerEntity) event.getEntity();
            Team               playerTeam = Team.getPlayerTeam(player.getUniqueID());
            if (playerTeam == null) {
                return;
            }

            ResourceLocation blockId = event.getPlacedBlock().getBlock().getRegistryName();
            if (!blockId.equals(new ResourceLocation(playerTeam.flagBlock))) {
                return;
            }

            Team areaOwner = Team.getOwnerOfAreaAroundBlock(event.getPos());
            if (areaOwner != null && !areaOwner.equals(playerTeam)) {
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Could not place flag because the area is occupied by " + TextFormatting.BOLD + areaOwner.name), WFMod.UUID_NULL);
                event.setCanceled(true);
                return;
            }

            BlockPos pos      = new BlockPos(event.getPos().getX(), 0, event.getPos().getZ());
            BlockPos nearest  = Team.getNearestFlag(pos);
            float    distance = (float) Math.sqrt(nearest.distanceSq(pos));

            float minDistance = 2 * WFMod.configServer.flagRadius * WFMod.configServer.minimumFlagPlaceDistanceRatio;
            if (distance < minDistance) {
                player.sendMessage(new StringTextComponent(TextFormatting.RED + "Could not place flag because the nearest flag is less than " + (int) minDistance + " blocks away"), WFMod.UUID_NULL);
                event.setCanceled(true);
                return;
            }

            playerTeam.flags.add(pos);
            player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Occupied everything within " + TextFormatting.BOLD + WFMod.configServer.flagRadius + TextFormatting.GREEN + " blocks"), WFMod.UUID_NULL);
        }
    }

    public static <E extends Entity> ArrayList<E> getEntitiesInChunk(Class<E> entityType, Chunk chunk, Predicate<? super E> filter) {
        ArrayList<E> entities = new ArrayList<>();
        chunk.getEntitiesOfTypeWithinAABB(entityType, new AxisAlignedBB(0, 0, 0, 16, 1024, 16), entities, filter);
        return entities;
    }

    public static void parseCommands(ServerPlayerEntity callerPlayer, String msg, Event event) {
        try {
            String   help = "" + TextFormatting.YELLOW;
            String[] arguments;

            if (msg.contains(" ")) {
                String[] parts = msg.split(" ");
                arguments = new String[parts.length - 1];
                System.arraycopy(parts, 1, arguments, 0, parts.length - 1);
            } else {
                arguments = new String[0];
            }

            if (msg.equals("reloadcfg")) {
                WFMod.loadAllConfigs();
                callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "successfully reloaded config files."), WFMod.UUID_NULL);
                event.setCanceled(true);
                return;
            }

            if (msg.startsWith("latlon2mc")) {
                help += "latlon2mc <lat> <lon>";

                if (arguments.length != 2 && arguments.length != 0) {
                    callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                float lat, lon;

                if (arguments.length == 2) {
                    try {
                        lat = Float.parseFloat(arguments[0]);
                        lon = Float.parseFloat(arguments[1]);
                    } catch (Exception e) {
                        callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                        event.setCanceled(true);
                        return;
                    }
                } else {
                    callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Point mcCoords = LocalReverseGeocodingDatabase.rl2mc(lat, lon);
                callerPlayer.sendMessage(new StringTextComponent(String.format("%s%d, %d", TextFormatting.AQUA, mcCoords.x, mcCoords.y)), WFMod.UUID_NULL);
                event.setCanceled(true);
            }

            if (msg.startsWith("mc2latlon")) {
                help += "mc2latlon [x] [z]";

                if (arguments.length != 2 && arguments.length != 0) {
                    callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                int x, z;

                if (arguments.length == 2) {
                    if (!StringUtils.isNumeric(arguments[0]) || !StringUtils.isNumeric(arguments[1])) {
                        callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                        event.setCanceled(true);
                        return;
                    }

                    x = Integer.parseInt(arguments[0]);
                    z = Integer.parseInt(arguments[1]);
                } else {
                    x = callerPlayer.getPosition().getX();
                    z = callerPlayer.getPosition().getZ();
                }

                Point2D.Double latLon = LocalReverseGeocodingDatabase.mc2rl(x, z);
                callerPlayer.sendMessage(new StringTextComponent(String.format("%slat: %f, lon: %f", TextFormatting.AQUA, latLon.y, latLon.x)), WFMod.UUID_NULL);
                event.setCanceled(true);
            }

            if (msg.startsWith("teamcreate")) {
                help += "teamcreate <team name> <flag> <color>";

                if (arguments.length != 3) {
                    callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Team playerTeam = Team.getPlayerTeam(callerPlayer.getUniqueID());
                if (playerTeam != null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "You're already in team " + TextFormatting.BOLD + playerTeam.name), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                String teamName = arguments[0];
                if (Team.exists(teamName)) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "A team already exists with the name " + TextFormatting.BOLD + teamName), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                String flag = arguments[1];
                if (!Team.FLAGS.containsKey(flag)) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "No flags found with the name " + TextFormatting.BOLD + flag + TextFormatting.RED + ", possible flags:"), WFMod.UUID_NULL);
                    Team.FLAGS.keySet().forEach(k -> callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "    " + k), WFMod.UUID_NULL));
                    event.setCanceled(true);
                    return;
                }

                if (Team.anyTeamHasFlag(flag)) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "A team already owns this flag"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                String color = arguments[2];
                if (WFMod.getColorByName(color) == null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "Invalid color " + TextFormatting.BOLD + color), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                if (Team.anyTeamHasColor(color)) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "A team already owns this color"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Team team = new Team(callerPlayer.getUniqueID(), teamName, color, Team.FLAGS.get(flag));
                team.register();

                callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Created team " + TextFormatting.BOLD + teamName), WFMod.UUID_NULL);
                event.setCanceled(true);
            }
            if (msg.startsWith("teamleave")) {
                Team playerTeam = Team.getPlayerTeam(callerPlayer.getUniqueID());
                if (playerTeam == null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "You're not in any team"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                playerTeam.removePlayerFromTeamRaw(callerPlayer);

                callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Left team " + TextFormatting.BOLD + playerTeam.name), WFMod.UUID_NULL);
                event.setCanceled(true);
            }
            if (msg.startsWith("teamban")) {
                help += TextFormatting.YELLOW + "teamban <player name>";
                if (arguments.length != 1) {
                    callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Team callerPlayerTeam = Team.getPlayerTeam(callerPlayer.getUniqueID());
                if (callerPlayerTeam == null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "You're not in any team"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                if (!callerPlayerTeam.owner.equals(callerPlayer.getUniqueID())) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "You're not the team owner"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                String targetPlayerName = arguments[0];
                if (targetPlayerName.equals(callerPlayer.getName())) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "You can't ban yourself"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                ServerPlayerEntity targetPlayer = callerPlayer.server.getPlayerList().getPlayerByUsername(targetPlayerName);
                if (targetPlayer == null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "Could not find any player named " + TextFormatting.BOLD + targetPlayerName), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Team targetPlayerTeam = Team.getPlayerTeam(targetPlayer.getUniqueID());
                if (targetPlayerTeam == null || !targetPlayerTeam.members.contains(targetPlayer.getUniqueID())) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "Player " + TextFormatting.BOLD + targetPlayerName + " is not in your team"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                callerPlayerTeam.removePlayerFromTeamRaw(targetPlayer);
                callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Kicked player " + TextFormatting.BOLD + targetPlayerName + TextFormatting.GREEN + " from your team"), WFMod.UUID_NULL);

                event.setCanceled(true);
            }
            if (msg.startsWith("teaminvite")) {
                help += TextFormatting.YELLOW + "teamivnite <player name>";
                if (arguments.length != 1) {
                    callerPlayer.sendMessage(new StringTextComponent(help), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Team callerPlayerTeam = Team.getPlayerTeam(callerPlayer.getUniqueID());
                if (callerPlayerTeam == null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "You're not in any team"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                String             targetPlayerName = arguments[0];
                ServerPlayerEntity playerToAdd      = callerPlayer.server.getPlayerList().getPlayerByUsername(targetPlayerName);

                UUID playerToAddUuid = null;
                if (playerToAdd == null) {
                    GameProfile profile = callerPlayer.server.getPlayerProfileCache().getGameProfileForUsername(targetPlayerName);
                    if (profile != null) {
                        playerToAddUuid = profile.getId();
                    }
                } else {
                    playerToAddUuid = playerToAdd.getUniqueID();
                }

                if (playerToAddUuid == null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "Could not find any player named " + TextFormatting.BOLD + targetPlayerName), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                Team targetPlayerTeam = Team.getPlayerTeam(playerToAddUuid);
                if (targetPlayerTeam != null) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "Player " + TextFormatting.BOLD + targetPlayerName + " is already in a team"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                callerPlayerTeam.members.add(playerToAddUuid);

                callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Added player " + TextFormatting.BOLD + targetPlayerName + TextFormatting.GREEN + " to your team"), WFMod.UUID_NULL);
                if (playerToAdd != null) {
                    playerToAdd.sendMessage(new StringTextComponent(TextFormatting.GREEN + "" + TextFormatting.BOLD + callerPlayer.getName() + "Added you to their team " + TextFormatting.BOLD + callerPlayerTeam.name), WFMod.UUID_NULL);
                }

                event.setCanceled(true);
            }
            if (msg.startsWith("teamlist")) {
                if (Team.teams.isEmpty()) {
                    callerPlayer.sendMessage(new StringTextComponent(TextFormatting.RED + "No teams found"), WFMod.UUID_NULL);
                    event.setCanceled(true);
                    return;
                }

                callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Teams:"), WFMod.UUID_NULL);

                WorldBorder worldBorder = callerPlayer.world.getWorldBorder();

                long worldArea      = Math.max(worldBorder.getSize() * worldBorder.getSize() * 4L, 1);
                long totalOwnedArea = Math.max(Team.getTotalAreaOwned(), 1);

                for (Team team : Team.teams) {
                    Item      item  = ForgeRegistries.ITEMS.getValue(new ResourceLocation(team.flagBlock));
                    ItemStack stack = new ItemStack(item);

                    String x = String.format("%s    %s%s %s(%s, %s):", TextFormatting.GREEN, TextFormatting.BOLD, team.name, TextFormatting.GREEN, team.nameColor, stack.getDisplayName());

                    callerPlayer.sendMessage(new StringTextComponent(x), WFMod.UUID_NULL);

                    long ownedArea = team.getAreaOwned();

                    float ownedPercentage = 100.0F * (ownedArea / (float) totalOwnedArea);
                    float totalPercentage = 100.0F * (ownedArea / (float) worldArea);

                    callerPlayer.sendMessage(new StringTextComponent(String.format("%s    Flags: %d (%.2f%% of all owned land, %.2f%% of world)", TextFormatting.GREEN, team.flags.size(), ownedPercentage, totalPercentage)), WFMod.UUID_NULL);
                    if (!team.members.isEmpty()) {
                        callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "        Members (" + team.members.size() + "):"), WFMod.UUID_NULL);
                        for (UUID memberUuid : team.members) {
                            ServerPlayerEntity member = callerPlayer.server.getPlayerList().getPlayerByUUID(memberUuid);

                            String memberName;
                            if (member == null) {
                                GameProfile profile = callerPlayer.server.getPlayerProfileCache().getProfileByUUID(memberUuid);
                                if (profile != null) {
                                    memberName = profile.getName();
                                } else {
                                    memberName = memberUuid.toString();
                                }
                            } else {
                                memberName = member.getName().getString();
                            }

                            if (memberUuid.equals(team.owner)) {
                                memberName = TextFormatting.RED + "" + TextFormatting.BOLD + memberName;
                            }

                            callerPlayer.sendMessage(new StringTextComponent(TextFormatting.GREEN + "            Member " + TextFormatting.BOLD + memberName), WFMod.UUID_NULL);
                        }
                    }
                }

                event.setCanceled(true);

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        parseCommands(event.getPlayer(), event.getMessage(), event);
    }

    @SubscribeEvent
    public void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof ShovelItem)) return;
        if (!(event.getWorld() instanceof ServerWorld)) return;

        ServerWorld world = (ServerWorld) event.getWorld();
        BlockPos    pos   = event.getPos();

        BlockState state     = world.getBlockState(pos);
        BlockState stateDown = world.getBlockState(pos.down());

        if (!(state.getBlock() instanceof LandmineBlock)) return;

        world.spawnParticle(new BlockParticleData(ParticleTypes.BLOCK, world.getBlockState(pos.down())), pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, 250, 0, 0, 0, 4);
        world.playSound(null, pos, stateDown.getSoundType().getPlaceSound(), SoundCategory.BLOCKS, 1, 1F);

        world.setBlockState(pos, state.with(BURIED, !state.get(BURIED)));
    }

    private static class EntityForceLoadData {
        public ChunkPos current = null, last = null;
        public boolean currentChunkWasForced = false;
    }

    private static final HashMap<Entity, EntityForceLoadData> forceLoadedEntities = new HashMap<>();

    @SubscribeEvent
    public void entityTick(EntityTickEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.world instanceof ServerWorld)) {
            return;
        }

        if (!entity.getTags().contains("forceLoad")) {
            return;
        }

        EntityForceLoadData forceLoadData = forceLoadedEntities.getOrDefault(entity, new EntityForceLoadData());

        Vector3d nextTickPos = entity.getPositionVec().add(entity.getMotion());
        forceLoadData.current = new ChunkPos(new BlockPos(nextTickPos));

        ServerWorld         serverWorld   = (ServerWorld) entity.world;
        ServerChunkProvider chunkProvider = serverWorld.getChunkProvider();

        if (!chunkProvider.isChunkLoaded(forceLoadData.current)) {
            if (forceLoadData.last != null) {
                serverWorld.forceChunk(forceLoadData.last.x, forceLoadData.last.z, false);
            }
            forceLoadData.last = forceLoadData.current;

            serverWorld.forceChunk(forceLoadData.current.x, forceLoadData.current.z, true);

            forceLoadData.currentChunkWasForced = true;
        } else {
            forceLoadData.currentChunkWasForced = false;
        }
    }

    @SubscribeEvent
    public void entityRemove(EntityLeaveWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.world instanceof ServerWorld)) {
            return;
        }

        if (!entity.getTags().contains("forceLoad")) {
            return;
        }

        EntityForceLoadData forceLoadData = forceLoadedEntities.getOrDefault(entity, new EntityForceLoadData());
        ServerWorld         serverWorld   = (ServerWorld) entity.world;

        if (forceLoadData.currentChunkWasForced) {
            serverWorld.forceChunk(forceLoadData.current.x, forceLoadData.current.z, false);
        }
    }

    @SubscribeEvent
    public void joinServerEvent(PlayerEvent.PlayerLoggedInEvent event) {
        WFMod.loadAllConfigs();
    }
}
