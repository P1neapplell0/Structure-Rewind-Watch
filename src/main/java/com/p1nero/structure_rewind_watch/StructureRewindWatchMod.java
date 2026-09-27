package com.p1nero.structure_rewind_watch;

import net.minecraft.world.item.CreativeModeTabs;
import com.p1nero.structure_rewind_watch.inhibitor.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(StructureRewindWatchMod.MODID)
public class StructureRewindWatchMod {
    public static final String MODID = "structure_rewind_watch";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<RewindWatchItem> WATCH = ITEMS.register("structure_rewind_watch",
            () -> new RewindWatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredBlock<RewindInhibitorBlock> INHIBITOR = BLOCKS.register("rewind_inhibitor",
            () -> new RewindInhibitorBlock(BlockBehaviour.Properties.of().strength(3.0f, 3600000.0f)
                    .noOcclusion().sound(SoundType.METAL).lightLevel(state -> 10).pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<RewindInhibitorItem> INHIBITOR_ITEM = ITEMS.register("rewind_inhibitor",
            () -> new RewindInhibitorItem(INHIBITOR.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RewindInhibitorBlockEntity>> INHIBITOR_ENTITY =
            BLOCK_ENTITIES.register("rewind_inhibitor", () -> BlockEntityType.Builder.of(RewindInhibitorBlockEntity::new, INHIBITOR.get()).build(null));

    public StructureRewindWatchMod(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        bus.addListener(RewindPayload::register);
        bus.addListener(this::creative);
        NeoForge.EVENT_BUS.register(RewindManager.class);
        NeoForge.EVENT_BUS.register(InhibitorProtection.class);
    }

    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WATCH);
            event.accept(INHIBITOR_ITEM);
        }
    }
}
