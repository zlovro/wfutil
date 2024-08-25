package io.github.zlovro.wfutil;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;

public class WFRPC {
    private static DiscordRichPresence presence;

    public static void init()
    {
        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder().setReadyEventHandler((user) -> {
            System.out.println("Welcome " + user.username + "#" + user.discriminator + "!");
        }).build();
        DiscordRPC.discordInitialize("1270512434922258453", handlers, true);

        presence = new DiscordRichPresence.Builder("meljem crnce").setStartTimestamps(System.currentTimeMillis()).setDetails("").build();
        flush();
    }

    public static void flush()
    {
        DiscordRPC.discordUpdatePresence(presence);
    }
}
