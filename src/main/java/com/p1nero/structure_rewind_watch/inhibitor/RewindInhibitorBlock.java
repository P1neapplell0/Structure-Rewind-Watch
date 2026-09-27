package com.p1nero.structure_rewind_watch.inhibitor;

import com.mojang.serialization.MapCodec;
import com.p1nero.structure_rewind_watch.StructureRewindWatchMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class RewindInhibitorBlock extends BaseEntityBlock {
    public static final MapCodec<RewindInhibitorBlock> CODEC = simpleCodec(RewindInhibitorBlock::new);
    public RewindInhibitorBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<RewindInhibitorBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RewindInhibitorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, StructureRewindWatchMod.INHIBITOR_ENTITY.get(), RewindInhibitorBlockEntity::serverTick);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof RewindInhibitorBlockEntity inhibitor) {
            inhibitor.setOwner(player.getUUID());
            inhibitor.announce();
        }
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide && !InhibitorProtection.mayBreak(player, level, pos)) return false;
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return false; }
}
