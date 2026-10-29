package nl.requios.effortlessbuilding.server;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.CommonClass;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.backend.FabricBackend;
import nl.requios.effortlessbuilding.backend.FabricContent;
import nl.requios.effortlessbuilding.menu.ModMenus;

/** Dedicated-server entry point; the upstream Fabric initializer is never packaged. */
public final class FabricServerMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "randomizer_tool");
        Registry.register(BuiltInRegistries.ITEM, id, FabricContent.createRandomizer(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));
        Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "randomizer"), ModMenus.RANDOMIZER);
        FabricContent.registerMenu();
        FabricBackend.initialize();
        CommonClass.init();
        Constants.LOG.info("Effortless Building server backend initialized (upstream client compatible)");
    }
}
