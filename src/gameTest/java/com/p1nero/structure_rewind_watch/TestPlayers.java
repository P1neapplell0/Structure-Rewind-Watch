package com.p1nero.structure_rewind_watch;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Forge 1.20.1's vanilla helper omits the channel required by Forge's login hooks. */
final class TestPlayers {
    static ServerPlayer create(ServerLevel level) {
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "rewind-test"));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        return player;
    }
}
