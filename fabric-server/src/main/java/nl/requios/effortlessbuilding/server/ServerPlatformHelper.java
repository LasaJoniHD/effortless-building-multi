package nl.requios.effortlessbuilding.server;

import nl.requios.effortlessbuilding.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class ServerPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
