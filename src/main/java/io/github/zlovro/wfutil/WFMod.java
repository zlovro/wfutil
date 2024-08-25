package io.github.zlovro.wfutil;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.tac.guns.Config;
import io.github.zlovro.wfutil.client.ClientBus;
import io.github.zlovro.wfutil.entities.SoldierEntityRenderer;
import io.github.zlovro.wfutil.network.ConfigSyncMessage;
import io.github.zlovro.wfutil.registry.WFBlocks;
import io.github.zlovro.wfutil.registry.WFEntities;
import io.github.zlovro.wfutil.registry.WFItems;
import io.github.zlovro.wfutil.registry.WFTileEntities;
import io.github.zlovro.wfutil.tileentities.mortar.MortarRenderer;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nullable;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(WFMod.MODID)
public class WFMod {
    public static class ServerConfig implements Serializable {
        public final int   flagRadius                    = 256;
        public final float minimumFlagPlaceDistanceRatio = 0.75F;

        public final float mortarReloadTime           = 1.3F;
        public final float mortarRoundExplosionRadius = 4.0F;
        public final float mortarRange                = 512;

        public final float explosionSoundRange = 768;
        public final float gunSoundRange       = 512;

        public final int   soldierCooldownTicks          = 10 * 60;
        public final int   soldierAlertRadius            = 64;
        public final int   soldierSurrenderRadius        = soldierAlertRadius;
        public final int   soldierMaxHelpers             = 10;
        public final int   soldierMinHelpers             = 4;
        /**
         * how much time passes between the first shot and the first shot that hits
         */
        public final float soldierMissTimeSeconds        = 3F;
        public final float soldierReactionTime           = 1.5F;
        public final float soldierVisionPitchRange       = 50;
        public final float soldierVisionYawRange         = 60;
        public final float soldierMaxVisibleTargetRadius = 64;
        /**
         * how much the soldier must heal before returning to battle
         */
        public final float soldierHealRatio              = 0.75F;
        /**
         * soldier starts healing when his health <= {@link #soldierMinHealth}
         */
        public final float soldierMinHealth              = 5;
        public final float soldierAttackSpeed            = 1.250F;
        public final float soldierLookForTargetSpeed     = 1.125F;
        public final float soldierWanderSpeed            = 1.0F;
        public final int   soldierMaxCoverDistance       = 50;
        public final int   soldierThreatCooldownTicks    = 30 * 20;

        public final int   soldierFollowRange = 56;
        public final int   soldierMaxHealth   = 25;
        public final float soldierMoveSpeed   = 0.3F;

        public final int minPathfindCooldownMs = 3 * 50;
    }

    public static class ClientConfig implements Serializable {

    }

    public static class CommonConfig implements Serializable {
        public transient boolean debug = false;
    }

    public static final String MODID = "wfutil";

    public static ServerConfig configServer;
    public static ClientConfig configClient;
    public static CommonConfig configCommon;

    public static final Gson   GSON   = new Gson();
    public static final Random RANDOM = new Random(System.currentTimeMillis());

    public static final UUID UUID_NULL = new UUID(0, 0);

    public static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(MODID);

    public static byte[] download(String url, int timeout) {
        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int                   b;
            while ((b = connection.getInputStream().read()) != -1) {
                baos.write(b);
            }

            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new byte[0];
    }

    public static InputStream readFromResources(String assetsPath) {
        return WFMod.class.getResourceAsStream(assetsPath);
    }

    public WFMod() {
        loadAllConfigs();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        bus.addListener(this::commonSetup);
        bus.addListener(this::clientSetup);
        bus.addListener(this::serverSetup);

        bus.addListener(WFEntities::entityAttributeCreateEvent);

        WFTileEntities.register(bus);
        WFBlocks.register(bus);
        WFItems.register(bus);
        WFEntities.register(bus);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Nullable
    public static <T> T loadConfig(String configName, Class<T> clazz) {
        T config = null;

        try {
            File cfgFile = new File("config/wfutil/" + configName + ".json");
            if (cfgFile.exists()) {
                config = GSON.fromJson(new FileReader(cfgFile), clazz);
            }

            if (config == null) {
                config = clazz.newInstance();
            }

            cfgFile.getParentFile().mkdirs();

            JsonWriter writer = new JsonWriter(new FileWriter(cfgFile));
            writer.setIndent("    ");

            GSON.toJson(config, Config.class, writer);

            writer.flush();
            writer.close();
        } catch (Exception e) {
            LOGGER.error(e);
        }

        return config;
    }

    private static final HashMap<String, Color> colorMap = new HashMap() {
        {
            put("black", Color.BLACK);
            put("red", Color.RED);
            put("green", Color.GREEN);
            put("yellow", Color.YELLOW);
            put("blue", Color.BLUE);
            put("magenta", Color.MAGENTA);
            put("cyan", Color.CYAN);
            put("white", Color.WHITE);
            put("gray", Color.GRAY);
        }
    };

    public static Color getColorByName(String name) {
        if (name.startsWith("#"))
        {
            return Color.decode(name);
        }

        return colorMap.get(name);
    }

    public static void loadClientConfig() {
        configClient = loadConfig("client", ClientConfig.class);
    }

    public static void loadServerConfig() {
        configServer = loadConfig("server", ServerConfig.class);
    }

    public static void loadCommonConfig() {
        configCommon       = loadConfig("common", CommonConfig.class);
        configCommon.debug = System.getenv().containsKey("MOD_CLASSES");
    }

    public static void loadAllConfigs() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> WFMod::loadClientConfig);

        WFMod.loadCommonConfig();
        WFMod.loadServerConfig();

        DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> () -> {
            if (WFNetwork.registered) {
                WFNetwork.INSTANCE.send(PacketDistributor.ALL.noArg(), new ConfigSyncMessage(configClient, configCommon, configServer));
            }

            Team.loadTeams();
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Locale.setDefault(Locale.US);

        // System.setProperty("fml.doNotBackup", "true");
        // LOGGER.info("Disabled backups");

        WFNetwork.registerMessages();
        LocalReverseGeocodingDatabase.loadData();
    }


    private void clientSetup(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new ClientBus());

        ClientRegistry.bindTileEntityRenderer(WFTileEntities.MORTAR_TYPE.get(), MortarRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(WFEntities.SOLDIER_ENTITY_TYPE.get(), SoldierEntityRenderer::factory);

        if (configCommon.debug) {
            ClientBus.enableMultiplayer();
        }

        WFRPC.init();
    }

    private void serverSetup(final FMLDedicatedServerSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new ServerBus());
    }
}
