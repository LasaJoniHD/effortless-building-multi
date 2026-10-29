package nl.requios.effortlessbuilding.mixin;

import net.fabricmc.fabric.impl.recipe.sync.ClientboundRecipeSyncPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import nl.requios.effortlessbuilding.backend.FabricContent;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class OptionalRecipeSyncMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> effortlessbuilding$filterRecipeBook(Packet<?> packet) {
        if ((Object) this instanceof ServerGamePacketListenerImpl handler
                && !FabricContent.canSyncTo(handler.player)) {
            if (packet instanceof ClientboundRecipeBookAddPacket recipes) {
                return new ClientboundRecipeBookAddPacket(recipes.entries().stream().filter(entry ->
                        !effortlessbuilding$isHidden(entry.contents().display())).toList(), recipes.replace());
            }
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof ClientboundRecipeSyncPayload recipes) {
                // Recipe results use a non-empty ItemStackTemplate codec. Hiding their result
                // as AIR/EMPTY would fail encoding, so omit the whole recipe for this player.
                // Never mutate Fabric's shared recipe lists: companion clients still need them.
                return new ClientboundCustomPayloadPacket(new ClientboundRecipeSyncPayload(
                        recipes.entries().stream().map(entry -> new ClientboundRecipeSyncPayload.Entry(
                                entry.serializer(), entry.recipes().stream().filter(recipe ->
                                        recipe.value().display().stream().noneMatch(OptionalRecipeSyncMixin::effortlessbuilding$isHidden))
                                        .toList())).filter(entry -> !entry.recipes().isEmpty()).toList()));
            }
        }
        return packet;
    }

    private static boolean effortlessbuilding$isHidden(RecipeDisplay recipe) {
        return recipe.result() instanceof SlotDisplay.ItemStackSlotDisplay display
                && display.stack().item().value() instanceof RandomizerToolItem;
    }
}
