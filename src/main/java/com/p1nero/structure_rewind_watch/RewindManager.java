package com.p1nero.structure_rewind_watch;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class RewindManager {
    public static final int WINDUP_TICKS = 100;
    private static final TicketType<ChunkPos> ENTITY_REWIND_TICKET = TicketType.create(
            "structure_rewind_watch_entities", Comparator.comparingLong(ChunkPos::toLong));
    private static Job job;
    private static boolean placing;

    static boolean isRewinding() { return job != null; }

    public static boolean isPlacing() {
        return placing && job != null && job.level.getServer().isSameThread();
    }

    /** Returns true only when this rewind owns the postprocessing request. */
    public static boolean queuePostprocessing(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockPos pos) {
        if (!isPlacing() || !(chunk instanceof net.minecraft.world.level.chunk.LevelChunk loaded)
                || loaded.getLevel() != job.level) return false;
        if (!chunk.isOutsideBuildHeight(pos)) {
            var entries = net.minecraft.world.level.chunk.ChunkAccess.getOrCreateOffsetList(
                    chunk.getPostProcessing(), chunk.getSectionIndex(pos.getY()));
            short packed = net.minecraft.world.level.chunk.ProtoChunk.packOffsetCoordinates(pos);
            if (!entries.contains(packed)) entries.add(packed);
        }
        return true;
    }

    public static BlockPos originalAnchor(TemplateStructurePiece piece) {
        return isPlacing() ? job.anchors.get(piece) : null;
    }

    public static void begin(ServerPlayer player) {
        if (com.p1nero.structure_rewind_watch.inhibitor.InhibitorProtection.isBlocked(player)) {
            message(player, "inhibited");
            return;
        }
        if (job != null) { message(player, "busy"); return; }
        if (!player.hasPermissions(2) && player.getCooldowns().isOnCooldown(StructureRewindWatchMod.WATCH.get())) return;
        ServerLevel level = player.serverLevel();
        int radius = Config.SEARCH_RADIUS.get();
        BlockPos origin = player.blockPosition();
        Set<StructureStart> candidates = new HashSet<>();
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                // Do not generate unexplored terrain merely to search for a target.
                if (level.hasChunk(x, z)) candidates.addAll(level.structureManager().startsForStructure(new ChunkPos(x, z), s -> true));
            }
        }
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var target = candidates.stream().filter(StructureStart::isValid)
                .filter(s -> distanceSquared(origin, s.getBoundingBox()) <= (double) radius * radius)
                .filter(s -> Config.allowed(registry.getKey(s.getStructure()).toString()))
                .min(Comparator.<StructureStart>comparingDouble(s -> distanceSquared(origin, s.getBoundingBox()))
                        .thenComparingLong(s -> s.getChunkPos().toLong())).orElse(null);
        if (target == null) { message(player, "no_structure"); return; }
        try {
            // Clone the saved layout: no changed terrain heights or newly rolled jigsaw layouts.
            var context = StructurePieceSerializationContext.fromLevel(level);
            CompoundTag tag = target.createTag(context, target.getChunkPos());
            for (Tag child : tag.getList("Children", Tag.TAG_COMPOUND)) {
                CompoundTag piece = (CompoundTag) child;
                String id = piece.getString("id");
                if (!id.startsWith("minecraft:")) continue;
                for (String key : List.of("hasPlacedChest0", "hasPlacedChest1", "hasPlacedChest2", "hasPlacedChest3",
                        "placedMainChest", "placedHiddenChest", "placedTrap1", "placedTrap2", "Mob", "hps", "Witch", "Cat")) {
                    if (piece.contains(key)) piece.putBoolean(key, false);
                }
                if (id.equals("minecraft:shcc")) piece.putBoolean("Chest", false);
            }
            StructureStart copy = StructureStart.loadStaticStart(context, tag, level.getSeed());
            if (copy == null || !copy.isValid()) throw new IllegalStateException("Cannot deserialize structure");
            BoundingBox box = copy.getBoundingBox();
            long count = (long) ((box.maxX() >> 4) - (box.minX() >> 4) + 1) * ((box.maxZ() >> 4) - (box.minZ() >> 4) + 1);
            if (count > Config.MAX_CHUNKS.get()) { message(player, "too_large"); return; }
            job = new Job(level, copy, player.getUUID(), level.getGameTime());
            for (ServerPlayer viewer : level.players()) {
                if (viewer == player || distanceSquared(viewer.blockPosition(), box) <= Math.pow(Config.EFFECT_RADIUS.get(), 2)) {
                    job.viewers.add(viewer.getUUID());
                    RewindPayload.send(viewer, new RewindPayload(0, job.sequence));
                }
            }
            message(player, "started");
        } catch (Exception e) {
            LogUtils.getLogger().error("Unable to prepare structure rewind", e);
            message(player, "failed");
        }
    }

    public static double distanceSquared(BlockPos pos, BoundingBox box) {
        double x = Math.max(Math.max(box.minX() - pos.getX(), 0), pos.getX() - box.maxX());
        double y = Math.max(Math.max(box.minY() - pos.getY(), 0), pos.getY() - box.maxY());
        double z = Math.max(Math.max(box.minZ() - pos.getZ(), 0), pos.getZ() - box.maxZ());
        return x * x + y * y + z * z;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Vanilla checks cooldown before calling Item.use, so clear stale cooldowns after an OP promotion.
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (player.hasPermissions(2) && player.getCooldowns().isOnCooldown(StructureRewindWatchMod.WATCH.get()))
                player.getCooldowns().removeCooldown(StructureRewindWatchMod.WATCH.get());
        }
        if (job == null) return;
        Job active = job;
        // Recheck throughout windup and placement, so entering a protected area cancels further writes.
        ServerPlayer owner = active.level.getServer().getPlayerList().getPlayer(active.owner);
        if (owner != null && com.p1nero.structure_rewind_watch.inhibitor.InhibitorProtection.isBlocked(owner)) {
            finish(active, false);
            message(owner, "inhibited");
            return;
        }
        try {
            // Block chunks alone do not imply that their saved entities have finished loading.
            // Keep the whole target resident until the entity comparison and placement finish.
            for (int i = 0; i < Config.CHUNKS_PER_TICK.get() && active.requested < active.positions.size(); i++) {
                ChunkPos pos = active.positions.get(active.requested++);
                active.level.getChunkSource().addRegionTicket(ENTITY_REWIND_TICKET, pos, 2, pos);
                active.level.getChunk(pos.x, pos.z);
            }
            if (active.level.getGameTime() - active.started > 2400) throw new IllegalStateException("Structure rewind timed out");
            if (active.level.getGameTime() - active.started < WINDUP_TICKS || active.requested < active.positions.size()) return;
            if (active.positions.stream().anyMatch(pos -> !active.level.areEntitiesLoaded(pos.toLong()))) return;
            for (int i = 0; i < Config.CHUNKS_PER_TICK.get() && active.chunks.hasNext(); i++) placeChunk(active, active.chunks.next());
            if (!active.chunks.hasNext()) finish(active, true);
        } catch (Exception e) {
            LogUtils.getLogger().error("Structure rewind failed; some chunks may already have been restored", e);
            finish(active, false);
        } finally { placing = false; }
    }

    private static void placeChunk(Job active, ChunkPos pos) {
        ServerLevel level = active.level;
        var chunk = level.getChunk(pos.x, pos.z);
        placing = true;
        try {
            active.start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                    RandomSource.create(level.getSeed() ^ pos.toLong()),
                    new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                            pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ()), pos);
            chunk.postProcessGeneration();
        } finally {
            active.entities.commitReplacements();
            placing = false;
        }
        chunk.setUnsaved(true);
        var packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        for (ServerPlayer viewer : level.getChunkSource().chunkMap.getPlayers(pos, false)) viewer.connection.send(packet);
        // Move anyone trapped in a newly restored wall to the first clear, supported spot above it.
        for (ServerPlayer player : level.players()) {
            if (player.chunkPosition().equals(pos) && !level.noCollision(player)) rescue(player);
        }
    }

    private static void rescue(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        for (int y = base.getY(); y < level.getMaxBuildHeight() - 2; y++) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(pos).getFluidState().isEmpty()
                    && level.noCollision(player, player.getBoundingBox().move(0, y - player.getY(), 0))) {
                player.teleportTo(player.getX(), y, player.getZ());
                player.fallDistance = 0;
                return;
            }
        }
    }

    private static void finish(Job active, boolean success) {
        for (int i = 0; i < active.requested; i++) {
            ChunkPos pos = active.positions.get(i);
            active.level.getChunkSource().removeRegionTicket(ENTITY_REWIND_TICKET, pos, 2, pos);
        }
        ServerPlayer owner = active.level.getServer().getPlayerList().getPlayer(active.owner);
        if (owner != null) {
            if (success && !owner.hasPermissions(2)) owner.getCooldowns().addCooldown(StructureRewindWatchMod.WATCH.get(), Config.COOLDOWN.get() * 20);
            message(owner, success ? "complete" : "failed");
        }
        for (UUID id : active.viewers) {
            ServerPlayer viewer = active.level.getServer().getPlayerList().getPlayer(id);
            if (viewer != null) RewindPayload.send(viewer, new RewindPayload(success ? 1 : 2, active.sequence));
        }
        job = null;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (!isPlacing() || event.getLevel() != job.level || event.loadedFromDisk()) return;
        job.entities.onSpawn(event);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { job = null; placing = false; }

    private static void message(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable("message.structure_rewind_watch." + suffix), true);
    }

    private static final class Job {
        final ServerLevel level;
        final StructureStart start;
        final UUID owner;
        final long started;
        final long sequence = System.nanoTime();
        final List<ChunkPos> positions;
        final Iterator<ChunkPos> chunks;
        int requested;
        final List<UUID> viewers = new ArrayList<>();
        final Map<TemplateStructurePiece, BlockPos> anchors = new IdentityHashMap<>();
        final StructureEntityRestorer entities;
        Job(ServerLevel level, StructureStart start, UUID owner, long started) {
            this.level = level; this.start = start; this.owner = owner; this.started = started;
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof TemplateStructurePiece template) anchors.put(template, template.templatePosition().immutable());
            }
            entities = new StructureEntityRestorer(level, start);
            var box = start.getBoundingBox();
            positions = ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                    new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList();
            chunks = positions.iterator();
        }
    }
}
