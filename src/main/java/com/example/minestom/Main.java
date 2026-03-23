package com.example.minestom;

import com.example.minestom.command.GamemodeCommand;
import com.example.minestom.command.GameruleCommand;
import com.example.minestom.command.DimensionCommand;
import com.example.minestom.command.SpawnCommand;
import com.example.minestom.command.TimeCommand;
import com.example.minestom.command.WeatherCommand;
import com.example.minestom.command.WorldBorderCommand;
import com.example.minestom.bootstrap.AuthBootstrap;
import com.example.minestom.bootstrap.WorldLoaderConfigurator;
import com.example.minestom.config.RuntimeSettings;
import com.example.minestom.config.ServerState;
import com.example.minestom.gameplay.BlockDropTable;
import com.example.minestom.gameplay.CombatController;
import com.example.minestom.gameplay.FoodController;
import com.example.minestom.gameplay.PlayerMechanicsController;
import com.example.minestom.gameplay.RecipeProgressionController;
import com.example.minestom.gameplay.SimpleRecipeController;
import com.example.minestom.gameplay.WorkstationController;
import com.example.minestom.mob.MobAiController;
import com.example.minestom.persistence.RemoteSyncClient;
import com.example.minestom.world.EndGenerator;
import com.example.minestom.world.NetherGenerator;
import com.example.minestom.world.OverworldGenerator;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class Main {

    private static final Pos SPAWN_POSITION = new Pos(0.5, 90, 0.5);
    private static final Map<String, Inventory> STORAGE_INVENTORIES = new ConcurrentHashMap<>();
    private static final ServerState SERVER_STATE = new ServerState();
    private static final RuntimeSettings RUNTIME = RuntimeSettings.load();
    private static final Set<EntityType> MANAGED_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> HOSTILE_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> PASSIVE_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> NETHER_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> END_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> RAID_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> BOSS_MOBS = ConcurrentHashMap.newKeySet();
    private static final Set<EntityType> CHASER_MOBS = ConcurrentHashMap.newKeySet();

    private static InstanceContainer OVERWORLD_INSTANCE;
    private static InstanceContainer NETHER_INSTANCE;
    private static InstanceContainer END_INSTANCE;

    private Main() {
    }

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        AuthBootstrap.configure(RUNTIME.onlineMode());
        RemoteSyncClient.initialize(RUNTIME, SERVER_STATE);
        bootstrapMobRegistry();

        InstanceContainer overworld = MinecraftServer.getInstanceManager().createInstanceContainer();
        InstanceContainer nether = MinecraftServer.getInstanceManager().createInstanceContainer();
        InstanceContainer end = MinecraftServer.getInstanceManager().createInstanceContainer();

        OVERWORLD_INSTANCE = overworld;
        NETHER_INSTANCE = nether;
        END_INSTANCE = end;

        overworld.setGenerator(new OverworldGenerator());
        nether.setGenerator(new NetherGenerator());
        end.setGenerator(new EndGenerator());

        overworld.setTime(SERVER_STATE.getWorldTime());
        nether.setTime(18000);
        end.setTime(6000);

        WorldLoaderConfigurator.attach(overworld, RUNTIME.worldPath());
        // Preload spawn chunk so first join does not see an empty/black world while chunk tasks warm up.
        overworld.loadChunk(0, 0);
        nether.loadChunk(0, 0);
        end.loadChunk(0, 0);

        registerBlockHandlers();

        registerEvents(overworld);
        registerCommands(overworld, nether, end);
        registerSchedulers(overworld, nether, end);

        minecraftServer.start(RUNTIME.bindAddress(), RUNTIME.bindPort());
    }

    private static void registerSchedulers(InstanceContainer overworld, InstanceContainer nether, InstanceContainer end) {
        // High-frequency gameplay ticks for smooth AI movement and player mechanics.
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            try {
                tickSimpleAi(overworld);
                tickSimpleAi(nether);
                tickSimpleAi(end);
                PlayerMechanicsController.tickPlayers();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return TaskSchedule.tick(2);
        });

        MinecraftServer.getSchedulerManager().submitTask(() -> {
            try {
                tickWorldLifecycle(overworld);
                enforceWorldBorder(overworld);
                tickNaturalSpawns(overworld);
                tickRaidSpawns(overworld);
                tickBossSpawns(overworld);
                tickBreeding(overworld);
                tickVillagerBehaviors(overworld);
                tickDespawn(overworld);
                tickBossAbilities(overworld);

                tickNaturalSpawns(nether);
                tickBossSpawns(nether);
                tickDespawn(nether);
                tickBossAbilities(nether);

                tickNaturalSpawns(end);
                tickBossSpawns(end);
                tickDespawn(end);
                tickBossAbilities(end);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return TaskSchedule.seconds(1);
        });

        MinecraftServer.getSchedulerManager().submitTask(() -> {
            try {
                overworld.saveChunksToStorage();
                nether.saveChunksToStorage();
                end.saveChunksToStorage();
                RemoteSyncClient.pushSnapshot(
                        SERVER_STATE,
                        List.copyOf(MinecraftServer.getConnectionManager().getOnlinePlayers())
                );
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return TaskSchedule.seconds(3);
        });
    }

    private static void registerBlockHandlers() {
        var blockManager = MinecraftServer.getBlockManager();

        // Keeps unknown block entities from data-loss warnings when loading external worlds.
        blockManager.registerHandler("minecraft:sculk_sensor", () -> BlockHandler.Dummy.get("minecraft:sculk_sensor"));
        blockManager.registerHandler("minecraft:calibrated_sculk_sensor", () -> BlockHandler.Dummy.get("minecraft:calibrated_sculk_sensor"));
    }

    private static void registerEvents(InstanceContainer overworld) {
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        events.addListener(ServerListPingEvent.class, event -> applyMotd(event, RUNTIME.motd()));

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
            RemoteSyncClient.applyPlayerState(event.getPlayer());
            event.getPlayer().sendMessage("Welcome. Vanilla-like survival baseline is active.");
        });

        // Minestom does not auto-route item entities into player inventory in vanilla style.
        events.addListener(PickupItemEvent.class, event -> {
            if (!(event.getLivingEntity() instanceof Player player)) {
                return;
            }

            ItemStack pickedStack = event.getItemEntity().getItemStack();
            boolean fullyAdded = player.getInventory().addItemStack(pickedStack);
            if (fullyAdded) {
                event.getItemEntity().remove();
                RecipeProgressionController.onPickup(player, extractMaterial(pickedStack));
            }
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

            Material dropMaterial = BlockDropTable.materialForBlock(event.getBlock());
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
            if (SimpleRecipeController.tryCraftLogToPlanks(event)) {
                return;
            }

            if (WorkstationController.handleInteraction(event)) {
                return;
            }

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

        events.addListener(EntityAttackEvent.class, event -> {
            if (event.getEntity() instanceof Player && event.getTarget() instanceof Entity target) {
                CombatController.handlePlayerAttack(event);
                tryMakeNeutralAngry(target);
            }
        });

        registerFoodUseListener(events);

        events.addListener(EntityDeathEvent.class, event -> {
            Entity dead = event.getEntity();
            if (!(dead instanceof EntityCreature creature)) {
                return;
            }
            if (!MANAGED_MOBS.contains(creature.getEntityType())) {
                return;
            }
            if (dead.getInstance() == null) {
                return;
            }

            for (Material material : lootDrops(creature.getEntityType())) {
                ItemEntity drop = new ItemEntity(ItemStack.of(material));
                drop.setPickupDelay(Duration.ofMillis(350));
                Pos position = dead.getPosition();
                drop.setInstance(dead.getInstance(), new Pos(position.x(), position.y() + 0.5, position.z()));
            }
        });
    }

    private static void applyMotd(ServerListPingEvent event, String motd) {
        Component description = Component.text(motd == null ? "Minestom Vanilla-like Server" : motd);

        // Minestom ping API shape changes between releases, so we probe common variants.
        try {
            event.getClass().getMethod("setDescription", Component.class).invoke(event, description);
            return;
        } catch (Exception ignored) {
            // Falls through to alternate API shapes.
        }

        try {
            Object responseData = event.getClass().getMethod("getResponseData").invoke(event);
            responseData.getClass().getMethod("setDescription", Component.class).invoke(responseData, description);
            return;
        } catch (Exception ignored) {
            // Falls through to alternate API shape.
        }

        try {
            Object responseData = event.getClass().getMethod("getResponseData").invoke(event);
            responseData.getClass().getMethod("description", Component.class).invoke(responseData, description);
        } catch (Exception ignored) {
            // No-op if this Minestom build exposes a different ping API.
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerFoodUseListener(GlobalEventHandler events) {
        try {
            Class<?> useItemEventClass = Class.forName("net.minestom.server.event.player.PlayerUseItemEvent");
            events.addListener((Class) useItemEventClass, event -> {
                try {
                    Object playerObj = event.getClass().getMethod("getPlayer").invoke(event);
                    if (!(playerObj instanceof Player player)) {
                        return;
                    }

                    if (FoodController.tryEatFromMainHand(player)) {
                        try {
                            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        } catch (Exception ignored) {
                            // Some versions expose non-cancellable use-item events.
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore for compatibility with API shape differences.
                }
            });
        } catch (ClassNotFoundException ignored) {
            // Older Minestom builds may not expose this event class.
        }
    }

    private static boolean isStorageBlock(Block block) {
        return block.compare(Block.CHEST)
                || block.compare(Block.TRAPPED_CHEST)
                || block.compare(Block.BARREL);
    }

    private static String blockKey(Point point) {
        return point.blockX() + ":" + point.blockY() + ":" + point.blockZ();
    }

    private static void tickWorldLifecycle(InstanceContainer instance) {
        if (SERVER_STATE.doDaylightCycle()) {
            SERVER_STATE.tickTime(20);
            instance.setTime(SERVER_STATE.getWorldTime());
        }

        if (!SERVER_STATE.doWeatherCycle()) {
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        if (SERVER_STATE.getWeather() == ServerState.Weather.CLEAR && roll < 0.0025) {
            SERVER_STATE.setWeather(ServerState.Weather.RAIN);
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage("Weather changed: rain")
            );
        } else if (SERVER_STATE.getWeather() == ServerState.Weather.RAIN && roll < 0.0015) {
            SERVER_STATE.setWeather(ServerState.Weather.THUNDER);
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage("Weather changed: thunder")
            );
        } else if (SERVER_STATE.getWeather() != ServerState.Weather.CLEAR && roll < 0.0020) {
            SERVER_STATE.setWeather(ServerState.Weather.CLEAR);
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage("Weather changed: clear")
            );
        }
    }

    private static void enforceWorldBorder(InstanceContainer instance) {
        int border = SERVER_STATE.getWorldBorderRadius();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getInstance() != instance) {
                continue;
            }

            int x = player.getPosition().blockX();
            int z = player.getPosition().blockZ();
            if (Math.abs(x) <= border && Math.abs(z) <= border) {
                continue;
            }

            int clampedX = Math.max(-border + 1, Math.min(border - 1, x));
            int clampedZ = Math.max(-border + 1, Math.min(border - 1, z));
            int safeY = findSurfaceY(instance, clampedX, clampedZ);
            if (safeY == Integer.MIN_VALUE) {
                safeY = SPAWN_POSITION.blockY();
            }

            player.teleport(new Pos(clampedX + 0.5, safeY, clampedZ + 0.5));
            player.sendMessage("You reached the world border.");
        }
    }

    private static void tickNaturalSpawns(InstanceContainer instance) {
        if (!SERVER_STATE.doMobSpawning()) {
            return;
        }

        if (MinecraftServer.getConnectionManager().getOnlinePlayers().isEmpty()) {
            return;
        }

        long hostileCount = instance.getEntities().stream()
                .map(Entity::getEntityType)
                .filter(HOSTILE_MOBS::contains)
                .count();

        long passiveCount = instance.getEntities().stream()
                .map(Entity::getEntityType)
                .filter(PASSIVE_MOBS::contains)
                .count();

        long netherCount = instance.getEntities().stream()
                .map(Entity::getEntityType)
                .filter(NETHER_MOBS::contains)
                .count();

        long endCount = instance.getEntities().stream()
                .map(Entity::getEntityType)
                .filter(END_MOBS::contains)
                .count();

        boolean night = SERVER_STATE.isNight();
        if (instance == OVERWORLD_INSTANCE && night && hostileCount >= 56) {
            return;
        }
        if (instance == OVERWORLD_INSTANCE && !night && passiveCount >= 44) {
            return;
        }
        if (instance == NETHER_INSTANCE && netherCount >= 52) {
            return;
        }
        if (instance == END_INSTANCE && endCount >= 36) {
            return;
        }

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getInstance() != instance) {
                continue;
            }

            int dx = ThreadLocalRandom.current().nextInt(-24, 25);
            int dz = ThreadLocalRandom.current().nextInt(-24, 25);
            int spawnX = player.getPosition().blockX() + dx;
            int spawnZ = player.getPosition().blockZ() + dz;
            int spawnY = findSurfaceY(instance, spawnX, spawnZ);
            if (spawnY == Integer.MIN_VALUE) {
                continue;
            }

            EntityType type = pickSpawnType(instance, night);
            if (type == null) {
                continue;
            }

            if (!canSpawnAt(
                    instance,
                    type,
                    HOSTILE_MOBS,
                    PASSIVE_MOBS,
                    NETHER_MOBS,
                    END_MOBS,
                    night,
                    spawnX,
                    spawnY,
                    spawnZ
            )) {
                continue;
            }

            EntityCreature creature = new EntityCreature(type);
            creature.setInstance(instance, new Pos(spawnX + 0.5, spawnY, spawnZ + 0.5));
            break;
        }
    }

    private static void tickRaidSpawns(InstanceContainer instance) {
        if (instance != OVERWORLD_INSTANCE || !SERVER_STATE.doMobSpawning()) {
            return;
        }
        if (SERVER_STATE.getWeather() != ServerState.Weather.THUNDER || !SERVER_STATE.isNight()) {
            return;
        }
        if (RAID_MOBS.isEmpty() || ThreadLocalRandom.current().nextDouble() > 0.015) {
            return;
        }

        List<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(player -> player.getInstance() == instance)
                .toList();
        if (players.isEmpty()) {
            return;
        }

        Player anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        int baseX = anchor.getPosition().blockX() + ThreadLocalRandom.current().nextInt(-20, 21);
        int baseZ = anchor.getPosition().blockZ() + ThreadLocalRandom.current().nextInt(-20, 21);
        int baseY = findSurfaceY(instance, baseX, baseZ);
        if (baseY == Integer.MIN_VALUE) {
            return;
        }

        int waveSize = ThreadLocalRandom.current().nextInt(3, 6);
        for (int i = 0; i < waveSize; i++) {
            EntityType type = pickRandomType(RAID_MOBS);
            if (type == null) {
                break;
            }

            int x = baseX + ThreadLocalRandom.current().nextInt(-4, 5);
            int z = baseZ + ThreadLocalRandom.current().nextInt(-4, 5);
            int y = findSurfaceY(instance, x, z);
            if (y == Integer.MIN_VALUE) {
                continue;
            }

            EntityCreature raider = new EntityCreature(type);
            raider.setInstance(instance, new Pos(x + 0.5, y, z + 0.5));
        }
        anchor.sendMessage("A raid wave is approaching.");
    }

    private static void tickBossSpawns(InstanceContainer instance) {
        if (!SERVER_STATE.doMobSpawning() || BOSS_MOBS.isEmpty()) {
            return;
        }

        if (instance == END_INSTANCE) {
            EntityType dragonType = entityType("minecraft:ender_dragon");
            if (dragonType == null || !shouldSpawnBoss(instance, dragonType, 0.0007)) {
                return;
            }
            EntityCreature dragon = new EntityCreature(dragonType);
            dragon.setInstance(instance, new Pos(0.5, 90, 0.5));
            broadcast(instance, "The Ender Dragon has appeared.");
            return;
        }

        if (instance == NETHER_INSTANCE) {
            EntityType witherType = entityType("minecraft:wither");
            if (witherType == null || !shouldSpawnBoss(instance, witherType, 0.0005)) {
                return;
            }
            Player anchor = pickAnchorPlayer(instance);
            if (anchor == null) {
                return;
            }

            EntityCreature wither = new EntityCreature(witherType);
            wither.setInstance(instance, anchor.getPosition().add(0, 2, 0));
            broadcast(instance, "A Wither has emerged in the Nether.");
            return;
        }

        EntityType wardenType = entityType("minecraft:warden");
        if (wardenType == null || !shouldSpawnBoss(instance, wardenType, 0.0003)) {
            return;
        }
        Player anchor = pickAnchorPlayer(instance);
        if (anchor == null) {
            return;
        }

        EntityCreature warden = new EntityCreature(wardenType);
        warden.setInstance(instance, anchor.getPosition().add(0, -2, 0));
        anchor.sendMessage("You hear a deep rumble nearby.");
    }

    private static boolean shouldSpawnBoss(InstanceContainer instance, EntityType type, double chancePerTick) {
        if (ThreadLocalRandom.current().nextDouble() > chancePerTick) {
            return false;
        }
        if (pickAnchorPlayer(instance) == null) {
            return false;
        }
        return instance.getEntities().stream().noneMatch(entity -> entity.getEntityType() == type);
    }

    private static void tickSimpleAi(InstanceContainer instance) {
        MobAiController.tickAi(instance, CHASER_MOBS);
        CombatController.tickMobMeleeDamage(instance, HOSTILE_MOBS);
    }

    private static void tickBreeding(InstanceContainer instance) {
        List<EntityCreature> animals = new ArrayList<>();
        for (Entity entity : instance.getEntities()) {
            if (!(entity instanceof EntityCreature creature)) {
                continue;
            }
            if (!PASSIVE_MOBS.contains(creature.getEntityType())) {
                continue;
            }
            if (creature.getEntityType() == EntityType.VILLAGER || creature.getEntityType() == EntityType.WANDERING_TRADER) {
                continue;
            }
            animals.add(creature);
        }

        for (int i = 0; i < animals.size(); i++) {
            EntityCreature first = animals.get(i);
            for (int j = i + 1; j < animals.size(); j++) {
                EntityCreature second = animals.get(j);
                if (first.getEntityType() != second.getEntityType()) {
                    continue;
                }
                int dx = first.getPosition().blockX() - second.getPosition().blockX();
                int dz = first.getPosition().blockZ() - second.getPosition().blockZ();
                if (dx * dx + dz * dz > 36) {
                    continue;
                }

                if (ThreadLocalRandom.current().nextDouble() > 0.025) {
                    continue;
                }

                EntityCreature child = new EntityCreature(first.getEntityType());
                child.setInstance(instance, first.getPosition().add(0.5, 0, 0.5));
                return;
            }
        }
    }

    private static void tickVillagerBehaviors(InstanceContainer instance) {
        boolean night = SERVER_STATE.isNight();
        for (Entity entity : instance.getEntities()) {
            if (!(entity instanceof EntityCreature villager)) {
                continue;
            }
            if (villager.getEntityType() != EntityType.VILLAGER && villager.getEntityType() != EntityType.WANDERING_TRADER) {
                continue;
            }

            if (!night) {
                if (ThreadLocalRandom.current().nextDouble() < 0.08) {
                    double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                    villager.setVelocity(new Vec(Math.cos(angle) * 1.8, 0, Math.sin(angle) * 1.8));
                }
                continue;
            }

            EntityCreature nearestHostile = null;
            int bestSq = 20 * 20;
            for (Entity maybeHostile : instance.getEntities()) {
                if (!(maybeHostile instanceof EntityCreature creature)) {
                    continue;
                }
                if (!HOSTILE_MOBS.contains(creature.getEntityType())) {
                    continue;
                }

                int dx = villager.getPosition().blockX() - creature.getPosition().blockX();
                int dz = villager.getPosition().blockZ() - creature.getPosition().blockZ();
                int distSq = dx * dx + dz * dz;
                if (distSq < bestSq) {
                    bestSq = distSq;
                    nearestHostile = creature;
                }
            }

            if (nearestHostile == null) {
                continue;
            }

            Vec away = villager.getPosition().asVec().sub(nearestHostile.getPosition().asVec());
            if (Math.abs(away.x()) > 0.001 || Math.abs(away.z()) > 0.001) {
                villager.setVelocity(new Vec(away.x(), 0, away.z()).normalize().mul(3.8));
            }
        }
    }

    private static void tickBossAbilities(InstanceContainer instance) {
        for (Entity entity : instance.getEntities()) {
            if (!(entity instanceof EntityCreature boss)) {
                continue;
            }

            if (boss.getEntityType() == EntityType.ENDER_DRAGON && ThreadLocalRandom.current().nextDouble() < 0.12) {
                EntityType helper = entityType("minecraft:enderman");
                if (helper != null) {
                    EntityCreature summoned = new EntityCreature(helper);
                    summoned.setInstance(instance, boss.getPosition().add(ThreadLocalRandom.current().nextInt(-4, 5), 0, ThreadLocalRandom.current().nextInt(-4, 5)));
                }
                continue;
            }

            if (boss.getEntityType() == EntityType.WITHER && ThreadLocalRandom.current().nextDouble() < 0.1) {
                EntityType helper = entityType("minecraft:skeleton");
                if (helper != null) {
                    EntityCreature summoned = new EntityCreature(helper);
                    summoned.setInstance(instance, boss.getPosition().add(ThreadLocalRandom.current().nextInt(-5, 6), 0, ThreadLocalRandom.current().nextInt(-5, 6)));
                }
                continue;
            }

            if (boss.getEntityType() == EntityType.WARDEN && ThreadLocalRandom.current().nextDouble() < 0.08) {
                Player nearest = pickAnchorPlayer(instance);
                if (nearest != null) {
                    nearest.sendMessage("The Warden emits a terrifying roar.");
                }
            }
        }
    }

    private static void tryMakeNeutralAngry(Entity entity) {
        Objects.requireNonNull(entity);
        try {
            MobAiController.class.getMethod("makeNeutralAngry", Entity.class).invoke(null, entity);
        } catch (Exception ignored) {
            // Older MobAiController builds do not expose neutral anger API.
        }
    }

    private static List<Material> lootDrops(EntityType type) {
        if (type == null) {
            return List.of();
        }

        if (type == EntityType.COW) return List.of(Material.BEEF, Material.LEATHER);
        if (type == EntityType.SHEEP) return List.of(Material.MUTTON, Material.WHITE_WOOL);
        if (type == EntityType.PIG) return List.of(Material.PORKCHOP);
        if (type == EntityType.CHICKEN) return List.of(Material.CHICKEN, Material.FEATHER);
        if (type == EntityType.ZOMBIE) return List.of(Material.ROTTEN_FLESH);
        if (type == EntityType.SKELETON) return List.of(Material.BONE, Material.ARROW);
        if (type == EntityType.SPIDER) return List.of(Material.STRING);
        if (type == EntityType.CREEPER) return List.of(Material.GUNPOWDER);
        if (type == EntityType.ENDERMAN) return List.of(Material.ENDER_PEARL);
        if (type == EntityType.PIGLIN || type == EntityType.ZOMBIFIED_PIGLIN) {
            return List.of(Material.GOLD_NUGGET, Material.ROTTEN_FLESH);
        }
        if (type == EntityType.HOGLIN) return List.of(Material.PORKCHOP, Material.LEATHER);
        if (type == EntityType.BLAZE) return List.of(Material.BLAZE_ROD);
        if (type == EntityType.GHAST) return List.of(Material.GHAST_TEAR, Material.GUNPOWDER);
        if (type == EntityType.PILLAGER || type == EntityType.VINDICATOR) return List.of(Material.EMERALD);
        if (type == EntityType.WITCH) return List.of(Material.REDSTONE, Material.GLOWSTONE_DUST);
        if (type == EntityType.RAVAGER) return List.of(Material.SADDLE);
        if (type == EntityType.WARDEN) return List.of(Material.SCULK_CATALYST);
        if (type == EntityType.WITHER) return List.of(Material.NETHER_STAR);
        return List.of();
    }

    private static boolean canSpawnAt(
            InstanceContainer instance,
            EntityType type,
            Set<EntityType> hostile,
            Set<EntityType> passive,
            Set<EntityType> nether,
            Set<EntityType> end,
            boolean night,
            int x,
            int y,
            int z
    ) {
        Block feet = instance.getBlock(x, y, z);
        Block body = instance.getBlock(x, y + 1, z);
        Block below = instance.getBlock(x, y - 1, z);

        if (!feet.isAir() || !body.isAir()) {
            return false;
        }
        if (below.isAir() || below.compare(Block.WATER) || below.compare(Block.LAVA)) {
            return false;
        }

        if (nether.contains(type)) {
            return below.compare(Block.NETHERRACK)
                    || below.compare(Block.BLACKSTONE)
                    || below.compare(Block.BASALT)
                    || below.compare(Block.SOUL_SOIL);
        }
        if (end.contains(type)) {
            return below.compare(Block.END_STONE) || below.compare(Block.OBSIDIAN);
        }
        if (hostile.contains(type)) {
            return night || !isSkyVisible(instance, x, y, z);
        }
        if (passive.contains(type)) {
            if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER) {
                return !night;
            }
            return !night && (below.compare(Block.GRASS_BLOCK) || below.compare(Block.DIRT));
        }
        return true;
    }

    private static boolean isSkyVisible(InstanceContainer instance, int x, int y, int z) {
        for (int scanY = y + 1; scanY <= 120; scanY++) {
            if (!instance.getBlock(x, scanY, z).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void tickDespawn(InstanceContainer instance) {
        for (Entity entity : instance.getEntities()) {
            EntityType type = entity.getEntityType();
            boolean managed = MANAGED_MOBS.contains(type);
            if (!managed) {
                continue;
            }

            boolean nearAnyPlayer = false;
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (player.getInstance() != instance) {
                    continue;
                }

                int dx = player.getPosition().blockX() - entity.getPosition().blockX();
                int dz = player.getPosition().blockZ() - entity.getPosition().blockZ();
                if (dx * dx + dz * dz <= 96 * 96) {
                    nearAnyPlayer = true;
                    break;
                }
            }

            if (!nearAnyPlayer) {
                entity.remove();
            }
        }
    }

    private static int findSurfaceY(InstanceContainer instance, int x, int z) {
        for (int y = 120; y >= -62; y--) {
            Block current = instance.getBlock(x, y, z);
            Block above = instance.getBlock(x, y + 1, z);
            if (!current.isAir() && above.isAir()) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static void registerCommands(InstanceContainer overworld, InstanceContainer nether, InstanceContainer end) {
        MinecraftServer.getCommandManager().register(new SpawnCommand());
        MinecraftServer.getCommandManager().register(new TimeCommand(overworld, SERVER_STATE));
        MinecraftServer.getCommandManager().register(new GamemodeCommand());
        MinecraftServer.getCommandManager().register(new DimensionCommand(overworld, nether, end));
        MinecraftServer.getCommandManager().register(new WeatherCommand(SERVER_STATE));
        MinecraftServer.getCommandManager().register(new GameruleCommand(SERVER_STATE));
        MinecraftServer.getCommandManager().register(new WorldBorderCommand(SERVER_STATE));
    }

    private static void bootstrapMobRegistry() {
        registerMob(PASSIVE_MOBS, "minecraft:cow");
        registerMob(PASSIVE_MOBS, "minecraft:sheep");
        registerMob(PASSIVE_MOBS, "minecraft:pig");
        registerMob(PASSIVE_MOBS, "minecraft:chicken");
        registerMob(PASSIVE_MOBS, "minecraft:villager");
        registerMob(PASSIVE_MOBS, "minecraft:wandering_trader");

        registerMob(HOSTILE_MOBS, "minecraft:zombie");
        registerMob(HOSTILE_MOBS, "minecraft:skeleton");
        registerMob(HOSTILE_MOBS, "minecraft:spider");
        registerMob(HOSTILE_MOBS, "minecraft:creeper");
        registerMob(HOSTILE_MOBS, "minecraft:enderman");

        registerMob(NETHER_MOBS, "minecraft:piglin");
        registerMob(NETHER_MOBS, "minecraft:zombified_piglin");
        registerMob(NETHER_MOBS, "minecraft:hoglin");
        registerMob(NETHER_MOBS, "minecraft:magma_cube");
        registerMob(NETHER_MOBS, "minecraft:ghast");
        registerMob(NETHER_MOBS, "minecraft:blaze");

        registerMob(END_MOBS, "minecraft:enderman");
        registerMob(END_MOBS, "minecraft:shulker");

        registerMob(RAID_MOBS, "minecraft:pillager");
        registerMob(RAID_MOBS, "minecraft:vindicator");
        registerMob(RAID_MOBS, "minecraft:witch");
        registerMob(RAID_MOBS, "minecraft:ravager");

        registerBoss("minecraft:ender_dragon");
        registerBoss("minecraft:wither");
        registerBoss("minecraft:warden");

        registerChaser("minecraft:zombie");
        registerChaser("minecraft:skeleton");
        registerChaser("minecraft:spider");
        registerChaser("minecraft:creeper");
        registerChaser("minecraft:enderman");
        registerChaser("minecraft:piglin");
        registerChaser("minecraft:hoglin");
        registerChaser("minecraft:vindicator");
        registerChaser("minecraft:ravager");
        registerChaser("minecraft:warden");
        registerChaser("minecraft:wither");

        registerNeutral("minecraft:piglin");
        registerNeutral("minecraft:enderman");
        registerNeutral("minecraft:zombified_piglin");
    }

    private static void registerMob(Set<EntityType> group, String key) {
        EntityType type = entityType(key);
        if (type == null) {
            return;
        }
        group.add(type);
        MANAGED_MOBS.add(type);
    }

    private static void registerBoss(String key) {
        EntityType type = entityType(key);
        if (type == null) {
            return;
        }
        BOSS_MOBS.add(type);
        MANAGED_MOBS.add(type);
    }

    private static void registerChaser(String key) {
        EntityType type = entityType(key);
        if (type != null) {
            CHASER_MOBS.add(type);
        }
    }

    private static void registerNeutral(String key) {
        EntityType type = entityType(key);
        MobAiController.registerNeutral(type);
    }

    private static EntityType pickSpawnType(InstanceContainer instance, boolean night) {
        if (instance == NETHER_INSTANCE) {
            return pickRandomType(NETHER_MOBS);
        }
        if (instance == END_INSTANCE) {
            return pickRandomType(END_MOBS);
        }
        if (night) {
            return pickRandomType(HOSTILE_MOBS);
        }
        return pickRandomType(PASSIVE_MOBS);
    }

    private static EntityType pickRandomType(Set<EntityType> types) {
        if (types.isEmpty()) {
            return null;
        }
        List<EntityType> pool = new ArrayList<>(types);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static EntityType entityType(String key) {
        return EntityType.fromKey(key);
    }

    private static Player pickAnchorPlayer(InstanceContainer instance) {
        List<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(player -> player.getInstance() == instance)
                .toList();
        if (players.isEmpty()) {
            return null;
        }
        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    private static void broadcast(InstanceContainer instance, String message) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getInstance() == instance) {
                player.sendMessage(message);
            }
        }
    }

    private static Material extractMaterial(ItemStack stack) {
        try {
            Object value = stack.getClass().getMethod("material").invoke(stack);
            return value instanceof Material material ? material : null;
        } catch (Exception ignored) {
            try {
                Object value = stack.getClass().getMethod("getMaterial").invoke(stack);
                return value instanceof Material material ? material : null;
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }
}