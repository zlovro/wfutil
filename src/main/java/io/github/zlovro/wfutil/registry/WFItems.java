package io.github.zlovro.wfutil.registry;

import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.WFTab;
import io.github.zlovro.wfutil.items.LandmineBlockItem;
import io.github.zlovro.wfutil.items.MortarBlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class WFItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, WFMod.MODID);
    public static final RegistryObject<Item> MORTAR = ITEMS.register("mortar_block_item", MortarBlockItem::new);
    public static final RegistryObject<Item> LANDMINE = ITEMS.register("landmine", LandmineBlockItem::new);

    public static final RegistryObject<Item> SPAWNEGG_SOLDIER = ITEMS.register("soldier_spawn_egg", () -> new ForgeSpawnEggItem(WFEntities.SOLDIER_ENTITY_TYPE, 0x434a33, 0x67a1c7, new Item.Properties().group(WFTab.TAB)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
