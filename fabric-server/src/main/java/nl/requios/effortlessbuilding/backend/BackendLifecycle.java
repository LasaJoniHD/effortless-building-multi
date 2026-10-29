package nl.requios.effortlessbuilding.backend;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.config.*;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.network.*;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;

/** Shared backend lifecycle; loader modules supply connection and packet transport hooks. */
public final class BackendLifecycle {
    private BackendLifecycle() {}

    public static void started(MinecraftServer server) {
        BackendSession.clear();
        ServerConfigStorage.load(server);
        WelcomeMessageStorage.load(server);
        BuildModeHintStorage.load(server);
    }

    public static void ready(ServerPlayer player) {
        ModifierServerStorage.loadPlayer(player.level().getServer(), player.getUUID());
        PacketHandler.sendToClient(player, new SyncModifiersS2CPacket(
                ModifierServerStorage.serializePlayer(player.getUUID())));
        PacketHandler.sendToClient(player, new SyncServerConfigS2CPacket(ServerConfig.INSTANCE.toJson()));
        WelcomeMessageStorage.showIfNeeded(player);
    }

    public static void disconnected(ServerPlayer player) {
        ModifierServerStorage.savePlayer(player.level().getServer(), player.getUUID());
        ModifierServerStorage.removePlayer(player.getUUID());
        UndoManager.clearPlayer(player.getUUID());
        PlacedBlockTracker.clearPlayer(player.getUUID());
        BackendSession.disconnect(player.getUUID());
    }

    public static void stopped(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) disconnected(player);
        ModifierServerStorage.clearAll();
        BackendSession.clear();
        ServerConfigStorage.clear();
        WelcomeMessageStorage.clear();
        BuildModeHintStorage.clear();
    }
}
