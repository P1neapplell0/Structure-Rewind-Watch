package com.p1nero.structure_rewind_watch.inhibitor;

import com.p1nero.structure_rewind_watch.Config;
import com.p1nero.structure_rewind_watch.StructureRewindWatchMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class RewindInhibitorBlockEntity extends BlockEntity {
    private UUID owner;
    private boolean firstTick = true;
    public RewindInhibitorBlockEntity(BlockPos pos, BlockState state) {
        super(StructureRewindWatchMod.INHIBITOR_ENTITY.get(), pos, state);
    }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; setChanged(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, RewindInhibitorBlockEntity inhibitor) {
        // Spread multiple inhibitors across the 20-tick window rather than bursting together.
        if (inhibitor.firstTick || Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0) {
            inhibitor.firstTick = false;
            inhibitor.announce();
        }
    }
    public void announce() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        double radiusSquared = (double) Config.INHIBITOR_RADIUS.get() * Config.INHIBITOR_RADIUS.get();
        for (var player : server.players()) {
            if (player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= radiusSquared)
                InhibitorProtection.notice(player, worldPosition);
        }
    }
}
