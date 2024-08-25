package io.github.zlovro.wfutil.tileentities.landmine;

import io.github.zlovro.wfutil.registry.WFTileEntities;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;

public class LandmineTileEntity extends TileEntity {

    public LandmineTileEntity() {
        this(WFTileEntities.LANDMINE_TYPE.get());
    }

    public String teamName = "";

    public LandmineTileEntity(TileEntityType<?> type) {
        super(type);
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        CompoundNBT tag = super.write(compound);
        tag.putString("teamName", teamName);
        return tag;
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        teamName = nbt.getString("teamName");
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        CompoundNBT nbtTag = new CompoundNBT();
        nbtTag.putString("teamName", teamName);
        return new SUpdateTileEntityPacket(getPos(), -1, nbtTag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        CompoundNBT tag = pkt.getNbtCompound();
        teamName = tag.getString("teamName");
    }
}
