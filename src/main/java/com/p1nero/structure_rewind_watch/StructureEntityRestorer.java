package com.p1nero.structure_rewind_watch;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.*;

/** Per-rewind entity slots, independent of unrelated creatures of the same type nearby. */
final class StructureEntityRestorer {
    private static final String SOURCE = StructureRewindWatchMod.MODID + ":source";
    private static final String SLOT = StructureRewindWatchMod.MODID + ":slot";
    private final ServerLevel level;
    private final AABB area;
    private final String source;
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final List<Replacement> replacements = new ArrayList<>();
    private final Set<UUID> handled = new HashSet<>();

    StructureEntityRestorer(ServerLevel level, StructureStart start) {
        this.level = level;
        this.area = AABB.of(start.getBoundingBox()).inflate(16);
        this.source = level.dimension().location() + "/" + level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure())
                + "@" + start.getChunkPos().toLong();
    }

    void onSpawn(EntityJoinLevelEvent event) {
        Entity incoming = event.getEntity();
        if (protectedEntity(incoming)) return;
        String position = incoming.getType().builtInRegistryHolder().key().location() + "/"
                + Math.round(incoming.getX() * 16) + "/" + Math.round(incoming.getY() * 16) + "/" + Math.round(incoming.getZ() * 16);
        String slot = position + "/" + (occurrences.merge(position, 1, Integer::sum) - 1);
        var nearby = level.getEntitiesOfClass(Entity.class, area,
                entity -> entity != incoming && entity.isAlive() && entity.getType() == incoming.getType() && !handled.contains(entity.getUUID()));
        Entity match = nearby.stream().filter(entity -> source.equals(entity.getPersistentData().getString(SOURCE))
                && slot.equals(entity.getPersistentData().getString(SLOT))).findFirst().orElse(null);
        if (match == null) {
            // Legacy entities have no origin tag. Match only the actual spawn point and name,
            // never consume a whole structure-wide count of this entity type.
            match = nearby.stream().filter(entity -> !entity.getPersistentData().contains(SOURCE)
                            && entity.distanceToSqr(incoming) <= 0.75 * 0.75
                            && Objects.equals(entity.getCustomName(), incoming.getCustomName()))
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(incoming))).orElse(null);
        }
        if (match != null) {
            handled.add(match.getUUID());
            if (protectedEntity(match) || !Config.RESET_EXISTING_ENTITIES.get()) {
                // Keep pets/occupied mounts, and avoid creating a duplicate on top of them.
                if (!protectedEntity(match)) tag(match, slot);
                event.setCanceled(true);
                return;
            }
            // Only remove the old entity after the new one was actually accepted by the server.
            replacements.add(new Replacement(match, incoming));
        }
        tag(incoming, slot);
        handled.add(incoming.getUUID());
    }

    void commitReplacements() {
        for (Replacement replacement : replacements) {
            if (level.getEntity(replacement.incoming.getUUID()) == replacement.incoming && !replacement.incoming.isRemoved())
                replacement.previous.discard(); // No death loot, experience, or item-frame contents dropped.
        }
        replacements.clear();
    }

    private record Replacement(Entity previous, Entity incoming) {}

    private void tag(Entity entity, String slot) {
        entity.getPersistentData().putString(SOURCE, source);
        entity.getPersistentData().putString(SLOT, slot);
    }

    private static boolean protectedEntity(Entity entity) {
        return entity instanceof Player || entity.isVehicle() || entity.isPassenger()
                || entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null
                || entity instanceof TamableAnimal animal && animal.isTame()
                || entity instanceof AbstractHorse horse && horse.isTamed();
    }
}
