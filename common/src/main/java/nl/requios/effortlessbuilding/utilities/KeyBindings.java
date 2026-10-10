package nl.requios.effortlessbuilding.utilities;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import nl.requios.effortlessbuilding.mixin.KeyMappingAccessor;

/**
 * Central holder for all mod keybindings.
 * Instances are created here; loader-specific code registers them.
 */
public class KeyBindings {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("effortlessbuilding", "keybindings"));

    public static KeyMapping openRadialMenu = new KeyMapping(
            "key.effortlessbuilding.open_radial_menu",
            InputConstants.KEY_LALT,
            CATEGORY
    );
    
    public static KeyMapping openModifiersScreen = new KeyMapping(
            "key.effortlessbuilding.open_modifiers_screen",
            InputConstants.KEY_ADD,
            CATEGORY
    );

    public static KeyMapping undo = new KeyMapping(
            "key.effortlessbuilding.undo.desc",
            InputConstants.KEY_Z,
            CATEGORY
    );

    public static KeyMapping redo = new KeyMapping(
            "key.effortlessbuilding.redo.desc",
            InputConstants.KEY_Y,
            CATEGORY
    );

    /**
     * Checks if the physical key bound to a KeyMapping is currently held down.
     * Unlike KeyMapping.isDown(), this works even when a Screen is open.
     */
    public static boolean isKeyDown(KeyMapping keyMapping) {
        return InputConstants.isKeyDown(((KeyMappingAccessor) keyMapping).effortlessbuilding$getKey().getValue());
    }
}
