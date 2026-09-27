package com.p1nero.structure_rewind_watch.mixin;

import com.p1nero.structure_rewind_watch.RewindManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    // Structure replay must never erase somebody's ownership-protected inhibitor.
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void rewind$preserveInhibitor(BlockPos pos, net.minecraft.world.level.block.state.BlockState state, boolean moving,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.level.block.state.BlockState> cir) {
        if (RewindManager.isPlacing() && ((LevelChunk) (Object) this).getBlockState(pos)
                .is(com.p1nero.structure_rewind_watch.StructureRewindWatchMod.INHIBITOR.get())) cir.setReturnValue(null);
    }

    // Vanilla inherits this method. Other mods may add an override to LevelChunk.
    // Inject into that override when present, without adding/replacing any method.
    @Inject(method = "markPosForPostprocessing", at = @At("HEAD"), cancellable = true, require = 0)
    private void rewind$markExistingOverride(BlockPos pos, CallbackInfo ci) {
        if (RewindManager.queuePostprocessing((ChunkAccess) (Object) this, pos)) ci.cancel();
    }
}
