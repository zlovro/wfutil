package io.github.zlovro.wfutil.registry;

import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.tileentities.landmine.LandmineTileEntity;
import io.github.zlovro.wfutil.tileentities.mortar.MortarTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class WFTileEntities {
    public static final DeferredRegister<TileEntityType<?>> TILE_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, WFMod.MODID);

    public static final RegistryObject<TileEntityType<MortarTileEntity>> MORTAR_TYPE = TILE_ENTITY_TYPES.register("mortar", () -> TileEntityType.Builder.create(MortarTileEntity::new, WFBlocks.MORTAR.get()).build(null));
    public static final RegistryObject<TileEntityType<LandmineTileEntity>> LANDMINE_TYPE = TILE_ENTITY_TYPES.register("landmine", () -> TileEntityType.Builder.create(LandmineTileEntity::new, WFBlocks.LANDMINE.get()).build(null));


    public static void register(IEventBus bus) {
        TILE_ENTITY_TYPES.register(bus);
    }

}
