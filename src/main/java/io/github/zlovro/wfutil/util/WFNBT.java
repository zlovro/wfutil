package io.github.zlovro.wfutil.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

public class WFNBT {
    public static final StringNBT NBT_NULL = StringNBT.valueOf("null");

    public static INBT writeBlockPos(@Nullable BlockPos pos) {
        if (pos == null) {
            return NBT_NULL;
        }

        return net.minecraft.nbt.NBTUtil.writeBlockPos(pos);
    }

    @Nullable
    public static BlockPos readBlockPos(INBT tag) {
        if (tag instanceof CompoundNBT) {
            return net.minecraft.nbt.NBTUtil.readBlockPos((CompoundNBT) tag);
        }

        return null;
    }

    public static INBT writeUuid(@Nullable UUID uuid) {
        if (uuid == null) {
            return NBT_NULL;
        }

        return net.minecraft.nbt.NBTUtil.func_240626_a_(uuid);
    }

    @Nullable
    public static UUID readUuid(INBT tag) {
        if (tag instanceof IntArrayNBT) {
            return net.minecraft.nbt.NBTUtil.readUniqueId(tag);
        }

        return null;
    }

    public static INBT writeNullable(@Nullable INBT value) {
        if (value == null) {
            return NBT_NULL;
        }

        return value;
    }

    @Nullable
    public static INBT readNullable(INBT tag) {
        if (tag instanceof StringNBT) {
            return tag.equals(NBT_NULL) ? null : tag;
        }

        return tag;
    }
}
