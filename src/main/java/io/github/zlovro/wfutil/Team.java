package io.github.zlovro.wfutil;

import com.google.common.reflect.TypeToken;
import com.tac.guns.network.PacketHandler;
import io.github.zlovro.wfutil.network.TeamAreaUpdateMessage;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nullable;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Team {
    public static HashMap<String, String> FLAGS = new HashMap<String, String>() {{
        put("white", Blocks.WHITE_BANNER.getRegistryName().toString());
        put("orange", Blocks.ORANGE_BANNER.getRegistryName().toString());
        put("magenta", Blocks.MAGENTA_BANNER.getRegistryName().toString());
        put("light_blue", Blocks.LIGHT_BLUE_BANNER.getRegistryName().toString());
        put("yellow", Blocks.YELLOW_BANNER.getRegistryName().toString());
        put("lime", Blocks.LIME_BANNER.getRegistryName().toString());
        put("pink", Blocks.PINK_BANNER.getRegistryName().toString());
        put("gray", Blocks.GRAY_BANNER.getRegistryName().toString());
        put("light_gray", Blocks.LIGHT_GRAY_BANNER.getRegistryName().toString());
        put("cyan", Blocks.CYAN_BANNER.getRegistryName().toString());
        put("purple", Blocks.PURPLE_BANNER.getRegistryName().toString());
        put("blue", Blocks.BLUE_BANNER.getRegistryName().toString());
        put("brown", Blocks.BROWN_BANNER.getRegistryName().toString());
        put("green", Blocks.GREEN_BANNER.getRegistryName().toString());
        put("red", Blocks.RED_BANNER.getRegistryName().toString());
        put("black", Blocks.BLACK_BANNER.getRegistryName().toString());
    }};

    public static ArrayList<Team> teams = new ArrayList<>();

    public UUID teamId;
    public UUID owner;

    public String name;
    public String nameColor;
    public String flagBlock;

    public ArrayList<UUID>     members = new ArrayList<>();
    public ArrayList<BlockPos> flags   = new ArrayList<>();

    public Team(UUID owner, String name, String nameColor, String flagBlock) {
        do {
            teamId = UUID.randomUUID();
        } while (Team.teams.stream().anyMatch(t -> t.teamId.equals(teamId) || t.teamId.equals(WFMod.UUID_NULL)));

        this.name      = name;
        this.nameColor = nameColor;
        this.flagBlock = flagBlock;
        this.owner     = owner;

        members.add(owner);
    }

    public static void loadTeams() {
        try {
            Team.teams = WFMod.GSON.fromJson(new FileReader("config/wfutil/teams.json"), new TypeToken<ArrayList<Team>>() {
            }.getType());

            for (Team team : Team.teams) {
                if (team == null) {
                    throw new NullPointerException();
                }

                if (team.name == null) {
                    throw new NullPointerException();
                }
            }
        } catch (Exception e) {
            WFMod.LOGGER.error(e);
            Team.teams = new ArrayList<>();
        }
    }

    public void register() {
        teams.add(this);

        WFNetwork.INSTANCE.send(PacketDistributor.ALL.noArg(), new TeamAreaUpdateMessage());
    }

    public boolean hasPlayer(UUID player) {
        return owner.equals(player) || members.contains(player);
    }

    public static Team getTeamById(UUID teamId) {
        return teams.stream().filter(t -> t.teamId.equals(teamId)).findFirst().orElse(null);
    }

    public static Team getTeamByName(String name) {
        return teams.stream().filter(t -> t.name.equals(name)).findFirst().orElse(null);
    }

    public static long getTotalAreaOwned() {
        return teams.stream().mapToLong(Team::getAreaOwned).sum();
    }

    public long getAreaOwned() {
        return flags.size() * 4L * WFMod.configServer.flagRadius;
    }

    public static boolean exists(String name) {
        AtomicBoolean exists = new AtomicBoolean(false);
        teams.forEach(t -> {
            if (t.name.equals(name)) {
                exists.set(true);
            }
        });

        return exists.get();
    }

    public static boolean anyTeamHasColor(String color) {
        return teams.stream().anyMatch(t -> t.nameColor.equals(color));
    }

    public static boolean anyTeamHasFlag(String flag) {
        return teams.stream().anyMatch(t -> t.flagBlock.equals(flag));
    }

    public void removePlayerFromTeamRaw(ServerPlayerEntity player) {
        if (owner.equals(player.getUniqueID())) {
            if (members.size() == 1) {
                World world = player.world.getServer().getWorld(World.OVERWORLD);
                for (BlockPos flag : flags) {
                    world.setBlockState(flag, Blocks.AIR.getDefaultState(), 0);
                }
                Team.teams.remove(this);
                return;
            }

            members.remove(player.getUniqueID());
            owner = members.get(0);
            return;
        }

        members.remove(player.getUniqueID());
    }

    public static Team getPlayerTeam(PlayerEntity player) {
        return getPlayerTeam(player.getUniqueID());
    }

    @Nullable
    public static Team getPlayerTeam(UUID playerUuid) {
        return teams.stream().filter(t -> t.members.contains(playerUuid)).findFirst().orElse(null);
    }

    public static BlockPos getNearestFlag(BlockPos pos) {
        float    minDistance = Float.MAX_VALUE;
        BlockPos nearest     = new BlockPos(30_000_000, 0, 30_000_000);
        for (Team team : teams) {
            for (BlockPos flagPos : team.flags) {
                float distance = (float) pos.distanceSq(flagPos);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest     = flagPos;
                }
            }
        }

        return nearest;
    }

    public static Team getFlagOwner(BlockPos pos) {
        for (Team team : teams) {
            for (BlockPos pos2 : team.flags) {
                if (pos2.equals(pos)) {
                    return team;
                }
            }
        }

        return null;
    }

    public static Team getOwnerOfAreaAroundBlock(BlockPos pos) {
        float maxDistance = WFMod.configServer.flagRadius * WFMod.configServer.flagRadius * 2;
        for (Team team : teams) {
            for (BlockPos flagPos : team.flags) {
                float distance = (float) pos.distanceSq(flagPos);
                if (distance < maxDistance) {
                    return team;
                }
            }
        }

        return null;
    }
}
