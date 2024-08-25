package io.github.zlovro.wfutil.network;

import io.github.zlovro.wfutil.client.ClientBus;
import io.github.zlovro.wfutil.tileentities.mortar.MortarTileEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class MortarTileEntityUpdateMessage {
    float pitch, yaw, velocity;
    BlockPos targetPos;
    BlockPos pos;
    String   waypointId;

    public MortarTileEntityUpdateMessage() {

    }

    public static MortarTileEntityUpdateMessage decode(PacketBuffer buf) {
        MortarTileEntityUpdateMessage packet = new MortarTileEntityUpdateMessage();

        packet.velocity   = buf.readFloat();
        packet.pitch      = buf.readFloat();
        packet.yaw        = buf.readFloat();
        packet.targetPos  = buf.readBlockPos();
        packet.pos        = buf.readBlockPos();
        packet.waypointId = buf.readString();

        return packet;
    }

    public MortarTileEntityUpdateMessage(BlockPos targetPos, float velocity, float pitch, float yaw, BlockPos pos, String waypointId) {
        this.velocity   = velocity;
        this.pitch      = pitch;
        this.yaw        = yaw;
        this.targetPos  = targetPos;
        this.pos        = pos;
        this.waypointId = waypointId;
    }

    public MortarTileEntityUpdateMessage(MortarTileEntity mortar) {
        this(mortar.targetPos, mortar.velocity, mortar.pitch, mortar.yaw, mortar.getPos(), mortar.waypointId);
    }


    public void encode(PacketBuffer buf) {
        buf.writeFloat(velocity);
        buf.writeFloat(pitch);
        buf.writeFloat(yaw);
        buf.writeBlockPos(targetPos);
        buf.writeBlockPos(pos);
        buf.writeString(waypointId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxArg) {
        NetworkEvent.Context ctx = ctxArg.get();
        ctx.enqueueWork(() -> {
            AtomicReference<World> worldRef = new AtomicReference<>();
            if (ctx.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> worldRef.set(ClientBus.getClientWorld()));
            }

            if (ctx.getSender() != null) {
                worldRef.set(ctx.getSender().world);
            }

            if (worldRef.get() == null) {
                return;
            }

            MortarTileEntity mortar = (MortarTileEntity) worldRef.get().getTileEntity(pos);

            if (mortar == null) {
                return;
            }

            mortar.waypointId = waypointId;
            mortar.pitch      = pitch;
            mortar.yaw        = yaw;
            mortar.velocity   = velocity;
            mortar.targetPos  = targetPos;

            if (worldRef.get() instanceof ServerWorld) {
                mortar.predict();
            }
        });
        ctx.setPacketHandled(true);
    }
}
