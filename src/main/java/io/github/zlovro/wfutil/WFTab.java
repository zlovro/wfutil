package io.github.zlovro.wfutil;

import io.github.zlovro.wfutil.registry.WFItems;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

import java.util.Collection;
import java.util.Collections;

public class WFTab extends ItemGroup {
    public static final WFTab                 TAB  = new WFTab();
    public static final Collection<ItemGroup> TABS = Collections.singleton(TAB);

    public WFTab() {
        super(WFMod.MODID);
    }

    @Override
    public ItemStack createIcon() {
        return WFItems.MORTAR.get().getDefaultInstance();
    }
}
