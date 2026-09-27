package com.p1nero.structure_rewind_watch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.p1nero.structure_rewind_watch.StructureRewindWatchMod;
import com.p1nero.structure_rewind_watch.inhibitor.RewindInhibitorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class InhibitorRenderer implements BlockEntityRenderer<RewindInhibitorBlockEntity> {
    private final ItemRenderer items;
    private final ItemStack watch = StructureRewindWatchMod.WATCH.get().getDefaultInstance();
    public InhibitorRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    @Override public void render(RewindInhibitorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (entity.getLevel() == null) return;
        float time = (entity.getLevel().getGameTime() % 72000) + partialTick;
        pose.pushPose();
        pose.translate(.5, .5 + Math.sin(time * .045) * .035, .5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2));
        pose.scale(.65f, .65f, .65f);
        items.renderStatic(watch, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }
    @Override public int getViewDistance() { return 48; }
}
