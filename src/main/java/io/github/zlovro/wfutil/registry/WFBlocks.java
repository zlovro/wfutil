package io.github.zlovro.wfutil.registry;

import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.blocks.LandmineBlock;
import io.github.zlovro.wfutil.blocks.MortarBlock;
import net.minecraft.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class WFBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, WFMod.MODID);
    public static final RegistryObject<Block> MORTAR = BLOCKS.register(MortarBlock.ID, MortarBlock::new);
    public static final RegistryObject<Block> LANDMINE = BLOCKS.register("landmine", LandmineBlock::new);

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
    }
}
