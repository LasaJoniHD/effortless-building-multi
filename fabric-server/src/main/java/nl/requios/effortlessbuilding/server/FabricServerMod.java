package nl.requios.effortlessbuilding.server;

import net.fabricmc.api.ModInitializer;
import nl.requios.effortlessbuilding.CommonClass;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.backend.FabricBackend;

/** Dedicated-server entry point; the upstream Fabric initializer is never packaged. */
public final class FabricServerMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricBackend.initialize();
        CommonClass.init();
        Constants.LOG.info("Effortless Building server backend initialized (upstream client compatible)");
    }
}
