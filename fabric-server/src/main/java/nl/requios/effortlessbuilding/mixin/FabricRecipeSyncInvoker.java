package nl.requios.effortlessbuilding.mixin;

import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses Fabric's negotiated serializers when a companion opts in after initial sync. */
@Mixin(value = RecipeSyncImpl.class, remap = false)
public interface FabricRecipeSyncInvoker {
    @Invoker("sendRecipes")
    static void effortlessbuilding$sendRecipes(ServerPlayer player, boolean existing) {
        throw new AssertionError("Mixin not applied");
    }
}
