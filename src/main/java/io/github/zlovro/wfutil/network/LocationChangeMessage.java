package io.github.zlovro.wfutil.network;

import io.github.zlovro.wfutil.client.LocationRenderer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class LocationChangeMessage
{
    // A default constructor is always required
    public LocationChangeMessage()
    {
    }

    public String location;

    public LocationChangeMessage(String location)
    {
        this.location = location;
    }

    public static void encode(LocationChangeMessage msg, PacketBuffer buf)
    {
        // Writes the int into the buf
        buf.writeCharSequence(msg.location, StandardCharsets.UTF_8);
    }

    public static LocationChangeMessage decode(PacketBuffer buf)
    {
        return new LocationChangeMessage(buf.toString(StandardCharsets.UTF_8));
    }

    public static void handle(LocationChangeMessage msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();

        if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT)
        {
            return;
        }

        ctx.enqueueWork(() -> {
            LocationRenderer.text = msg.location;
        });
        ctx.setPacketHandled(true);
    }
}
