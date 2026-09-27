package com.p1nero.structure_rewind_watch;

import com.p1nero.structure_rewind_watch.inhibitor.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.*;

@Mod(StructureRewindWatchMod.MODID)
public class StructureRewindWatchMod {
    public static final String MODID = "structure_rewind_watch";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<RewindWatchItem> WATCH = ITEMS.register("structure_rewind_watch",
            () -> new RewindWatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final RegistryObject<RewindInhibitorBlock> INHIBITOR = BLOCKS.register("rewind_inhibitor",
            () -> new RewindInhibitorBlock(BlockBehaviour.Properties.of().strength(3.0f, 3600000.0f)
                    .noOcclusion().sound(SoundType.METAL).lightLevel(state -> 10).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<RewindInhibitorItem> INHIBITOR_ITEM = ITEMS.register("rewind_inhibitor",
            () -> new RewindInhibitorItem(INHIBITOR.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final RegistryObject<BlockEntityType<RewindInhibitorBlockEntity>> INHIBITOR_ENTITY =
            BLOCK_ENTITIES.register("rewind_inhibitor", () -> BlockEntityType.Builder.of(RewindInhibitorBlockEntity::new, INHIBITOR.get()).build(null));

    public StructureRewindWatchMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        RewindPayload.register();
        bus.addListener(this::creative);
        MinecraftForge.EVENT_BUS.register(RewindManager.class);
        MinecraftForge.EVENT_BUS.register(InhibitorProtection.class);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> com.p1nero.structure_rewind_watch.client.RewindClient::register);
    }

    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WATCH); event.accept(INHIBITOR_ITEM);
        }
    }
}
