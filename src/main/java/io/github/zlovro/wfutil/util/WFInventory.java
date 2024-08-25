package io.github.zlovro.wfutil.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class WFInventory {
    public static int index(IInventory inv, Item item) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i).getItem() == item) {
                return i;
            }
        }

        return 0;
    }

    public static boolean contains(IInventory inventory, Item... items) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            for (Item item : items) {
                if (item.equals(inventory.getStackInSlot(i).getItem())) return true;
            }
        }
        return false;
    }

    public static void take(IInventory inv, Item item, int amount) {
        if (!contains(inv, item)) return;
        int idx = index(inv, item);
        ItemStack itemStack = inv.getStackInSlot(idx);
        if (itemStack.getCount() == 1) {
            inv.removeStackFromSlot(idx);
            return;
        }
        itemStack.setCount(itemStack.getCount() - 1);
        inv.setInventorySlotContents(idx, itemStack);
    }
}
