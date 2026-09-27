package com.p1nero.structure_rewind_watch;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public record RewindPayload(int phase, long sequence) {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StructureRewindWatchMod.MODID, "rewind"), () -> "1", "1"::equals, "1"::equals);
    public static java.util.function.Consumer<RewindPayload> receiver = payload -> {};
    public static void register() {
        CHANNEL.messageBuilder(RewindPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buf) -> { buf.writeVarInt(payload.phase); buf.writeLong(payload.sequence); })
                .decoder(buf -> new RewindPayload(buf.readVarInt(), buf.readLong()))
                .consumerMainThread((payload, context) -> receiver.accept(payload))
                .add();
    }
    public static void send(ServerPlayer player, RewindPayload payload) {
        if (CHANNEL.isRemotePresent(player.connection.connection)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
