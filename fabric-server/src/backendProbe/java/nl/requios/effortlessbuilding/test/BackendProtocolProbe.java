package nl.requios.effortlessbuilding.test;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.cookie.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.fabricmc.fabric.impl.recipe.sync.ClientboundRecipeSyncPayload;
import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions.ActionEnum;
import nl.requios.effortlessbuilding.network.*;

import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Real TCP connections, decoded by Mojang's vanilla codecs; no Fabric loader is launched. */
public final class BackendProtocolProbe {
    private final boolean modded;
    private final boolean mapped;
    private final boolean fabricOnly;
    private boolean configurationAdvertised;
    private volatile boolean receivedRegistryMapping;
    private volatile int recipeSyncs;
    private volatile boolean toolRecipe;
    private final RegistryDataCollector collector = new RegistryDataCollector();
    private final Set<String> payloads = ConcurrentHashMap.newKeySet();
    private volatile Throwable failure;
    private volatile boolean joined;
    private volatile boolean finished;
    private volatile int packets;
    private volatile boolean receivedTool;
    private volatile int randomizerMenu;
    private volatile int vanillaChest;
    private volatile boolean chestToolHidden;
    private boolean chestRequested;
    private boolean chestClicked;
    private volatile int craftingMenu;
    private volatile List<net.minecraft.world.item.ItemStack> craftingContents;
    private boolean craftingRequested;
    private boolean craftingClicked;
    private Connection connection;
    private RegistryAccess.Frozen registries;
    private ProtocolInfo<ServerGamePacketListener> outbound;

