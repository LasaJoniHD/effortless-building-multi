package nl.requios.effortlessbuilding.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import nl.requios.effortlessbuilding.backend.FabricContent;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers shift-click transfers as well as Slot subclasses overriding mayPickup. */
@Mixin(AbstractContainerMenu.class)
public abstract class OptionalContainerMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void effortlessbuilding$guardTransfer(int slot, int button, ContainerInput input,
                                                  Player player, CallbackInfo ci) {
        if (FabricContent.supported(player)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        boolean hiddenSlot = slot >= 0 && slot < menu.slots.size()
                && menu.slots.get(slot).getItem().getItem() instanceof RandomizerToolItem;
        boolean hiddenSwap = input == ContainerInput.SWAP && button >= 0 && button < 9
                && player.getInventory().getItem(button).getItem() instanceof RandomizerToolItem;
        if (hiddenSlot || hiddenSwap || menu.getCarried().getItem() instanceof RandomizerToolItem) {
            ci.cancel();
            menu.sendAllDataToRemote();
        }
    }
}
