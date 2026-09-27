package com.p1nero.structure_rewind_watch.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;

/** Reproduces a mod adding the inherited method before our mixins run. Development only. */
@Mixin(value = LevelChunk.class, priority = 1100)
public abstract class ExistingLevelChunkOverride extends ChunkAccess {
    protected ExistingLevelChunkOverride(ChunkPos pos, UpgradeData upgrade, LevelHeightAccessor height,
                                        Registry<Biome> biomes, long time, LevelChunkSection[] sections, BlendingData blending) {
        super(pos, upgrade, height, biomes, time, sections, blending);
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        // Unlike vanilla's no-op, this observable behavior must remain outside a rewind.
        if (!isOutsideBuildHeight(pos)) addPackedPostProcess(ProtoChunk.packOffsetCoordinates(pos), getSectionIndex(pos.getY()));
    }
}
