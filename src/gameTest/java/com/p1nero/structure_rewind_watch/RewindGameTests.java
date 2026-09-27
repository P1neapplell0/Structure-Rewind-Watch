package com.p1nero.structure_rewind_watch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(StructureRewindWatchMod.MODID)
@PrefixGameTestTemplate(false)
public class RewindGameTests {
    @GameTest(template = "empty")
    public static void preservesOtherModsPostprocessing(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        var chunk = helper.getLevel().getChunkAt(pos);
        int section = chunk.getSectionIndex(pos.getY());
        var list = chunk.getPostProcessing()[section];
        int before = list == null ? 0 : list.size();
        chunk.markPosForPostprocessing(pos);
        list = chunk.getPostProcessing()[section];
        int after = list == null ? 0 : list.size();
        int expected = Boolean.getBoolean("rewind.compatibilityTest") ? 1 : 0;
        helper.assertTrue(after == before + expected, "Outside rewind, preserve vanilla or another mod's override");
        if (expected == 1) list.removeShort(list.size() - 1);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void searchDistance(GameTestHelper helper) {
        BoundingBox box = new BoundingBox(-20, 60, -20, -10, 70, -10);
        helper.assertTrue(RewindManager.distanceSquared(new BlockPos(-15, 65, -15), box) == 0, "Inside structure");
        helper.assertTrue(RewindManager.distanceSquared(new BlockPos(0, 65, -15), box) == 100, "Exactly ten blocks");
        helper.assertTrue(RewindManager.distanceSquared(new BlockPos(0, 80, -15), box) == 200, "Diagonal must not count as ten blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regexRules(GameTestHelper helper) {
        try {
            Config.WHITELIST.set(List.of("minecraft:.*"));
            Config.BLACKLIST.set(List.of("minecraft:(stronghold|fortress)"));
            helper.assertTrue(Config.allowed("minecraft:desert_pyramid"), "Whitelist accepts vanilla");
            helper.assertTrue(!Config.allowed("minecraft:stronghold"), "Blacklist wins");
            helper.assertTrue(!Config.allowed("other:desert_pyramid"), "Whitelist rejects other namespaces");
            helper.assertTrue(!Config.validRegex("["), "Invalid regex rejected");
            Config.WHITELIST.set(List.of());
            helper.assertTrue(Config.allowed("other:desert_pyramid"), "Empty whitelist allows all");
        } finally {
            Config.WHITELIST.set(List.of()); Config.BLACKLIST.set(List.of());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1800, batch = "restoration")
    public static void restoresPyramid(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(64, 0, 64));
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("desert_pyramid"));
        var piece = new DesertPyramidPiece(RandomSource.create(42), origin.getX(), origin.getZ());
        ChunkPos startPos = new ChunkPos(origin);
        var start = new StructureStart(structure, startPos, 0, new PiecesContainer(List.of(piece)));
        var broadBox = new BoundingBox(origin.getX() - 32, level.getMinBuildHeight(), origin.getZ() - 32,
                origin.getX() + 48, level.getMaxBuildHeight() - 1, origin.getZ() + 48);
        // The GameTest superflat floor is near minY; raise terrain so the underground treasure room fits.
        for (BlockPos ground : BlockPos.betweenClosed(broadBox.minX(), 64, broadBox.minZ(), broadBox.maxX(), 64, broadBox.maxZ())) {
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        }
        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(42), broadBox, startPos);
        var box = piece.getBoundingBox();
        level.getChunk(startPos.x, startPos.z).setStartForStructure(structure, start);
        for (ChunkPos pos : ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList()) {
            level.getChunk(pos.x, pos.z).addReferenceForStructure(structure, startPos.toLong());
        }
        java.util.List<BlockPos> chests = new java.util.ArrayList<>();
        for (ChunkPos pos : ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList()) {
            for (var entry : level.getChunk(pos.x, pos.z).getBlockEntities().entrySet()) {
                if (entry.getValue() instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                    chests.add(entry.getKey().immutable());
                    chest.setLootTable(null); chest.clearContent();
                    chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                }
            }
        }
        helper.assertTrue(chests.size() == 4, "Test pyramid has four treasure chests");
        BlockPos damaged = BlockPos.betweenClosedStream(box).filter(p -> level.getBlockState(p).is(Blocks.SANDSTONE)).findFirst().orElseThrow().immutable();
        level.setBlockAndUpdate(damaged, Blocks.AIR.defaultBlockState());
        var pinned = ChunkPos.rangeClosed(new ChunkPos((box.minX() - 16) >> 4, (box.minZ() - 16) >> 4),
                new ChunkPos((box.maxX() + 16) >> 4, (box.maxZ() + 16) >> 4)).toList();
        pinned.forEach(pos -> level.setChunkForced(pos.x, pos.z, true));
        var player = helper.makeMockServerPlayerInLevel();
        player.setNoGravity(true);
        player.teleportTo(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ());
        RewindManager.begin(player);
        helper.startSequence().thenIdle(90)
                .thenExecute(() -> helper.assertTrue(level.getBlockState(damaged).isAir(), "Restoration must wait for animation"))
                .thenWaitUntil(() -> helper.assertTrue(!RewindManager.isRewinding(), "Waiting for loaded chunks and first rewind"))
                .thenExecute(() -> {
            helper.assertTrue(level.getBlockState(damaged).is(Blocks.SANDSTONE), "Destroyed sandstone restored");
            helper.assertTrue(player.getCooldowns().isOnCooldown(StructureRewindWatchMod.WATCH.get()), "Cooldown starts after completion");
            for (BlockPos pos : chests) {
                var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos);
                helper.assertTrue(chest != null && chest.getLootTable() != null, "Loot tables restored, including underground chests");
            }
            level.setBlockAndUpdate(damaged, Blocks.AIR.defaultBlockState());
            RewindManager.begin(player); // Must be rejected by the active vanilla cooldown.
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(level.getBlockState(damaged).isAir(), "Cooldown blocks repeated activation");
            player.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 2, false));
            helper.assertTrue(player.hasPermissions(2), "Mock player has OP permission");
            player.teleportTo(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ());
            RewindManager.begin(player);
        }).thenWaitUntil(() -> helper.assertTrue(!RewindManager.isRewinding(), "Waiting for second rewind"))
        .thenExecute(() -> {
            helper.assertTrue(level.getBlockState(damaged).is(Blocks.SANDSTONE), "OP can rewind again during cooldown");
            helper.assertTrue(!player.getCooldowns().isOnCooldown(StructureRewindWatchMod.WATCH.get()), "OP has no cooldown");
            level.setBlockAndUpdate(damaged, Blocks.AIR.defaultBlockState());
            player.teleportTo(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ());
            BlockPos blocker = player.blockPosition().above(3);
            level.setBlockAndUpdate(blocker, StructureRewindWatchMod.INHIBITOR.get().defaultBlockState());
            var inhibitor = (com.p1nero.structure_rewind_watch.inhibitor.RewindInhibitorBlockEntity) level.getBlockEntity(blocker);
            inhibitor.setOwner(player.getUUID()); inhibitor.announce();
            RewindManager.begin(player);
            helper.assertTrue(!RewindManager.isRewinding(), "Announced inhibitor blocks activation even for OP");
            level.setBlockAndUpdate(blocker, Blocks.AIR.defaultBlockState());
            RewindManager.begin(player);
            helper.assertTrue(RewindManager.isRewinding(), "Removal permits activation");
            level.setBlockAndUpdate(blocker, StructureRewindWatchMod.INHIBITOR.get().defaultBlockState());
            ((com.p1nero.structure_rewind_watch.inhibitor.RewindInhibitorBlockEntity) level.getBlockEntity(blocker)).announce();
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(!RewindManager.isRewinding(), "New inhibitor cancels active windup");
            helper.assertTrue(level.getBlockState(damaged).isAir(), "Canceled windup never restores structure");
            level.setBlockAndUpdate(player.blockPosition().above(3), Blocks.AIR.defaultBlockState());
            player.getServer().getPlayerList().deop(player.getGameProfile());
            player.getServer().getPlayerList().remove(player);
            pinned.forEach(pos -> level.setChunkForced(pos.x, pos.z, false));
        }).thenSucceed();
    }
}
