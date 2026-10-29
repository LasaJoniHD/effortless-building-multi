package nl.requios.effortlessbuilding.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.impl.networking.ChannelInfoHolder;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.SyncServerConfigS2CPacket;
import nl.requios.effortlessbuilding.network.SyncAE2CountS2CPacket;
import nl.requios.effortlessbuilding.server.DeferredRegistrySync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashMap;
import java.util.Map;

/** Supplies normal Fabric ID remapping to upstream clients without requiring Polymer on them. */
@Mixin(value = RegistrySyncManager.class, remap = false)
public abstract class ClientRegistrySyncMixin {
    @WrapMethod(method = "configureClient")
    private static void effortlessbuilding$deferUntilChannelsKnown(ServerConfigurationPacketListenerImpl handler,
            MinecraftServer server, Operation<Void> original) {
        DeferredRegistrySync.defer(handler, () -> original.call(handler, server));
    }

    @ModifyExpressionValue(method = "configureClient", at = @At(value = "INVOKE",
            target = "Lnet/fabricmc/fabric/impl/registry/sync/RegistrySyncManager;createAndPopulateRegistryMap()Ljava/util/Map;"))
    private static Map<Identifier, Object2IntMap<Identifier>> effortlessbuilding$mapUpstreamClient(
            Map<Identifier, Object2IntMap<Identifier>> original,
            ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        var connection = ((ServerConnectionAccessor) handler).effortlessbuilding$getConnection();
        var channels = ((ChannelInfoHolder) connection).fabric_getPendingChannelsNames(ConnectionProtocol.PLAY);
        if (!channels.contains(SyncModifiersS2CPacket.TYPE.id())
                || !channels.contains(SyncServerConfigS2CPacket.TYPE.id())
                || !channels.contains(SyncAE2CountS2CPacket.TYPE.id())) return original;
        Map<Identifier, Object2IntMap<Identifier>> result = original == null ? new LinkedHashMap<>() : new LinkedHashMap<>(original);
        addRegistry(result, BuiltInRegistries.ITEM);
        addRegistry(result, BuiltInRegistries.MENU);
        return result;
    }

    private static <T> void addRegistry(Map<Identifier, Object2IntMap<Identifier>> map, Registry<T> registry) {
        Object2IntMap<Identifier> entries = new Object2IntLinkedOpenHashMap<>();
        for (T entry : registry) {
            Identifier id = registry.getKey(entry);
            if (!RegistrySyncUtils.isServerEntry(registry, entry) || id.getNamespace().equals("effortlessbuilding")) {
                entries.put(id, registry.getId(entry));
            }
        }
        map.put(registry.key().identifier(), entries);
    }
}
