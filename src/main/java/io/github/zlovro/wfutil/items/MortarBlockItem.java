package io.github.zlovro.wfutil.items;

import io.github.zlovro.wfutil.WFTab;
import io.github.zlovro.wfutil.registry.WFBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.IDyeableArmorItem;
import net.minecraft.item.ItemGroup;

import java.util.Collection;

public class MortarBlockItem extends BlockItem implements IDyeableArmorItem {
    public MortarBlockItem() {
        super(WFBlocks.MORTAR.get(), new Properties());
    }

    @Override
    public Collection<ItemGroup> getCreativeTabs() {
        return WFTab.TABS;
    }
}
