package com.p1nero.structure_rewind_watch;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record RewindPayload(int phase, long sequence) implements CustomPacketPayload {
    public static final Type<RewindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StructureRewindWatchMod.MODID, "rewind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RewindPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.phase); buf.writeLong(value.sequence); },
            buf -> new RewindPayload(buf.readVarInt(), buf.readLong()));
    // Client installs this callback in its own entry point; dedicated servers never load rendering classes.
    public static java.util.function.Consumer<RewindPayload> receiver = payload -> {};
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> receiver.accept(payload)));
    }
}
