package nl.requios.effortlessbuilding.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import nl.requios.effortlessbuilding.backend.FabricContent;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class OptionalContentMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void effortlessbuilding$preventVanillaPickup(Player player, CallbackInfo ci) {
        if (getItem().getItem() instanceof RandomizerToolItem && !FabricContent.supported(player)) ci.cancel();
    }
}
