package com.p1nero.structure_rewind_watch;

import com.p1nero.structure_rewind_watch.inhibitor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StructureRewindWatchMod.MODID)
@PrefixGameTestTemplate(false)
public final class InhibitorGameTests {
    @GameTest(template = "empty", batch = "inhibitor", timeoutTicks = 180)
    public static void rangeOwnershipAndExpiry(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        player.setNoGravity(true);
        BlockPos pos = helper.absolutePos(new BlockPos(0, 8, 0));
        int radius = Config.INHIBITOR_RADIUS.get();
        level.setBlockAndUpdate(pos, StructureRewindWatchMod.INHIBITOR.get().defaultBlockState());
        var inhibitor = (RewindInhibitorBlockEntity) level.getBlockEntity(pos);
        inhibitor.setOwner(player.getUUID());
        player.teleportTo(pos.getX() + .5 + radius, pos.getY() + .5, pos.getZ() + .5);
        inhibitor.announce();
        helper.assertTrue(InhibitorProtection.isBlocked(player), "Inclusive radius boundary and owner is blocked");
        player.teleportTo(pos.getX() + .501 + radius, pos.getY() + .5, pos.getZ() + .5);
        helper.assertTrue(!InhibitorProtection.isBlocked(player), "Leaving the radius clears restriction immediately");
        player.teleportTo(pos.getX() + .5, pos.getY() + .5 + radius + .001, pos.getZ() + .5);
        helper.assertTrue(!InhibitorProtection.isBlocked(player), "Distance includes vertical axis");
        player.teleportTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        inhibitor.announce();
        helper.assertTrue(InhibitorProtection.mayBreak(player, level, pos), "Placer may break");
        var data = inhibitor.saveWithoutMetadata(level.registryAccess());
        var copy = new RewindInhibitorBlockEntity(pos, inhibitor.getBlockState());
        copy.loadWithComponents(data, level.registryAccess());
        helper.assertTrue(player.getUUID().equals(copy.getOwner()), "Owner survives NBT save/load");
        inhibitor.setOwner(java.util.UUID.randomUUID());
        var denied = new BlockEvent.BreakEvent(level, pos, inhibitor.getBlockState(), player);
        InhibitorProtection.breakBlock(denied);
        helper.assertTrue(denied.isCanceled(), "Non-owner break event canceled");
        helper.assertTrue(!StructureRewindWatchMod.INHIBITOR.get().onDestroyedByPlayer(inhibitor.getBlockState(), level, pos, player, true, level.getFluidState(pos)), "Direct player destruction is also denied");
        player.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 2, false));
        helper.assertTrue(InhibitorProtection.mayBreak(player, level, pos), "OP may dismantle");
        helper.assertTrue(InhibitorProtection.isBlocked(player), "OP cannot bypass suppression");
        helper.assertTrue(inhibitor.getBlockState().getPistonPushReaction() == PushReaction.BLOCK, "Pistons cannot move the inhibitor");
        helper.assertTrue(!inhibitor.getBlockState().canEntityDestroy(level, pos, player), "Entity destruction cannot bypass protection");
        BlockPos secondPos = pos.offset(3, 0, 0);
        level.setBlockAndUpdate(secondPos, StructureRewindWatchMod.INHIBITOR.get().defaultBlockState());
        var second = (RewindInhibitorBlockEntity) level.getBlockEntity(secondPos);
        second.setOwner(player.getUUID()); second.announce();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.assertTrue(InhibitorProtection.isBlocked(player), "Other inhibitor still blocks after first removed");
        level.setBlockAndUpdate(secondPos, Blocks.AIR.defaultBlockState());
        helper.assertTrue(!InhibitorProtection.isBlocked(player), "Removal invalidates announcements immediately");
        // A ticking block announces without an explicit item-use search.
        level.setBlockAndUpdate(secondPos, StructureRewindWatchMod.INHIBITOR.get().defaultBlockState());
        var ticking = (RewindInhibitorBlockEntity) level.getBlockEntity(secondPos);
        ticking.setOwner(player.getUUID());
        RewindInhibitorBlockEntity.serverTick(level, secondPos, ticking.getBlockState(), ticking);
        helper.assertTrue(InhibitorProtection.isBlocked(player), "Block entity tick announces to player");
        var savedRadius = Config.INHIBITOR_RADIUS.get();
        try {
            Config.INHIBITOR_RADIUS.set(1);
            helper.assertTrue(!InhibitorProtection.isBlocked(player), "Config radius takes effect without rescanning blocks");
        } finally { Config.INHIBITOR_RADIUS.set(savedRadius); }
        level.setBlockAndUpdate(secondPos, Blocks.AIR.defaultBlockState());
        player.getServer().getPlayerList().deop(player.getGameProfile());
        player.getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
