package nl.requios.effortlessbuilding.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem regression checks without starting Minecraft or Fabric Loader. */
public final class ConfigStorageProbe {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("."), "config-storage-");
        Path file = root.resolve("config/effortlessbuilding/server_config.json");
        Path legacy = root.resolve("world/effortlessbuilding/server_config.json");

        ServerConfigStorage.load(file, legacy);
        check(Files.isRegularFile(file), "Fresh startup must create the standard config file");
        check(!Files.exists(legacy), "Fresh startup must not create a world-scoped config");
        check(ServerConfig.fromJson(Files.readString(file)).creativeReach == new ServerConfig().creativeReach,
                "Fresh config must contain defaults");

        Files.createDirectories(legacy.getParent());
        String oldJson = "{\"creativeReach\":321,\"survivalReach\":47,\"survivalAllowBreaking\":false}";
        Files.writeString(legacy, oldJson);
        Files.delete(file);
        ServerConfigStorage.load(file, legacy);
        check(ServerConfig.INSTANCE.creativeReach == 321 && ServerConfig.INSTANCE.survivalReach == 47
                && !ServerConfig.INSTANCE.survivalAllowBreaking, "Migration must retain configured values");
        check(Files.readString(file).equals(oldJson) && Files.readString(legacy).equals(oldJson),
                "Migration must copy the old file without changing it");

        Files.writeString(file, "{\"creativeReach\":444,\"survivalReach\":55}");
        ServerConfigStorage.load(file, legacy);
        check(ServerConfig.INSTANCE.creativeReach == 444 && ServerConfig.INSTANCE.survivalReach == 55,
                "An existing standard config must take precedence over the legacy file");

        ServerConfig.INSTANCE.creativeReach = 555;
        ServerConfigStorage.save(file);
        ServerConfigStorage.clear();
        ServerConfigStorage.load(file, legacy);
        check(ServerConfig.INSTANCE.creativeReach == 555, "Operator saves must survive restart");
        check(Files.readString(legacy).equals(oldJson), "Saves must leave the legacy backup untouched");
        ServerConfigStorage.clear();
        System.out.println("CONFIG STORAGE PASS defaults, legacy migration, new-file precedence, save/reload, legacy backup");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
