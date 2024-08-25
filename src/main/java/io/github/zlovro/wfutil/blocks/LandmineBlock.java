package io.github.zlovro.wfutil.blocks;

import io.github.zlovro.wfutil.Team;
import io.github.zlovro.wfutil.registry.WFTileEntities;
import io.github.zlovro.wfutil.tileentities.landmine.LandmineTileEntity;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;

public class LandmineBlock extends Block {
    public static final VoxelShape SHAPE = VoxelShapes.create(new AxisAlignedBB(0, 0, 0, 1, 0.175, 1));

    public static final BooleanProperty BURIED = BooleanProperty.create("buried");

    public LandmineBlock() {
        super(AbstractBlock.Properties.create(Material.IRON).sound(SoundType.NETHERITE).hardnessAndResistance(0.8F).notSolid());
        setDefaultState(getStateContainer().getBaseState().with(BURIED, false));
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return WFTileEntities.LANDMINE_TYPE.get().create();
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!(placer instanceof ServerPlayerEntity))
        {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) placer;

        Team team = Team.getPlayerTeam(player.getUniqueID());
        if (team == null)
        {
            return;
        }

        LandmineTileEntity te = (LandmineTileEntity) worldIn.getTileEntity(pos);
        if (te == null)
        {
            return;
        }

        te.teamName = team.name;
    }

    public static void explode(BlockPos pos, World world) {
        if (!(world instanceof ServerWorld)) return;

        world.createExplosion(null, pos.getX(), pos.getY(), pos.getZ(), 5F, Explosion.Mode.BREAK);
    }

    @Override
    public void onExplosionDestroy(World worldIn, BlockPos pos, Explosion explosionIn) {
        explode(pos, worldIn);
    }

    @Override
    public void onEntityCollision(BlockState state, World worldIn, BlockPos pos, Entity entityIn) {
        if (!entityIn.isCrouching()) {
            LandmineBlock.explode(pos, worldIn);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState p_149645_1_) {
        if (p_149645_1_.get(BURIED))
        {
            return BlockRenderType.INVISIBLE;
        }
        return BlockRenderType.MODEL;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        super.fillStateContainer(builder);
        builder.add(BURIED);
    }
}
