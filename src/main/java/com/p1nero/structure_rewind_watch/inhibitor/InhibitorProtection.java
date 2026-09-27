package com.p1nero.structure_rewind_watch.inhibitor;

import com.p1nero.structure_rewind_watch.Config;
import com.p1nero.structure_rewind_watch.StructureRewindWatchMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Only positions announced by ticking block entities are queried; no world/block searches. */
public final class InhibitorProtection {
    private static final Map<UUID, PlayerNotices> NOTICES = new HashMap<>();
    private static final int LEASE_TICKS = 40;

    static void notice(ServerPlayer player, BlockPos pos) {
        long now = player.getServer().overworld().getGameTime();
        var notices = NOTICES.computeIfAbsent(player.getUUID(), key -> new PlayerNotices());
        // A dense cluster must not repeatedly prune the same player's cache once per block.
        if (now >= notices.nextPrune) {
            notices.sources.entrySet().removeIf(entry -> entry.getValue() < now || !entry.getKey().dimension().equals(player.level().dimension()));
            notices.nextPrune = now + LEASE_TICKS;
        }
        notices.sources.put(GlobalPos.of(player.level().dimension(), pos.immutable()), now + LEASE_TICKS);
    }

    public static boolean isBlocked(ServerPlayer player) {
        var notices = NOTICES.get(player.getUUID());
        if (notices == null) return false;
        var sources = notices.sources;
        long now = player.getServer().overworld().getGameTime();
        var level = player.serverLevel();
        double radiusSquared = (double) Config.INHIBITOR_RADIUS.get() * Config.INHIBITOR_RADIUS.get();
        boolean blocked = false;
        for (var iterator = sources.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var source = entry.getKey();
            BlockPos pos = source.pos();
            if (entry.getValue() < now || !source.dimension().equals(level.dimension())
                    || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    || !(level.getBlockEntity(pos) instanceof RewindInhibitorBlockEntity inhibitor) || inhibitor.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= radiusSquared) blocked = true;
        }
        if (sources.isEmpty()) NOTICES.remove(player.getUUID());
        return blocked;
    }

    private static final class PlayerNotices {
        final Map<GlobalPos, Long> sources = new HashMap<>();
        long nextPrune;
    }

    public static boolean mayBreak(Player player, LevelAccessor level, BlockPos pos) {
        return player.hasPermissions(2) || level.getBlockEntity(pos) instanceof RewindInhibitorBlockEntity inhibitor
                && player.getUUID().equals(inhibitor.getOwner());
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getState().is(StructureRewindWatchMod.INHIBITOR.get()) && !mayBreak(event.getPlayer(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.structure_rewind_watch.inhibitor_owner_only"), true);
        }
    }
    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        // Filter only blocks already selected by the explosion; never scan an area.
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getBlockState(pos).is(StructureRewindWatchMod.INHIBITOR.get()));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { NOTICES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { NOTICES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { NOTICES.clear(); }
}
