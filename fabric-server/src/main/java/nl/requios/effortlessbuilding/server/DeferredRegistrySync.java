package nl.requios.effortlessbuilding.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import nl.requios.effortlessbuilding.mixin.ConfigurationTaskQueueAccessor;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Moves Fabric registry mapping after its standard channel discovery and before vanilla join tasks. */
public final class DeferredRegistrySync implements ConfigurationTask {
    private static final Type TYPE = new Type("effortlessbuilding:registry_sync");
    private static final Map<ServerConfigurationPacketListenerImpl, Runnable> PENDING = new ConcurrentHashMap<>();
    private final ServerConfigurationPacketListenerImpl listener;
    private final Runnable configure;

    private DeferredRegistrySync(ServerConfigurationPacketListenerImpl listener, Runnable configure) {
        this.listener = listener;
        this.configure = configure;
    }

    public static void initialize() {
        // Registry sync normally runs in BEFORE_CONFIGURE; Fabric's common packets run in CONFIGURE.
        // An explicit event phase also makes this independent of mod initializer ordering.
        var phase = Identifier.parse("effortlessbuilding:after_channel_tasks");
        ServerConfigurationConnectionEvents.CONFIGURE.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
        ServerConfigurationConnectionEvents.CONFIGURE.register(phase, (listener, server) -> {
            Runnable configure = PENDING.remove(listener);
            if (configure != null) listener.addTask(new DeferredRegistrySync(listener, configure));
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> PENDING.remove(listener));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    public static void defer(ServerConfigurationPacketListenerImpl listener, Runnable configure) {
        PENDING.put(listener, configure);
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        // configureClient appends its sync task. Insert that task before the already queued
        // vanilla registry/spawn/join tasks, so its usual Fabric acknowledgement still applies.
        var queue = ((ConfigurationTaskQueueAccessor) listener).effortlessbuilding$getConfigurationTasks();
        var remaining = new ArrayList<>(queue);
        queue.clear();
        try {
            configure.run();
        } finally {
            queue.addAll(remaining);
        }
        listener.completeTask(TYPE);
    }

    @Override public Type type() { return TYPE; }
}
