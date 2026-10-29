package nl.requios.effortlessbuilding.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import nl.requios.effortlessbuilding.backend.FabricContent;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class OptionalContentSlotMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void effortlessbuilding$preventVanillaTransfer(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (getItem().getItem() instanceof RandomizerToolItem && !FabricContent.supported(player)) cir.setReturnValue(false);
    }
}
