package com.p1nero.structure_rewind_watch.inhibitor;

import com.p1nero.structure_rewind_watch.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class RewindInhibitorItem extends BlockItem {
    public RewindInhibitorItem(Block block, Properties properties) { super(block, properties); }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        if (Config.SPEC.isLoaded()) {
            lines.add(Component.translatable("tooltip.structure_rewind_watch.inhibitor_range", Config.INHIBITOR_RADIUS.get()).withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("tooltip.structure_rewind_watch.inhibitor_default").withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("tooltip.structure_rewind_watch.inhibitor_owner").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("tooltip.structure_rewind_watch.inhibitor_tick").withStyle(ChatFormatting.GRAY));
    }
}
