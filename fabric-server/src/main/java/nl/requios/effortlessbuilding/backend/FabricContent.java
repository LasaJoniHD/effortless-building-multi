package nl.requios.effortlessbuilding.backend;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerMenuUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import nl.requios.effortlessbuilding.menu.ModMenus;

/** Per-recipient item encoding, including nested stacks, equipment and entity metadata. */
public final class FabricContent {
    private FabricContent() {}

    public static boolean supported(Player player) {
        return BackendSession.supports(player.getUUID());
    }

    /** Encoding can precede JOIN; advertised channels already identify the companion then. */
    public static boolean canSyncTo(Player player) {
        return player instanceof ServerPlayer serverPlayer && FabricBackend.hasClientChannels(serverPlayer);
    }

    public static Item createRandomizer(Item.Properties properties) {
        Item item = new RandomizerToolItem(properties) {
            @Override
            public InteractionResult use(Level level, Player player, InteractionHand hand) {
                return supported(player) ? super.use(level, player, hand) : InteractionResult.PASS;
            }
        };
        PolymerItem.registerOverlay(item, new PolymerItem() {
            private boolean supported(PacketContext context) {
                Player player = PolymerCommonUtils.getPlayer(context);
                return player != null && canSyncTo(player);
            }

            @Override
            public Item getPolymerItem(ItemStack stack, PacketContext context) {
                return supported(context) ? item : Items.AIR;
            }

            @Override
            public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltip,
                                                 PacketContext context, HolderLookup.Provider lookup) {
                // No fallback item, custom data, model or tooltip is exposed to vanilla.
                return supported(context) ? stack : ItemStack.EMPTY;
            }

            @Override
            public boolean canSyncRawToClient(PacketContext context) { return supported(context); }
        });
        return item;
    }

    public static void registerMenu() {
        PolymerMenuUtils.registerType(ModMenus.RANDOMIZER);
    }
}
