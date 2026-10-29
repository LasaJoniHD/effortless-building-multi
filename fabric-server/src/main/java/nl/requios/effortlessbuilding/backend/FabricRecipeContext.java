package nl.requios.effortlessbuilding.backend;

import net.minecraft.world.entity.player.Player;

/** Restricts the tool recipe to an opted-in player's crafting grid, excluding automation. */
public final class FabricRecipeContext {
    private static final ThreadLocal<Player> PLAYER = new ThreadLocal<>();
    private FabricRecipeContext() {}
    public static Player get() { return PLAYER.get(); }
    public static void set(Player player) { PLAYER.set(player); }
    public static void clear() { PLAYER.remove(); }
}
