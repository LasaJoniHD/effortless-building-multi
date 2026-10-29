package nl.requios.effortlessbuilding.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.backend.FabricContent;
import nl.requios.effortlessbuilding.backend.FabricRecipeContext;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipe.class)
public abstract class OptionalRecipeMixin {
    @Shadow @Final private ItemStackTemplate result;

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void effortlessbuilding$onlyModdedCrafting(CraftingInput input, Level level,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide() || !(result.item().value() instanceof RandomizerToolItem)) return;
        var player = FabricRecipeContext.get();
        if (player == null || !FabricContent.supported(player)) cir.setReturnValue(false);
    }
}
