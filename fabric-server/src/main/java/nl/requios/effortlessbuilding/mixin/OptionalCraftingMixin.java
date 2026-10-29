package nl.requios.effortlessbuilding.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import nl.requios.effortlessbuilding.backend.FabricRecipeContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CraftingMenu.class)
public abstract class OptionalCraftingMixin {
    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void effortlessbuilding$recipeOwner(AbstractContainerMenu menu, ServerLevel level,
            Player player, CraftingContainer input, ResultContainer output,
            RecipeHolder<CraftingRecipe> hint, Operation<Void> original) {
        FabricRecipeContext.set(player);
        try {
            original.call(menu, level, player, input, output, hint);
        } finally {
            FabricRecipeContext.clear();
        }
    }
}
