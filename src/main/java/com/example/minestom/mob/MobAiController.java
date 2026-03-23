package com.example.minestom.mob;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MobAiController {

    private static final Set<EntityType> NEUTRAL_MOBS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> NEUTRAL_ANGER_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_PATHFIND_AT = new ConcurrentHashMap<>();
    private static volatile Method setPathToMethod;

    private MobAiController() {
    }

    public static void registerNeutral(EntityType type) {
        if (type != null) {
            NEUTRAL_MOBS.add(type);
        }
    }

    public static void makeNeutralAngry(Entity entity) {
        if (entity == null) {
            return;
        }
        NEUTRAL_ANGER_UNTIL.put(entity.getUuid(), System.currentTimeMillis() + 45_000L);
    }

    public static void tickAi(InstanceContainer instance, Set<EntityType> chaserMobs) {
        for (Entity entity : instance.getEntities()) {
            if (!(entity instanceof EntityCreature creature)) {
                continue;
            }
            if (!chaserMobs.contains(creature.getEntityType())) {
                continue;
            }

            Player nearest = findNearestPlayer(instance, creature, 24);
            if (nearest == null) {
                continue;
            }

            if (NEUTRAL_MOBS.contains(creature.getEntityType())) {
                long angryUntil = NEUTRAL_ANGER_UNTIL.getOrDefault(creature.getUuid(), 0L);
                int distX = nearest.getPosition().blockX() - creature.getPosition().blockX();
                int distZ = nearest.getPosition().blockZ() - creature.getPosition().blockZ();
                boolean proximityAggro = distX * distX + distZ * distZ <= 6 * 6;
                boolean angerAggro = angryUntil > System.currentTimeMillis();
                if (!proximityAggro && !angerAggro) {
                    continue;
                }
            }

            if (shouldRefreshPath(creature)) {
                if (tryNavigatorPath(creature, nearest)) {
                    continue;
                }
            }

            // Keep fallback movement smooth and low-speed if navigator API is unavailable.
            Vec direction = nearest.getPosition().asVec().sub(creature.getPosition().asVec());
            if (Math.abs(direction.x()) < 0.4 && Math.abs(direction.z()) < 0.4) {
                continue;
            }
            Vec horizontal = new Vec(direction.x(), 0, direction.z()).normalize();
            creature.setVelocity(horizontal.mul(0.8));
        }
    }

    private static boolean shouldRefreshPath(EntityCreature creature) {
        long now = System.currentTimeMillis();
        long next = NEXT_PATHFIND_AT.getOrDefault(creature.getUuid(), 0L);
        if (now < next) {
            return false;
        }
        NEXT_PATHFIND_AT.put(creature.getUuid(), now + ThreadLocalRandom.current().nextLong(250, 450));
        return true;
    }

    private static boolean tryNavigatorPath(EntityCreature creature, Player target) {
        try {
            Object navigator = creature.getClass().getMethod("getNavigator").invoke(creature);
            if (navigator == null) {
                return false;
            }

            Method pathTo = setPathToMethod;
            if (pathTo == null) {
                for (Method method : navigator.getClass().getMethods()) {
                    if (method.getName().equals("setPathTo") && method.getParameterCount() == 1) {
                        pathTo = method;
                        setPathToMethod = method;
                        break;
                    }
                }
            }

            if (pathTo == null) {
                return false;
            }

            pathTo.invoke(navigator, target.getPosition());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Player findNearestPlayer(InstanceContainer instance, Entity creature, int rangeBlocks) {
        Player nearest = null;
        int bestDistanceSq = rangeBlocks * rangeBlocks;
        int cx = creature.getPosition().blockX();
        int cz = creature.getPosition().blockZ();

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getInstance() != instance || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            int dx = player.getPosition().blockX() - cx;
            int dz = player.getPosition().blockZ() - cz;
            int distSq = dx * dx + dz * dz;
            if (distSq > bestDistanceSq) {
                continue;
            }

            bestDistanceSq = distSq;
            nearest = player;
        }
        return nearest;
    }
}