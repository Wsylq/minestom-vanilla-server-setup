package com.example.minestom.gameplay;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMechanicsController {

    private static final Map<UUID, Pos> LAST_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> EXHAUSTION = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> UNDERWATER_TICKS = new ConcurrentHashMap<>();
    private static int tickCounter;

    private PlayerMechanicsController() {
    }

    public static void tickPlayers() {
        tickCounter++;

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            UUID id = player.getUuid();
            Pos current = player.getPosition();
            Pos previous = LAST_POSITIONS.put(id, current);
            if (previous == null) {
                continue;
            }

            double dx = current.x() - previous.x();
            double dz = current.z() - previous.z();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            boolean sprinting = invokeBoolean(player, "isSprinting");
            boolean sneaking = invokeBoolean(player, "isSneaking");

            if (horizontalDistance > 0.001) {
                double exhaustionGain = 0.0;
                if (sprinting) {
                    exhaustionGain += horizontalDistance * 0.16;
                } else if (sneaking) {
                    exhaustionGain += horizontalDistance * 0.02;
                } else {
                    exhaustionGain += horizontalDistance * 0.04;
                }
                EXHAUSTION.merge(id, exhaustionGain, Double::sum);
            }

            handleFoodAndStarvation(player, id);
            handleSwimmingAndDrowning(player, id);
        }
    }

    private static void handleFoodAndStarvation(Player player, UUID id) {
        Integer food = invokeIntGetter(player, "getFood");
        if (food == null) {
            food = invokeIntGetter(player, "getFoodLevel");
        }
        if (food == null) {
            return;
        }

        double exhaustion = EXHAUSTION.getOrDefault(id, 0.0);
        while (exhaustion >= 4.0 && food > 0) {
            exhaustion -= 4.0;
            food -= 1;
        }
        EXHAUSTION.put(id, exhaustion);

        if (food <= 0 && tickCounter % 40 == 0) {
            damagePlayer(player, 1.0f);
        }

        invokeIntSetter(player, "setFood", food);
        invokeIntSetter(player, "setFoodLevel", food);
    }

    private static void handleSwimmingAndDrowning(Player player, UUID id) {
        if (player.getInstance() == null) {
            return;
        }

        Pos position = player.getPosition();
        int x = position.blockX();
        int y = position.blockY();
        int z = position.blockZ();

        Block feet = player.getInstance().getBlock(x, y, z);
        Block eye = player.getInstance().getBlock(x, y + 1, z);
        boolean underwater = (feet.compare(Block.WATER) || feet.compare(Block.KELP) || feet.compare(Block.SEAGRASS))
                && (eye.compare(Block.WATER) || eye.compare(Block.KELP) || eye.compare(Block.SEAGRASS));

        if (!underwater) {
            UNDERWATER_TICKS.remove(id);
            return;
        }

        int ticks = UNDERWATER_TICKS.merge(id, 1, Integer::sum);
        if (ticks > 200 && tickCounter % 20 == 0) {
            damagePlayer(player, 1.0f);
        }
    }

    private static void damagePlayer(Player player, float damage) {
        Float health = invokeFloatGetter(player, "getHealth");
        if (health == null) {
            return;
        }
        float next = Math.max(0.0f, health - damage);
        invokeFloatSetter(player, "setHealth", next);
    }

    private static boolean invokeBoolean(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            Object value = method.invoke(instance);
            return value instanceof Boolean bool && bool;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Integer invokeIntGetter(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            Object value = method.invoke(instance);
            return value instanceof Integer integer ? integer : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float invokeFloatGetter(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            Object value = method.invoke(instance);
            return value instanceof Float floatValue ? floatValue : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void invokeIntSetter(Object instance, String methodName, int value) {
        try {
            Method method = instance.getClass().getMethod(methodName, int.class);
            method.invoke(instance, value);
        } catch (Exception ignored) {
        }
    }

    private static void invokeFloatSetter(Object instance, String methodName, float value) {
        try {
            Method method = instance.getClass().getMethod(methodName, float.class);
            method.invoke(instance, value);
        } catch (Exception ignored) {
        }
    }
}