package nl.requios.effortlessbuilding.server;

import nl.requios.effortlessbuilding.backend.FabricBackend;
import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;
import nl.requios.effortlessbuilding.network.SyncServerConfigS2CPacket;
import nl.requios.effortlessbuilding.network.QueryAE2CountC2SPacket;
import nl.requios.effortlessbuilding.network.SyncAE2CountS2CPacket;
import nl.requios.effortlessbuilding.network.BuildModeHintC2SPacket;
import nl.requios.effortlessbuilding.platform.services.INetworkHelper;

public class ServerNetworkHelper implements INetworkHelper {

    @Override
    public void sendToServer(PlaceBuildModePacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToServer(BreakBuildModePacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToServer(UndoPacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToServer(RedoPacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToServer(UpdateModifiersC2SPacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet) {
        FabricBackend.send(player, packet);
    }

    @Override
    public void sendToServer(UpdateServerConfigC2SPacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncServerConfigS2CPacket packet) {
        FabricBackend.send(player, packet);
    }

    @Override
    public void sendToServer(QueryAE2CountC2SPacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToServer(BuildModeHintC2SPacket packet) {
        throw new UnsupportedOperationException("Server backend has no client transport");
    }

    @Override
    public void sendToClient(ServerPlayer player, SyncAE2CountS2CPacket packet) {
        FabricBackend.send(player, packet);
    }

}
