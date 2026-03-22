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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MobAiController {

    private static final Set<EntityType> NEUTRAL_MOBS = ConcurrentHashMap.newKeySet();
    private static volatile Method setPathToMethod;

    private MobAiController() {
    }

    public static void registerNeutral(EntityType type) {
        if (type != null) {
            NEUTRAL_MOBS.add(type);
        }
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
                int distX = nearest.getPosition().blockX() - creature.getPosition().blockX();
                int distZ = nearest.getPosition().blockZ() - creature.getPosition().blockZ();
                if (distX * distX + distZ * distZ > 8 * 8) {
                    continue;
                }
            }

            if (tryNavigatorPath(creature, nearest)) {
                continue;
            }

            // Fallback for snapshots where navigator methods changed.
            Vec direction = nearest.getPosition().asVec().sub(creature.getPosition().asVec());
            if (Math.abs(direction.x()) < 0.1 && Math.abs(direction.z()) < 0.1) {
                continue;
            }
            Vec horizontal = new Vec(direction.x(), 0, direction.z()).normalize();
            creature.setVelocity(horizontal.mul(4.0));
        }
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