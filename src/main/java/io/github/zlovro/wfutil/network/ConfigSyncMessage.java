package io.github.zlovro.wfutil.network;

import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.client.LocationRenderer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import org.apache.commons.lang3.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class ConfigSyncMessage {
    public WFMod.ClientConfig clientConfig;
    public WFMod.CommonConfig commonConfig;
    public WFMod.ServerConfig serverConfig;

    // A default constructor is always required
    public ConfigSyncMessage() {
    }

    public ConfigSyncMessage(WFMod.ClientConfig clientConfig, WFMod.CommonConfig commonConfig, WFMod.ServerConfig serverConfig) {
        this.clientConfig = clientConfig;
        this.commonConfig = commonConfig;
        this.serverConfig = serverConfig;
    }

    public static void encode(ConfigSyncMessage msg, PacketBuffer buf) {
        buf.writeByteArray(SerializationUtils.serialize(msg.clientConfig));
        buf.writeByteArray(SerializationUtils.serialize(msg.commonConfig));
        buf.writeByteArray(SerializationUtils.serialize(msg.serverConfig));
    }

    public static ConfigSyncMessage decode(PacketBuffer buf) {
        WFMod.ClientConfig clientConfig = SerializationUtils.deserialize(buf.readByteArray());
        WFMod.CommonConfig commonConfig = SerializationUtils.deserialize(buf.readByteArray());
        WFMod.ServerConfig serverConfig = SerializationUtils.deserialize(buf.readByteArray());

        return new ConfigSyncMessage(clientConfig, commonConfig, serverConfig);
    }

    public static void handle(ConfigSyncMessage msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();

        if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            return;
        }

        ctx.enqueueWork(() -> {
            WFMod.configCommon = msg.commonConfig;
            WFMod.configServer = msg.serverConfig;
        });
        ctx.setPacketHandled(true);
    }
}
