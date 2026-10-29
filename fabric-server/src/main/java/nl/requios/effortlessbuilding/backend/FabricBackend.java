package nl.requios.effortlessbuilding.backend;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.network.*;

import java.util.function.BiConsumer;

/** Fabric transport adapter for the shared backend. No login task or required handshake. */
public final class FabricBackend {
    private FabricBackend() {}

    public static void initialize() {
        nl.requios.effortlessbuilding.server.DeferredRegistrySync.initialize();
        // Offer shaped recipes (including the randomizer) to recipe viewers that request
        // Fabric's optional full sync. Vanilla clients never request or receive this payload.
        RecipeSynchronization.synchronizeRecipeSerializer(BuiltInRegistries.RECIPE_SERIALIZER.getValue(
                Identifier.parse("minecraft:crafting_shaped")));
        PayloadTypeRegistry.serverboundPlay().register(PlaceBuildModePacket.TYPE, PlaceBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BreakBuildModePacket.TYPE, BreakBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UndoPacket.TYPE, UndoPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RedoPacket.TYPE, RedoPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateModifiersC2SPacket.TYPE, UpdateModifiersC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateServerConfigC2SPacket.TYPE, UpdateServerConfigC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QueryAE2CountC2SPacket.TYPE, QueryAE2CountC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuildModeHintC2SPacket.TYPE, BuildModeHintC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncModifiersS2CPacket.TYPE, SyncModifiersS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncServerConfigS2CPacket.TYPE, SyncServerConfigS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncAE2CountS2CPacket.TYPE, SyncAE2CountS2CPacket.STREAM_CODEC);

        receive(PlaceBuildModePacket.TYPE, PacketHandler::handlePlaceBuildMode);
        receive(BreakBuildModePacket.TYPE, PacketHandler::handleBreakBuildMode);
        receive(UndoPacket.TYPE, (packet, player) -> PacketHandler.handleUndo(player));
        receive(RedoPacket.TYPE, (packet, player) -> PacketHandler.handleRedo(player));
        receive(UpdateModifiersC2SPacket.TYPE, PacketHandler::handleUpdateModifiers);
        receive(UpdateServerConfigC2SPacket.TYPE, PacketHandler::handleUpdateServerConfig);
        receive(QueryAE2CountC2SPacket.TYPE, PacketHandler::handleQueryAE2Count);
        receive(BuildModeHintC2SPacket.TYPE, (packet, player) -> PacketHandler.handleBuildModeHint(player));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> enableIfSupported(handler.getPlayer()));
        ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) ->
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(handler.getPlayer().getUUID()) == handler.getPlayer()) {
                        enableIfSupported(handler.getPlayer());
                    }
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BackendLifecycle.disconnected(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(BackendLifecycle::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(BackendLifecycle::stopped);
    }

    private static void enableIfSupported(ServerPlayer player) {
        if (hasClientChannels(player) && BackendSession.connect(player.getUUID())) {
            BackendLifecycle.ready(player);
        }
    }

    /** Also available during initial data sync, before the JOIN event enables gameplay. */
    public static boolean hasClientChannels(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, SyncModifiersS2CPacket.TYPE)
                && ServerPlayNetworking.canSend(player, SyncServerConfigS2CPacket.TYPE)
                && ServerPlayNetworking.canSend(player, SyncAE2CountS2CPacket.TYPE);
    }

    private static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                               BiConsumer<T, ServerPlayer> handler) {
        // Fabric invokes play handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) -> {
            ServerPlayer player = context.player();
            if (BackendSession.acceptRequest(player.getUUID(), context.server().getTickCount())) {
                handler.accept(packet, player);
            }
        });
    }

    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        if (BackendSession.supports(player.getUUID()) && ServerPlayNetworking.canSend(player, packet.type())) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
