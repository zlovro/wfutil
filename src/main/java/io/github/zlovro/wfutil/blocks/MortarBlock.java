package io.github.zlovro.wfutil.blocks;

import io.github.zlovro.wfutil.gui.MortarScreen;
import io.github.zlovro.wfutil.registry.WFItems;
import io.github.zlovro.wfutil.registry.WFTileEntities;
import io.github.zlovro.wfutil.tileentities.mortar.MortarTileEntity;
import io.github.zlovro.wfutil.util.WFList;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.block.material.PushReaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;

import javax.annotation.Nullable;
import java.util.List;

public class MortarBlock extends Block {
    // public static final Properties PROPERTIES = AbstractBlock.Properties.from(Blocks.IRON_BLOCK).setOpaque((a, b, c) -> false).sound(SoundType.NETHERITE).notSolid();
    public static final Properties PROPERTIES = AbstractBlock.Properties.create(new Material(MaterialColor.BLUE, false, true, true, false, false, false, PushReaction.IGNORE), MaterialColor.BLUE).sound(SoundType.NETHERITE).notSolid();
    public static final String     ID         = "mortar";

    public MortarBlock() {
        super(PROPERTIES);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return WFTileEntities.MORTAR_TYPE.get().create();
    }

    @Nullable
    @Override
    public ToolType getHarvestTool(BlockState state) {
        return ToolType.PICKAXE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        return new WFList<>(WFItems.MORTAR.get().getDefaultInstance());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        MortarTileEntity tileEntity = (MortarTileEntity) worldIn.getTileEntity(pos);

        if (tileEntity == null) {
            return ActionResultType.SUCCESS;
        }

        if (worldIn instanceof ClientWorld) {
            Minecraft.getInstance().displayGuiScreen(new MortarScreen(tileEntity));
        }

        return ActionResultType.SUCCESS;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }
}