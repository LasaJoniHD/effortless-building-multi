package nl.requios.effortlessbuilding.backend;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import nl.requios.effortlessbuilding.config.ServerConfig;

/** Server-side authorization shared by build execution and undo/redo. */
public final class BackendRules {
    private BackendRules() {}

    public static boolean canBuild(ServerPlayer player) {
        GameType gameType = player.gameMode.getGameModeForPlayer();
        return player.isAlive() && (gameType == GameType.CREATIVE || gameType == GameType.SURVIVAL);
    }

    public static boolean canChange(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!canBuild(player) || player.level() != level || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) return false;
        // Includes vanilla spawn protection. Claim mods must integrate their own protection checks.
        return !level.getServer().isUnderSpawnProtection(level, pos, player) && level.mayInteract(player, pos);
    }

    public static boolean validAnchor(ServerPlayer player, BlockPos pos) {
        double reach = ServerConfig.INSTANCE.getReach(player) + 2.0;
        return pos != null && pos.distToCenterSqr(player.getEyePosition()) <= reach * reach;
    }
}
