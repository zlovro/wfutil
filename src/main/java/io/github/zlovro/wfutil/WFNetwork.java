package io.github.zlovro.wfutil;

import io.github.zlovro.wfutil.network.ConfigSyncMessage;
import io.github.zlovro.wfutil.network.LocationChangeMessage;
import io.github.zlovro.wfutil.network.TeamAreaUpdateMessage;
import io.github.zlovro.wfutil.network.MortarTileEntityUpdateMessage;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class WFNetwork {
    private static final String        PROTOCOL_VERSION = "1";
    public static final  SimpleChannel INSTANCE         = NetworkRegistry.newSimpleChannel(new ResourceLocation(WFMod.MODID, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    public static boolean registered = false;

    public static void registerMessages() {
        registered = true;

        int id = 0;

        INSTANCE.registerMessage(id++, LocationChangeMessage.class, LocationChangeMessage::encode, LocationChangeMessage::decode, LocationChangeMessage::handle);
        INSTANCE.registerMessage(id++, TeamAreaUpdateMessage.class, TeamAreaUpdateMessage::encode, TeamAreaUpdateMessage::decode, TeamAreaUpdateMessage::handle);
        INSTANCE.registerMessage(id++, MortarTileEntityUpdateMessage.class, MortarTileEntityUpdateMessage::encode, MortarTileEntityUpdateMessage::decode, MortarTileEntityUpdateMessage::handle);
        INSTANCE.registerMessage(id++, ConfigSyncMessage.class, ConfigSyncMessage::encode, ConfigSyncMessage::decode, ConfigSyncMessage::handle);
    }
}
