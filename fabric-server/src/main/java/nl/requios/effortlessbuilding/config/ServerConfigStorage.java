package nl.requios.effortlessbuilding.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import nl.requios.effortlessbuilding.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Backend settings shared by all worlds in this Fabric installation. */
public final class ServerConfigStorage {
    private ServerConfigStorage() {}

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("effortlessbuilding").resolve("server_config.json");
    }

    public static void load(MinecraftServer server) {
        Path legacyFile = server.getWorldPath(LevelResource.ROOT)
                .resolve("effortlessbuilding").resolve("server_config.json");
        load(configFile(), legacyFile);
    }

    static void load(Path file, Path legacyFile) {
        ServerConfig.INSTANCE.reset();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                if (Files.exists(legacyFile)) {
                    // Never overwrite a new config or remove the original world file.
                    Files.copy(legacyFile, file);
                    Constants.LOG.info("[EffortlessBuilding] Imported server config from {} to {}; original retained as backup",
                            legacyFile, file);
                } else {
                    Files.writeString(file, ServerConfig.INSTANCE.toJson(), StandardOpenOption.CREATE_NEW);
                }
            }
            ServerConfig.INSTANCE.copyFrom(ServerConfig.fromJson(Files.readString(file)));
        } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load server config {}: {}", file, e.getMessage());
        }
    }

    /** Retains the upstream API used by the operator configuration packet handler. */
    public static void save(MinecraftServer server) {
        save(configFile());
    }

    static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, ServerConfig.INSTANCE.toJson());
        } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save server config {}: {}", file, e.getMessage());
        }
    }

    public static void clear() {
        ServerConfig.INSTANCE.reset();
    }
}
