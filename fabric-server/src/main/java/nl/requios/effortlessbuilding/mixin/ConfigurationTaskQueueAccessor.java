package nl.requios.effortlessbuilding.mixin;

import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public interface ConfigurationTaskQueueAccessor {
    @Accessor("configurationTasks") Queue<ConfigurationTask> effortlessbuilding$getConfigurationTasks();
}