    private BackendProtocolProbe(boolean modded, boolean mapped, boolean fabricOnly) {
        this.modded = modded;
        this.mapped = mapped;
        this.fabricOnly = fabricOnly;
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "vanilla" : args[0];
        if (mode.equals("stop")) {
            try (ProbeRcon rcon = new ProbeRcon()) { System.out.println(rcon.command("stop")); }
            return;
        }
        Process vanilla = null;
        Process fabricClient = null;
        if (mode.equals("mixed") || mode.equals("mapped")) {
            Files.deleteIfExists(Path.of("vanilla-chest-go"));
            Files.deleteIfExists(Path.of("vanilla-chest-done"));
            Files.deleteIfExists(Path.of("vanilla-craft-go"));
            Files.deleteIfExists(Path.of("vanilla-craft-done"));
            vanilla = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), BackendProtocolProbe.class.getName(), "vanilla")
                    .inheritIO().start();
            if (mode.equals("mapped")) {
                fabricClient = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("java.class.path"), BackendProtocolProbe.class.getName(), "fabric-only")
                        .inheritIO().start();
            }
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RecipeSyncImpl.addSynchronizedSerializer(BuiltInRegistries.RECIPE_SERIALIZER.getValue(
                Identifier.parse("minecraft:crafting_shaped")));
        boolean fabricOnly = mode.equals("fabric-only");
        boolean mapped = mode.equals("mapped") || fabricOnly;
        boolean modded = !mode.equals("vanilla") && !fabricOnly;
        if (modded) {
            reopen(BuiltInRegistries.ITEM);
            reopen(BuiltInRegistries.MENU);
            if (mapped) {
                // Deliberately shift the tool's local ID to prove standard Fabric remapping is used.
                Identifier extra = Identifier.parse("backend_probe:client_only");
                Registry.register(BuiltInRegistries.ITEM, extra, new Item(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, extra))));
            }
            // Emulate the companion's local registries, while the vanilla child has no custom entries.
            Identifier id = Identifier.fromNamespaceAndPath("effortlessbuilding", "randomizer_tool");
            Registry.register(BuiltInRegistries.ITEM, id, new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));
            MenuType<?> menu = Registry.register(BuiltInRegistries.MENU, Identifier.parse("effortlessbuilding:randomizer"),
                    new MenuType<>((container, inventory) -> null, FeatureFlags.VANILLA_SET));
            Method bindValue = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
            bindValue.setAccessible(true);
            bindValue.invoke(BuiltInRegistries.MENU.get(Identifier.parse("effortlessbuilding:randomizer")).orElseThrow(), menu);
            Field frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
            frozen.setAccessible(true);
            frozen.set(BuiltInRegistries.ITEM, true);
            frozen.set(BuiltInRegistries.MENU, true);
        }

        BackendProtocolProbe probe = new BackendProtocolProbe(modded, mapped, fabricOnly);
        try {
            probe.run();
            if (vanilla != null && vanilla.waitFor() != 0) throw new AssertionError("Vanilla connection probe failed");
            if (fabricClient != null && fabricClient.waitFor() != 0) throw new AssertionError("Fabric-without-companion probe failed");
        } catch (Throwable failure) {
            failure.printStackTrace();
            if (vanilla != null) vanilla.destroy();
            if (fabricClient != null) fabricClient.destroy();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void reopen(Registry<?> registry) throws Exception {
        // A real Fabric client registers during bootstrap. This loader-free probe appends the
        // same two entries after vanilla bootstrap; the separate vanilla process stays untouched.
        Field frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.set(registry, false);
        Field intrusive = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        if (registry == BuiltInRegistries.ITEM) intrusive.set(registry, new IdentityHashMap<>());
    }

    private void run() throws Exception {
        // Keep this reusable fixture stable across sequential runs and survival assertions.
        if (modded) try (ProbeRcon rcon = new ProbeRcon()) { rcon.command("difficulty peaceful"); }
        connection = Connection.connectToServer(new InetSocketAddress("127.0.0.1", 25576), EventLoopGroupHolder.remote(false), null);
        Field channelField = Connection.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        for (int wait = 0; channelField.get(connection) == null && wait < 500; wait++) Thread.sleep(10);
        ((Channel) channelField.get(connection)).pipeline().addBefore("packet_handler", "probe_errors",
                new io.netty.channel.ChannelInboundHandlerAdapter() {
                    @Override public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
                        cause.printStackTrace();
                        ctx.fireExceptionCaught(cause);
                    }
                });
        connection.initiateServerboundPlayConnection("127.0.0.1", 25576,
                listener(ClientLoginPacketListener.class, ConnectionProtocol.LOGIN));
        String name = modded ? "BackendModded" : fabricOnly ? "BackendFabric" : "BackendVanilla";
        connection.send(new ServerboundHelloPacket(name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))));
        await(() -> joined, 30_000, "join");
        if (modded || fabricOnly) {
            await(() -> recipeSyncs > 0, 10_000, "Fabric full recipe sync");
            if (!mapped && toolRecipe) throw new AssertionError("Tool recipe leaked before companion opt-in");
        }
        if (modded) {
            if (!mapped) try (ProbeRcon rcon = new ProbeRcon()) {
                rcon.command("gamemode creative BackendModded");
                rcon.command("tp BackendModded 0.5 -60 0.5");
                rcon.command("setblock 1 -59 8 air");
                rcon.command("item replace entity BackendModded weapon.mainhand with stone 64");
                place(BuildModeEnum.SINGLE, new BlockPos(1, -59, 8), new BlockPos(1, -59, 8), null);
                pause();
                expect(rcon, "execute if block 1 -59 8 air run say EB_UNNEGOTIATED_DENIED", "EB_UNNEGOTIATED_DENIED");
            }
            if (!mapped) custom("minecraft:register", buf -> buf.writeBytes(("effortlessbuilding:sync_modifiers\0effortlessbuilding:sync_server_config\0"
                    + "effortlessbuilding:sync_ae2_count").getBytes(StandardCharsets.UTF_8)));
            await(() -> payloads.contains("effortlessbuilding:sync_server_config")
                    && payloads.contains("effortlessbuilding:sync_modifiers"), 10_000, "backend synchronization");
            await(() -> toolRecipe, 10_000, "companion full randomizer recipe (including late opt-in)");
            if (mapped && !receivedRegistryMapping) throw new AssertionError("No upstream registry map; channels=" + payloads);
            gameplay();
            int beforeReload = recipeSyncs;
            try (ProbeRcon rcon = new ProbeRcon()) { rcon.command("reload"); }
            await(() -> recipeSyncs > beforeReload && toolRecipe, 15_000, "full recipe sync after reload");
            if (mapped && !receivedRegistryMapping) throw new AssertionError("Unchanged client did not receive Fabric registry remapping");
        }
        long until = System.currentTimeMillis() + Long.getLong("probe.duration", modded ? 5000L : 30_000L);
        while (System.currentTimeMillis() < until) {
            tick();
            check();
            if (!modded && !fabricOnly) vanillaActions();
            Thread.sleep(25);
        }
        finished = true;
        connection.disconnect(Component.literal("Probe complete"));
        System.out.println("PROBE PASS " + name + ": " + packets + " decoded packets, recipe syncs=" + recipeSyncs + ", payloads=" + payloads);
    }

    private void gameplay() throws Exception {
        try (ProbeRcon rcon = new ProbeRcon()) {
            rcon.command("gamemode creative BackendModded");
            rcon.command("gamemode creative BackendVanilla");
            rcon.command("fill -10 -61 -10 15 -61 15 stone");
            rcon.command("tp BackendModded 0.5 -60 0.5");
            rcon.command("tp BackendVanilla 3.5 -60 0.5");
            rcon.command("fill 1 -59 1 8 -59 8 air");
            rcon.command("item replace entity BackendModded weapon.mainhand with stone 64");
            custom("effortlessbuilding:update_modifiers", buf -> buf.writeUtf("[]"));
            pause();
            place(BuildModeEnum.LINE, new BlockPos(1, -59, 1), new BlockPos(3, -59, 1), null);
            pause();
            expect(rcon, "execute if block 1 -59 1 stone if block 2 -59 1 stone if block 3 -59 1 stone run say EB_PLACE_OK", "EB_PLACE_OK");
            custom("effortlessbuilding:undo", buf -> {}); pause();
            expect(rcon, "execute if block 1 -59 1 air if block 2 -59 1 air if block 3 -59 1 air run say EB_UNDO_OK", "EB_UNDO_OK");
            custom("effortlessbuilding:redo", buf -> {}); pause();
            expect(rcon, "execute if block 1 -59 1 stone if block 3 -59 1 stone run say EB_REDO_OK", "EB_REDO_OK");
            custom("effortlessbuilding:break_build_mode", buf -> BreakBuildModePacket.STREAM_CODEC.encode(buf,
                    new BreakBuildModePacket(BuildModeEnum.LINE, new BlockPos(1, -59, 1), new BlockPos(3, -59, 1), null,
                            ActionEnum.FULL, ActionEnum.CUBE_FULL, ActionEnum.SHORT_EDGE, ActionEnum.CIRCLE_START_CORNER, true)));
            pause();
            expect(rcon, "execute if block 1 -59 1 air if block 3 -59 1 air run say EB_BREAK_OK", "EB_BREAK_OK");

            custom("effortlessbuilding:update_modifiers", buf -> buf.writeUtf("[{\"type\":\"array\",\"count\":2,\"offsetX\":2,\"offsetY\":0,\"offsetZ\":0}]"));
            pause();
            place(BuildModeEnum.SINGLE, new BlockPos(1, -59, 2), new BlockPos(1, -59, 2), null);
            pause();
            expect(rcon, "execute if block 1 -59 2 stone if block 3 -59 2 stone if block 5 -59 2 stone run say EB_ARRAY_OK", "EB_ARRAY_OK");
            custom("effortlessbuilding:update_modifiers", buf -> buf.writeUtf("[]"));
            rcon.command("gamemode spectator BackendModded");
            pause();
            place(BuildModeEnum.SINGLE, new BlockPos(1, -59, 3), new BlockPos(1, -59, 3), null);
            pause();
            expect(rcon, "execute if block 1 -59 3 air run say EB_SPECTATOR_DENIED", "EB_SPECTATOR_DENIED");
            rcon.command("gamemode creative BackendModded");
            rcon.command("item replace entity BackendModded weapon.mainhand with effortlessbuilding:randomizer_tool[minecraft:custom_data={RandomizerBlocks:['minecraft:stone'],RandomizerRatios:[I;1,0,0,0,0,0,0,0,0]}]");
            await(() -> receivedTool, 5000, "real randomizer item");
            connection.send(new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, false, false, false, true, false)));
            connection.send(new ServerboundUseItemPacket(net.minecraft.world.InteractionHand.MAIN_HAND, 1, 0, 0));
            await(() -> randomizerMenu > 0, 5000, "randomizer editor");
            connection.send(new ServerboundContainerButtonClickPacket(randomizerMenu, 0));
            pause();
            expect(rcon, "execute if items entity BackendModded weapon.mainhand effortlessbuilding:randomizer_tool[minecraft:custom_data~{RandomizerRatios:[I;2,0,0,0,0,0,0,0,0]}] run say EB_MENU_SAVED", "EB_MENU_SAVED");
            connection.send(new ServerboundContainerClosePacket(randomizerMenu));
            connection.send(new ServerboundPlayerInputPacket(net.minecraft.world.entity.player.Input.EMPTY));
            place(BuildModeEnum.SINGLE, new BlockPos(1, -59, 4), new BlockPos(1, -59, 4), null); pause();
            expect(rcon, "execute if block 1 -59 4 stone run say EB_RANDOMIZER_OK", "EB_RANDOMIZER_OK");
            // The observer decodes equipment, item entities and nested chest contents with vanilla registries.
            rcon.command("summon item 3.5 -60 0.5 {Item:{id:'effortlessbuilding:randomizer_tool',count:1},PickupDelay:0}");
            rcon.command("setblock 4 -60 1 chest{Items:[{Slot:0b,id:'effortlessbuilding:randomizer_tool',count:1},{Slot:1b,id:'minecraft:stone',count:1}]}");
            Files.writeString(Path.of("vanilla-chest-go"), "go");
            await(() -> Files.exists(Path.of("vanilla-chest-done")), 5000, "vanilla chest interaction");
            rcon.command("recipe give BackendVanilla *");
            rcon.command("recipe give BackendModded *");
            pause();
            expect(rcon, "execute unless items entity BackendVanilla inventory.* effortlessbuilding:randomizer_tool unless items entity BackendVanilla hotbar.* effortlessbuilding:randomizer_tool run say EB_VANILLA_NO_TOOL", "EB_VANILLA_NO_TOOL");
            expect(rcon, "execute if items block 4 -60 1 container.0 effortlessbuilding:randomizer_tool run say EB_CHEST_TOOL_UNTOUCHED", "EB_CHEST_TOOL_UNTOUCHED");
            expect(rcon, "execute if items entity BackendVanilla hotbar.* minecraft:stone run say EB_VANILLA_NORMAL_INVENTORY", "EB_VANILLA_NORMAL_INVENTORY");

            rcon.command("setblock 2 -60 1 crafting_table");
            rcon.command("kill @e[type=item]");
            prepareCrafting(rcon, "BackendModded");
            prepareCrafting(rcon, "BackendVanilla");
            pause();
            Files.writeString(Path.of("vanilla-craft-go"), "go");
            useCraftingTable();
            await(() -> craftingMenu > 0, 5000, "modded crafting table");
            fillCraftingGrid();
            await(() -> craftingContents != null && !craftingContents.getFirst().isEmpty(), 5000, "modded recipe result");
            connection.send(new ServerboundContainerClickPacket(craftingMenu, 0, (short) 0, (byte) 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
            pause();
            expect(rcon, "execute if items entity BackendModded hotbar.* effortlessbuilding:randomizer_tool run say EB_MODDED_CRAFTING", "EB_MODDED_CRAFTING");
            connection.send(new ServerboundContainerClosePacket(craftingMenu));
            await(() -> Files.exists(Path.of("vanilla-craft-done")), 5000, "vanilla recipe rejection");
            if (!Files.readString(Path.of("vanilla-craft-done")).equals("pass")) throw new AssertionError("Vanilla tool crafting was not rejected");
            System.out.println("ASSERT PASS EB_VANILLA_CRAFTING_DENIED");

            rcon.command("clear BackendModded");
            rcon.command("gamemode survival BackendModded");
            rcon.command("item replace entity BackendModded weapon.mainhand with stone 2");
            place(BuildModeEnum.LINE, new BlockPos(1, -59, 5), new BlockPos(3, -59, 5), null); pause();
            expect(rcon, "execute if block 1 -59 5 stone if block 2 -59 5 stone if block 3 -59 5 air run say EB_SURVIVAL_INVENTORY_LIMIT", "EB_SURVIVAL_INVENTORY_LIMIT");
            rcon.command("op BackendModded");
            custom("effortlessbuilding:update_server_config", buf -> buf.writeUtf("{\"creativeReach\":150}"));
            pause();
            // Vanilla remains connected while the config is broadcast to modded sessions.
            expect(rcon, "execute if entity BackendVanilla run say EB_CONFIG_BROADCAST_SAFE", "EB_CONFIG_BROADCAST_SAFE");
            rcon.command("deop BackendModded");
            System.out.println("GAMEPLAY PASS building, modifiers, undo/redo, permissions, survival, randomizer editor, crafting, vanilla isolation");
        }
    }

    private void vanillaActions() throws Exception {
        if (!chestRequested && Files.exists(Path.of("vanilla-chest-go"))) {
            chestRequested = true;
            BlockPos chest = new BlockPos(4, -60, 1);
            connection.send(new ServerboundUseItemOnPacket(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(chest), Direction.NORTH, chest, false), 1));
        }
        if (!chestClicked && vanillaChest > 0 && chestToolHidden) {
            chestClicked = true;
            // Deliberately target the hidden slot; the server must reject it even though the UI sees air.
            connection.send(new ServerboundContainerClickPacket(vanillaChest, 0, (short) 0, (byte) 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
            connection.send(new ServerboundContainerClickPacket(vanillaChest, 0, (short) 1, (byte) 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
            Thread.sleep(300);
            connection.send(new ServerboundContainerClosePacket(vanillaChest));
            Files.writeString(Path.of("vanilla-chest-done"), "done");
        }
        if (!craftingRequested && Files.exists(Path.of("vanilla-craft-go"))) {
            craftingRequested = true;
            useCraftingTable();
        }
        if (!craftingClicked && craftingMenu > 0) {
            craftingClicked = true;
            fillCraftingGrid();
            Thread.sleep(500);
            List<net.minecraft.world.item.ItemStack> contents = craftingContents;
            boolean filled = contents != null && contents.size() > 9
                    && contents.get(2).is(net.minecraft.world.item.Items.IRON_INGOT)
                    && contents.get(4).is(net.minecraft.world.item.Items.IRON_INGOT)
                    && contents.get(6).is(net.minecraft.world.item.Items.IRON_INGOT)
                    && contents.get(5).is(net.minecraft.world.item.Items.POPPY)
                    && contents.get(8).is(net.minecraft.world.item.Items.REDSTONE);
            Files.writeString(Path.of("vanilla-craft-done"), filled && contents.getFirst().isEmpty() ? "pass" : "fail");
            connection.send(new ServerboundContainerClosePacket(craftingMenu));
        }
    }

    private static void prepareCrafting(ProbeRcon rcon, String player) throws Exception {
        rcon.command("clear " + player);
        rcon.command("tp " + player + " 3.5 -60 0.5");
        String[] items = {"iron_ingot", "iron_ingot", "iron_ingot", "poppy", "redstone"};
        for (int i = 0; i < items.length; i++) rcon.command("item replace entity " + player + " hotbar." + i + " with " + items[i]);
    }
    private void useCraftingTable() {
        BlockPos table = new BlockPos(2, -60, 1);
        connection.send(new ServerboundSetCarriedItemPacket(8));
        connection.send(new ServerboundUseItemOnPacket(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(table), Direction.NORTH, table, false), 2));
    }
    private void fillCraftingGrid() {
        int[] slots = {2, 4, 6, 5, 8};
        for (int i = 0; i < slots.length; i++) connection.send(new ServerboundContainerClickPacket(craftingMenu, 0,
                (short) slots[i], (byte) i, net.minecraft.world.inventory.ContainerInput.SWAP,
                it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }

    private void place(BuildModeEnum mode, BlockPos first, BlockPos second, BlockPos third) throws Exception {
        custom("effortlessbuilding:place_build_mode", buf -> PlaceBuildModePacket.STREAM_CODEC.encode(buf,
                new PlaceBuildModePacket(mode, first, second, third, Direction.UP, Vec3.atCenterOf(first),
                        ActionEnum.FULL, ActionEnum.CUBE_FULL, ActionEnum.SHORT_EDGE, ActionEnum.CIRCLE_START_CORNER,
                        BuildSettings.ReplaceMode.ONLY_AIR, true)));
    }
    private void pause() throws Exception {
        for (int i = 0; i < 16; i++) { tick(); check(); Thread.sleep(25); }
    }
    private static void expect(ProbeRcon rcon, String command, String expected) throws Exception {
        String condition = command.substring(0, command.indexOf(" run say "));
        String result = rcon.command(condition);
        if (!result.startsWith("Test passed")) throw new AssertionError(condition + " -> " + result);
        System.out.println("ASSERT PASS " + expected);
    }

    private void tick() {
        connection.tick();
        if (joined) connection.send(new ServerboundClientTickEndPacket());
    }

    private interface Condition { boolean get(); }
    private void await(Condition condition, long timeout, String operation) throws Exception {
        long until = System.currentTimeMillis() + timeout;
        while (!condition.get() && System.currentTimeMillis() < until) {
            tick(); check(); Thread.sleep(25);
        }
        if (!condition.get()) throw new AssertionError("Timed out waiting for " + operation);
    }
    private void check() {
        if (failure != null) throw new AssertionError("Protocol probe failed", failure);
        if (!connection.isConnected()) throw new AssertionError("Connection closed before probe completed");
    }

    @SuppressWarnings("unchecked")
    private <T> T listener(Class<T> type, ConnectionProtocol protocol) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("flow")) return PacketFlow.CLIENTBOUND;
            if (method.getName().equals("protocol")) return protocol;
            if (method.getName().equals("isAcceptingMessages") || method.getName().equals("shouldHandleMessage")) return true;
            if (method.getName().equals("onDisconnect")) {
                if (!finished) failure = new AssertionError("Disconnected: " + args[0]);
                return null;
            }
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            if (args != null && args.length == 1 && args[0] instanceof Packet<?> packet) {
                try { received(packet, protocol); }
                catch (Throwable error) { failure = error; error.printStackTrace(); }
            }
            return null;
        });
    }

    private void received(Packet<?> packet, ConnectionProtocol protocol) throws Exception {
        packets++;
        if (packet instanceof ClientboundLoginCompressionPacket compression) connection.setupCompression(compression.getCompressionThreshold(), false);
        else if (packet instanceof ClientboundLoginFinishedPacket) {
            connection.setupInboundProtocol(configurationProtocol(),
                    listener(ClientConfigurationPacketListener.class, ConnectionProtocol.CONFIGURATION));
            connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
            connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
            connection.send(new ServerboundClientInformationPacket(ClientInformation.createDefault()));
        } else if (packet instanceof ClientboundCustomQueryPacket query) connection.send(new ServerboundCustomQueryAnswerPacket(query.transactionId(), null));
        else if (packet instanceof ClientboundSelectKnownPacks) connection.send(new ServerboundSelectKnownPacks(List.of()));
        else if (packet instanceof ClientboundRegistryDataPacket data) {
            if (!modded && data.entries().stream().anyMatch(entry -> entry.id().getNamespace().equals("effortlessbuilding"))) {
                throw new AssertionError("Vanilla received custom registry entries");
            }
            collector.appendContents(data.registry(), data.entries());
        } else if (packet instanceof ClientboundUpdateTagsPacket tags && protocol == ConnectionProtocol.CONFIGURATION) collector.appendTags(tags.tags());
        else if (packet instanceof ClientboundFinishConfigurationPacket) {
            registries = collector.collectGameRegistries(ResourceProvider.EMPTY,
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), false);
            connection.setupInboundProtocol(gameProtocol(),
                    listener(ClientGamePacketListener.class, ConnectionProtocol.PLAY));
            connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
            outbound = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries), new GameProtocols.Context() {
                public boolean hasInfiniteMaterials() { return true; }
                public boolean canUseCommandBlocks() { return true; }
            });
            connection.setupOutboundProtocol(outbound);
        } else if (packet instanceof ClientboundLoginPacket) {
            connection.send(new ServerboundPlayerLoadedPacket());
            joined = true;
        } else if (packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0) {
            connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        } else if (packet instanceof ClientboundKeepAlivePacket alive) connection.send(new ServerboundKeepAlivePacket(alive.getId()));
        else if (packet instanceof ClientboundPingPacket ping) connection.send(new ServerboundPongPacket(ping.getId()));
        else if (packet instanceof ClientboundPlayerPositionPacket position) connection.send(new ServerboundAcceptTeleportationPacket(position.id(), position.change().position().x, position.change().position().y, position.change().position().z, position.change().yRot(), position.change().xRot()));
        else if (packet instanceof ClientboundCookieRequestPacket cookie) connection.send(new ServerboundCookieResponsePacket(cookie.key(), null));
        else if (packet instanceof ClientboundCustomPayloadPacket custom) {
            String id = custom.payload().type().id().toString();
            payloads.add(id);
            if (!modded && id.startsWith("effortlessbuilding:")) throw new AssertionError("Vanilla received " + id);
            if ((modded || fabricOnly) && protocol == ConnectionProtocol.CONFIGURATION && id.equals("minecraft:register") && !configurationAdvertised) {
                configurationAdvertised = true;
                customConfiguration("minecraft:register", buf -> buf.writeBytes(
                        "c:version\0c:register\0fabric:registry/sync\0fabric:recipe_sync/supported_serializers".getBytes(StandardCharsets.UTF_8)));
                customConfiguration("fabric:recipe_sync/supported_serializers", buf -> {
                    buf.writeVarInt(1);
                    buf.writeIdentifier(Identifier.parse("minecraft:crafting_shaped"));
                });
            }
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            if (BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString().equals("effortlessbuilding:randomizer_tool")) receivedTool = true;
        } else if (packet instanceof ClientboundOpenScreenPacket screen) {
            String type = BuiltInRegistries.MENU.getKey(screen.getType()).toString();
            System.out.println("OPEN " + (modded ? "modded " : "vanilla ") + type);
            if (type.equals("effortlessbuilding:randomizer")) {
                if (!modded) throw new AssertionError("Vanilla received custom menu");
                randomizerMenu = screen.getContainerId();
            } else if (!modded && screen.getType() == MenuType.GENERIC_9x3) vanillaChest = screen.getContainerId();
            else if (screen.getType() == MenuType.CRAFTING) craftingMenu = screen.getContainerId();
        } else if (packet instanceof ClientboundContainerSetContentPacket contents) {
            if (craftingMenu > 0 && contents.containerId() == craftingMenu) craftingContents = contents.items();
            if (!modded && contents.containerId() == vanillaChest && !contents.items().isEmpty()) {
                if (!contents.items().getFirst().isEmpty()) throw new AssertionError("Custom item leaked into vanilla chest");
                chestToolHidden = true;
            }
        } else if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
            System.out.println("OVERLAY " + (modded ? "modded " : "vanilla ") + chat.content().getString());
        } else if (packet instanceof ClientboundDisconnectPacket disconnect) throw new AssertionError(disconnect.reason().getString());
        else if (packet instanceof ClientboundLoginDisconnectPacket disconnect) throw new AssertionError(disconnect.reason().getString());
        else if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> child : bundle.subPackets()) received(child, protocol);
        }
    }

    /** Bypasses only the vanilla outbound custom-payload whitelist to emulate the companion mod. */
    private void custom(String id, Consumer<FriendlyByteBuf> writer) throws Exception {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        outbound.codec().encode(encoded, new ServerboundCustomPayloadPacket(new BrandPayload("probe")));
        int packetId = encoded.readVarInt();
        encoded.release();
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        raw.writeVarInt(packetId);
        raw.writeIdentifier(Identifier.parse(id));
        writer.accept(raw);
        Field field = Connection.class.getDeclaredField("channel");
        field.setAccessible(true);
        ((Channel) field.get(connection)).writeAndFlush(raw);
    }

    private void customConfiguration(String id, Consumer<FriendlyByteBuf> writer) throws Exception {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        ConfigurationProtocols.SERVERBOUND.codec().encode(encoded, new ServerboundCustomPayloadPacket(new BrandPayload("probe")));
        int packetId = encoded.readVarInt();
        encoded.release();
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        raw.writeVarInt(packetId);
        raw.writeIdentifier(Identifier.parse(id));
        writer.accept(raw);
        Field field = Connection.class.getDeclaredField("channel");
        field.setAccessible(true);
        ((Channel) field.get(connection)).writeAndFlush(raw);
    }

    /** Decode Fabric's existing configuration protocol without loading a client mod or Polymer. */
    private ProtocolInfo<ClientConfigurationPacketListener> configurationProtocol() {
        ProtocolInfo<ClientConfigurationPacketListener> vanilla = ConfigurationProtocols.CLIENTBOUND;
        if (!modded && !fabricOnly) return vanilla;
        int[] customId = {-1};
        ConfigurationProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> {
            if (type == CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD) customId[0] = id;
        });
        return new ProtocolInfo<>() {
            public ConnectionProtocol id() { return vanilla.id(); }
            public PacketFlow flow() { return vanilla.flow(); }
            public net.minecraft.network.protocol.BundlerInfo bundlerInfo() { return vanilla.bundlerInfo(); }
            public net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Packet<? super ClientConfigurationPacketListener>> codec() {
                return net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> vanilla.codec().encode(buffer, packet), buffer -> {
                    int start = buffer.readerIndex();
                    FriendlyByteBuf input = new FriendlyByteBuf(buffer);
                    try {
                        if (input.readVarInt() == customId[0]) {
                            String id = input.readIdentifier().toString();
                            if (id.equals("c:version")) {
                                input.readVarIntArray();
                                customConfiguration(id, out -> out.writeVarIntArray(new int[]{1}));
                            } else if (id.equals("c:register")) {
                                input.readVarInt(); input.readUtf();
                                int count = input.readVarInt();
                                for (int i = 0; i < count; i++) input.readIdentifier();
                                customConfiguration(id, out -> {
                                    out.writeVarInt(1); out.writeUtf("play"); out.writeVarInt(modded && mapped ? 4 : 1);
                                    out.writeIdentifier(ClientboundRecipeSyncPayload.TYPE.id());
                                    if (modded && mapped) {
                                        out.writeIdentifier(SyncModifiersS2CPacket.TYPE.id());
                                        out.writeIdentifier(SyncServerConfigS2CPacket.TYPE.id());
                                        out.writeIdentifier(SyncAE2CountS2CPacket.TYPE.id());
                                    }
                                });
                            } else if (id.equals("fabric:registry/sync")) {
                                var mapping = net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload.CODEC.decode(input);
                                if (!modded) throw new AssertionError("Non-companion Fabric client received custom registries");
                                for (var entry : mapping.registryMap().entrySet()) {
                                    Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(entry.getKey());
                                    if (registry != null) remap(registry, entry.getValue());
                                }
                                receivedRegistryMapping = true;
                                customConfiguration("fabric:registry/sync/complete", out -> {});
                            }
                        }
                    } catch (Exception error) { throw new RuntimeException(error); }
                    buffer.readerIndex(start);
                    return vanilla.codec().decode(buffer);
                });
            }
        };
    }

    /** Negotiate and decode the actual Fabric recipe payload that vanilla codecs discard. */
    private ProtocolInfo<ClientGamePacketListener> gameProtocol() {
        ProtocolInfo<ClientGamePacketListener> vanilla = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
        if (!modded && !fabricOnly) return vanilla;
        int[] customId = {-1};
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> {
            if (type == CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD) customId[0] = id;
        });
        return new ProtocolInfo<>() {
            public ConnectionProtocol id() { return vanilla.id(); }
            public PacketFlow flow() { return vanilla.flow(); }
            public net.minecraft.network.protocol.BundlerInfo bundlerInfo() { return vanilla.bundlerInfo(); }
            public net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Packet<? super ClientGamePacketListener>> codec() {
                return net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> vanilla.codec().encode(buffer, packet), buffer -> {
                    int start = buffer.readerIndex();
                    RegistryFriendlyByteBuf input = new RegistryFriendlyByteBuf(buffer, registries);
                    if (input.readVarInt() == customId[0]
                            && input.readIdentifier().equals(ClientboundRecipeSyncPayload.TYPE.id())) {
                        var recipes = ClientboundRecipeSyncPayload.CODEC.decode(input);
                        var ids = recipes.entries().stream().flatMap(entry -> entry.recipes().stream())
                                .map(recipe -> recipe.id().identifier().toString()).toList();
                        if (!ids.contains("minecraft:crafting_table")) throw new AssertionError("Vanilla recipes missing from Fabric sync");
                        boolean hasTool = ids.contains("effortlessbuilding:randomizer_tool");
                        if (!modded && hasTool) throw new AssertionError("Non-companion Fabric client received tool recipe");
                        recipes.entries().stream().flatMap(entry -> entry.recipes().stream())
                                .filter(recipe -> recipe.id().identifier().toString().equals("effortlessbuilding:randomizer_tool"))
                                .forEach(recipe -> {
                                    var result = recipe.value().display().getFirst().result();
                                    if (!(result instanceof net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay stack)
                                            || !BuiltInRegistries.ITEM.getKey(stack.stack().item().value()).toString()
                                                    .equals("effortlessbuilding:randomizer_tool")) {
                                        throw new AssertionError("Randomizer recipe result has an incorrect remapped item ID");
                                    }
                                });
                        if (input.isReadable()) throw new AssertionError("Unread data in Fabric recipe payload");
                        toolRecipe = hasTool;
                        recipeSyncs++;
                        System.out.println("ASSERT PASS full recipe sync: companion=" + modded + ", tool=" + hasTool + ", recipes=" + ids.size());
                        return new ClientboundCustomPayloadPacket(new DiscardedPayload(ClientboundRecipeSyncPayload.TYPE.id()));
                    }
                    buffer.readerIndex(start);
                    return vanilla.codec().decode(buffer);
                });
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void remap(Registry registry, it.unimi.dsi.fastutil.objects.Object2IntMap<Identifier> mapping) throws Exception {
        List<net.minecraft.core.Holder.Reference<?>> ordered = new ArrayList<>(Collections.nCopies(mapping.size(), null));
        for (var entry : mapping.object2IntEntrySet()) {
            ordered.set(entry.getIntValue(), (net.minecraft.core.Holder.Reference<?>) registry.get(entry.getKey()).orElseThrow());
        }
        for (Object value : registry) {
            Identifier id = registry.getKey(value);
            if (!mapping.containsKey(id)) ordered.add((net.minecraft.core.Holder.Reference<?>) registry.get(id).orElseThrow());
        }
        Field byId = net.minecraft.core.MappedRegistry.class.getDeclaredField("byId");
        byId.setAccessible(true);
        List list = (List) byId.get(registry);
        list.clear(); list.addAll(ordered);
        Field toId = net.minecraft.core.MappedRegistry.class.getDeclaredField("toId");
        toId.setAccessible(true);
        it.unimi.dsi.fastutil.objects.Reference2IntMap ids = (it.unimi.dsi.fastutil.objects.Reference2IntMap) toId.get(registry);
        ids.clear();
        for (int i = 0; i < ordered.size(); i++) ids.put(ordered.get(i).value(), i);
    }
}
