package com.example.minestom;

import com.example.minestom.command.GamemodeCommand;
import com.example.minestom.command.SpawnCommand;
import com.example.minestom.command.TimeCommand;
import com.example.minestom.world.OverworldGenerator;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Main {

    private static final Pos SPAWN_POSITION = new Pos(0.5, 90, 0.5);
    private static final Map<String, Inventory> STORAGE_INVENTORIES = new ConcurrentHashMap<>();

    private Main() {
    }

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceContainer overworld = MinecraftServer.getInstanceManager().createInstanceContainer();
        overworld.setGenerator(new OverworldGenerator());
        configureWorldLoader(overworld);
        // Preload spawn chunk so first join does not see an empty/black world while chunk tasks warm up.
        overworld.loadChunk(0, 0);

        registerBlockHandlers();

        registerEvents(overworld);
        registerCommands(overworld);

        minecraftServer.start(resolveAddress(), resolvePort());
    }

    private static void configureWorldLoader(InstanceContainer instance) {
        String worldPath = resolveWorldPath();
        if (worldPath == null) {
            return;
        }

        Path path = Path.of(worldPath);
        if (!Files.exists(path)) {
            System.err.println("WORLD_PATH was provided, but it does not exist: " + worldPath);
            return;
        }

        Path regionPath = path.resolve("region");
        if (!Files.isDirectory(regionPath)) {
            System.err.println("WORLD_PATH has no region folder. Falling back to generated world: " + worldPath);
            return;
        }

        // If a chunk does not exist in the anvil world, the configured generator is used.
        instance.setChunkLoader(new AnvilLoader(path));
        System.out.println("Loading world from anvil path: " + worldPath);
    }

    private static void registerBlockHandlers() {
        var blockManager = MinecraftServer.getBlockManager();

        // Keeps unknown block entities from data-loss warnings when loading external worlds.
        blockManager.registerHandler("minecraft:sculk_sensor", () -> BlockHandler.Dummy.get("minecraft:sculk_sensor"));
        blockManager.registerHandler("minecraft:calibrated_sculk_sensor", () -> BlockHandler.Dummy.get("minecraft:calibrated_sculk_sensor"));
    }

    private static String resolveAddress() {
        String fromPterodactyl = System.getenv("SERVER_IP");
        if (fromPterodactyl != null && !fromPterodactyl.isBlank()) {
            return fromPterodactyl.trim();
        }

        String fromProperty = System.getProperty("server.address");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }

        return "0.0.0.0";
    }

    private static int resolvePort() {
        String portFromPterodactyl = System.getenv("SERVER_PORT");
        String portFromEnv = System.getenv("PORT");
        String portFromProperty = System.getProperty("server.port");

        String rawPort;
        if (portFromPterodactyl != null && !portFromPterodactyl.isBlank()) {
            rawPort = portFromPterodactyl;
        } else if (portFromEnv != null && !portFromEnv.isBlank()) {
            rawPort = portFromEnv;
        } else {
            rawPort = portFromProperty;
        }

        if (rawPort == null || rawPort.isBlank()) {
            return 25565;
        }

        try {
            int port = Integer.parseInt(rawPort.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid port value: " + rawPort);
        }
    }

    private static String resolveWorldPath() {
        String env = System.getenv("WORLD_PATH");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        String property = System.getProperty("server.world-path");
        if (property != null && !property.isBlank()) {
            return property.trim();
        }

        return null;
    }

    private static void registerEvents(InstanceContainer overworld) {
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(overworld);
            event.getPlayer().setRespawnPoint(SPAWN_POSITION);
        });

        events.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                return;
            }

            event.getPlayer().teleport(SPAWN_POSITION);
            event.getPlayer().setGameMode(GameMode.SURVIVAL);
            event.getPlayer().sendMessage("Welcome. This is a vanilla-like Minestom world.");
        });

        // Minestom does not auto-route item entities into player inventory in vanilla style.
        events.addListener(PickupItemEvent.class, event -> {
            if (!(event.getLivingEntity() instanceof net.minestom.server.entity.Player player)) {
                return;
            }

            ItemStack pickedStack = event.getItemStack();
            boolean fullyAdded = player.getInventory().addItemStack(pickedStack);
            event.setCancelled(!fullyAdded);
        });

        events.addListener(PlayerBlockPlaceEvent.class, event -> {
            GameMode gameMode = event.getPlayer().getGameMode();
            if (gameMode == GameMode.SPECTATOR || gameMode == GameMode.ADVENTURE) {
                event.setCancelled(true);
            }
        });

        // Basic block breaking flow so survival players can mine.
        events.addListener(PlayerBlockBreakEvent.class, event -> {
            GameMode gameMode = event.getPlayer().getGameMode();
            if (gameMode == GameMode.SPECTATOR || gameMode == GameMode.ADVENTURE) {
                event.setCancelled(true);
                return;
            }

            if (event.getBlock().compare(Block.BEDROCK)) {
                event.setCancelled(true);
                return;
            }

            if (isStorageBlock(event.getBlock())) {
                STORAGE_INVENTORIES.remove(blockKey(event.getBlockPosition()));
            }

            event.setResultBlock(Block.AIR);

            if (gameMode == GameMode.CREATIVE) {
                return;
            }

            Material dropMaterial = materialForBlock(event.getBlock());
            if (dropMaterial != null) {
                ItemEntity drop = new ItemEntity(ItemStack.of(dropMaterial));
                drop.setPickupDelay(Duration.ofMillis(350));
                drop.setVelocity(new Vec(0.0, 4.0, 0.0));
                drop.setInstance(
                        event.getPlayer().getInstance(),
                        new Pos(
                                event.getBlockPosition().blockX() + 0.5,
                                event.getBlockPosition().blockY() + 0.5,
                                event.getBlockPosition().blockZ() + 0.5
                        )
                );
            }
        });

        // Basic chest/barrel interactions. Minestom requires explicit behavior wiring.
        events.addListener(PlayerBlockInteractEvent.class, event -> {
            if (!isStorageBlock(event.getBlock())) {
                return;
            }

            event.setCancelled(true);
            event.setBlockingItemUse(true);

            String key = blockKey(event.getBlockPosition());
            Inventory inventory = STORAGE_INVENTORIES.computeIfAbsent(
                    key,
                    ignored -> new Inventory(InventoryType.CHEST_3_ROW, Component.text("Chest"))
            );
            event.getPlayer().openInventory(inventory);
        });
    }

    private static boolean isStorageBlock(Block block) {
        return block.compare(Block.CHEST)
                || block.compare(Block.TRAPPED_CHEST)
                || block.compare(Block.BARREL);
    }

    private static Material materialForBlock(Block block) {
        if (block.compare(Block.GRASS_BLOCK)) return Material.GRASS_BLOCK;
        if (block.compare(Block.DIRT)) return Material.DIRT;
        if (block.compare(Block.STONE)) return Material.COBBLESTONE;
        if (block.compare(Block.SAND)) return Material.SAND;
        if (block.compare(Block.OAK_LOG)) return Material.OAK_LOG;
        if (block.compare(Block.OAK_LEAVES)) return Material.OAK_LEAVES;
        if (block.compare(Block.SHORT_GRASS)) return Material.SHORT_GRASS;
        if (block.compare(Block.DANDELION)) return Material.DANDELION;
        if (block.compare(Block.POPPY)) return Material.POPPY;
        if (block.compare(Block.AZURE_BLUET)) return Material.AZURE_BLUET;
        if (block.compare(Block.OXEYE_DAISY)) return Material.OXEYE_DAISY;
        if (block.compare(Block.CORNFLOWER)) return Material.CORNFLOWER;
        if (block.compare(Block.COBBLESTONE)) return Material.COBBLESTONE;
        if (block.compare(Block.OAK_PLANKS)) return Material.OAK_PLANKS;
        if (block.compare(Block.CRAFTING_TABLE)) return Material.CRAFTING_TABLE;
        if (block.compare(Block.FURNACE)) return Material.FURNACE;
        if (block.compare(Block.CHEST)) return Material.CHEST;
        if (block.compare(Block.BARREL)) return Material.BARREL;
        return null;
    }

    private static String blockKey(Point point) {
        return point.blockX() + ":" + point.blockY() + ":" + point.blockZ();
    }

    private static void registerCommands(InstanceContainer overworld) {
        MinecraftServer.getCommandManager().register(new SpawnCommand());
        MinecraftServer.getCommandManager().register(new TimeCommand(overworld));
        MinecraftServer.getCommandManager().register(new GamemodeCommand());
    }
}