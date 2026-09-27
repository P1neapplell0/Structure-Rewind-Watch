package com.p1nero.structure_rewind_watch.mixin;

import com.p1nero.structure_rewind_watch.client.RewindClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void rewind$topmost(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        if (!RewindClient.behindMenu()) RewindClient.renderOverlay(delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void rewind$belowPauseMenu(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        if (RewindClient.behindMenu()) RewindClient.renderOverlay(delta.getGameTimeDeltaPartialTick(false));
    }
}
