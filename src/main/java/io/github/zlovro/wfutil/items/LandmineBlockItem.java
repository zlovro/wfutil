package io.github.zlovro.wfutil.items;

import io.github.zlovro.wfutil.WFTab;
import io.github.zlovro.wfutil.registry.WFBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;

import java.util.Collection;

public class LandmineBlockItem extends BlockItem {
    public LandmineBlockItem() {
        super(WFBlocks.LANDMINE.get(), new Properties());
    }

    @Override
    public Collection<ItemGroup> getCreativeTabs() {
        return WFTab.TABS;
    }
}
