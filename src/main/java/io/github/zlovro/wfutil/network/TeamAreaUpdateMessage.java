package io.github.zlovro.wfutil.network;

import io.github.zlovro.wfutil.Team;
import io.github.zlovro.wfutil.client.JourneymapPlugin;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;

public class TeamAreaUpdateMessage {
    // A default constructor is always required
    public TeamAreaUpdateMessage() {
    }

    public ArrayList<Team> teams;

    public TeamAreaUpdateMessage(ArrayList<Team> teams) {
        this.teams = teams;
    }

    public static void encode(TeamAreaUpdateMessage msg, PacketBuffer buf) {
        MessageUtil.writeList(msg.teams, buf, team -> {
            buf.writeUniqueId(team.owner);
            buf.writeString(team.name);
            buf.writeString(team.nameColor);
            buf.writeString(team.flagBlock);

            MessageUtil.writeList(team.members, buf, buf::writeUniqueId);
            MessageUtil.writeList(team.flags, buf, pos -> {
                buf.writeInt(pos.getX());
                buf.writeInt(pos.getZ());
            });

            buf.writeUniqueId(team.teamId);
        });
    }

    public static TeamAreaUpdateMessage decode(PacketBuffer buf) {
        TeamAreaUpdateMessage msg = new TeamAreaUpdateMessage();

        msg.teams = MessageUtil.readList(buf, () -> {
            Team team = new Team(buf.readUniqueId(), buf.readString(), buf.readString(), buf.readString());

            team.members = MessageUtil.readList(buf, buf::readUniqueId);
            team.flags   = MessageUtil.readList(buf, () -> new BlockPos(buf.readInt(), 0, buf.readInt()));

            team.teamId = buf.readUniqueId();

            return team;
        });

        return msg;
    }

    public static void handle(TeamAreaUpdateMessage msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();

        if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            return;
        }

        ctx.enqueueWork(() -> JourneymapPlugin.teamsToDraw = Team.teams = msg.teams);
        ctx.setPacketHandled(true);
    }
}
