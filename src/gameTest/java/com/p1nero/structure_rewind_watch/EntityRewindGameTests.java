package com.p1nero.structure_rewind_watch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(StructureRewindWatchMod.MODID)
@PrefixGameTestTemplate(false)
public class EntityRewindGameTests {
    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_safety")
    public static void protectsPetsAndRejectedSpawns(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        ChunkPos chunk = new ChunkPos(pos);
        level.setChunkForced(chunk.x, chunk.z, true);
        helper.runAfterDelay(20, () -> {
            var type = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("end_city"));
            var piece = new net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece(RandomSource.create(0), pos.getX(), pos.getZ());
            piece.move(0, pos.getY() - 64, 0);
            var start = new StructureStart(type, chunk, 0, new PiecesContainer(List.of(piece)));
            var pet = EntityType.WOLF.create(level);
            pet.setPos(pos.getX(), pos.getY(), pos.getZ());
            pet.setNoAi(true); pet.setNoGravity(true);
            pet.setOwnerUUID(java.util.UUID.randomUUID());
            level.addFreshEntity(pet);
            var replacementPet = EntityType.WOLF.create(level);
            replacementPet.setPos(pet.position());
            var petEvent = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(replacementPet, level);
            var restorer = new StructureEntityRestorer(level, start);
            restorer.onSpawn(petEvent);
            helper.assertTrue(petEvent.isCanceled() && !pet.isRemoved(), "Owned pet is preserved without duplication");

            var old = new ArmorStand(level, pos.getX() + 1, pos.getY(), pos.getZ() + 1);
            old.setNoGravity(true); level.addFreshEntity(old);
            var incoming = new ArmorStand(level, old.getX(), old.getY(), old.getZ());
            var event = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(incoming, level);
            restorer.onSpawn(event);
            restorer.commitReplacements(); // Simulate a later mod rejecting the new spawn.
            helper.assertTrue(!old.isRemoved(), "Rejected replacement must not delete existing entity");
            try {
                Config.RESET_EXISTING_ENTITIES.set(false);
                var keep = new StructureEntityRestorer(level, start);
                var keepEvent = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(new ArmorStand(level, old.getX(), old.getY(), old.getZ()), level);
                keep.onSpawn(keepEvent);
                helper.assertTrue(keepEvent.isCanceled() && !old.isRemoved(), "Missing-only mode preserves existing entities");
            } finally { Config.RESET_EXISTING_ENTITIES.set(true); }
            pet.discard(); old.discard();
            level.setChunkForced(chunk.x, chunk.z, false);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1800, batch = "entity_restoration")
    public static void restoresTemplateEntities(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var manager = level.getStructureManager();
        var template = manager.getOrCreate(ResourceLocation.withDefaultNamespace("end_city/rewind_entity_test"));
        template.load(level.holderLookup(Registries.BLOCK), TagParser.parseTag("""
            {size:[5,4,5],palette:[{Name:"minecraft:stone"}],blocks:[{pos:[0,0,0],state:0}],entities:[
              {pos:[1.5d,1.0d,1.5d],blockPos:[1,1,1],nbt:{id:"minecraft:armor_stand",NoGravity:1b,Marker:1b}},
              {pos:[3.5d,1.0d,3.5d],blockPos:[3,1,3],nbt:{id:"minecraft:armor_stand",NoGravity:1b,Marker:1b}}
            ]}
            """));
        BlockPos base = helper.absolutePos(new BlockPos(256, 0, 256));
        BlockPos origin = new BlockPos(base.getX(), 80, base.getZ());
        var piece = new EndCityPieces.EndCityPiece(manager, "rewind_entity_test", origin, Rotation.NONE, true);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("end_city"));
        var start = new StructureStart(structure, new ChunkPos(origin), 0, new PiecesContainer(List.of(piece)));
        var box = start.getBoundingBox();
        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(0), box, new ChunkPos(origin));
        helper.assertTrue(piece.placeSettings().isIgnoreEntities(), "Normal generation still ignores entities");
        helper.assertTrue(level.getEntitiesOfClass(ArmorStand.class, AABB.of(box)).isEmpty(), "No outside-rewind entity injection");
        var startPos = start.getChunkPos();
        level.getChunk(startPos.x, startPos.z).setStartForStructure(structure, start);
        for (var pos : ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList())
            level.getChunk(pos.x, pos.z).addReferenceForStructure(structure, startPos.toLong());
        var forced = ChunkPos.rangeClosed(new ChunkPos((box.minX() - 16) >> 4, (box.minZ() - 16) >> 4),
                new ChunkPos((box.maxX() + 16) >> 4, (box.maxZ() + 16) >> 4)).toList();
        forced.forEach(pos -> level.setChunkForced(pos.x, pos.z, true));
        var original = new ArmorStand(level, origin.getX() + 1.5, 81, origin.getZ() + 1.5);
        original.setNoGravity(true); original.setHealth(2);
        level.addFreshEntity(original);
        var unrelated = new ArmorStand(level, origin.getX() + 8.5, 81, origin.getZ() + 1.5);
        unrelated.setNoGravity(true); level.addFreshEntity(unrelated);
        var player = helper.makeMockServerPlayerInLevel();
        player.setNoGravity(true);
        player.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 2, false));
        player.teleportTo(origin.getX() + 2, 82, origin.getZ() + 2);
        RewindManager.begin(player);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!RewindManager.isRewinding(), "Waiting for first entity rewind"))
        .thenExecute(() -> {
            var restored = level.getEntitiesOfClass(ArmorStand.class, AABB.of(box));
            helper.assertTrue(restored.size() == 2, "Restore both saved entities despite unrelated same-type neighbor; found=" + restored.size() + ", originalRemoved=" + original.getRemovalReason() + ", unrelatedRemoved=" + unrelated.getRemovalReason() + ", nearby=" + level.getEntitiesOfClass(ArmorStand.class, AABB.of(box).inflate(16)).stream().map(e -> e.position().toString()).toList());
            helper.assertTrue(original.isRemoved(), "Existing damaged structure entity replaced");
            helper.assertTrue(restored.stream().allMatch(e -> e.getHealth() == e.getMaxHealth()), "Original entity state restored");
            helper.assertTrue(!unrelated.isRemoved(), "Unrelated nearby entity preserved");
            restored.get(0).setPos(origin.getX() + 6.5, 81, origin.getZ() + 6.5); // Origin tag follows it.
            restored.get(0).setHealth(1);
            restored.get(1).discard(); // Missing entity must return.
            RewindManager.begin(player);
        }).thenWaitUntil(() -> helper.assertTrue(!RewindManager.isRewinding(), "Waiting for second entity rewind"))
        .thenExecute(() -> {
            var restored = level.getEntitiesOfClass(ArmorStand.class, AABB.of(box).inflate(8));
            helper.assertTrue(restored.size() == 3, "Repeated rewind restores missing/moved entities without duplicates");
            helper.assertTrue(level.getEntitiesOfClass(ArmorStand.class, AABB.of(box)).size() == 2, "Both entities return to their original slots");
            helper.assertTrue(!unrelated.isRemoved(), "Unrelated neighbor still intact");
            helper.assertTrue(piece.placeSettings().isIgnoreEntities(), "Shared template settings not mutated");
            player.getServer().getPlayerList().deop(player.getGameProfile());
            player.getServer().getPlayerList().remove(player);
            forced.forEach(pos -> level.setChunkForced(pos.x, pos.z, false));
        }).thenSucceed();
    }
}
