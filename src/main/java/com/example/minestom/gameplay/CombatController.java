package com.example.minestom.gameplay;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.Material;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatController {

    private static final Map<UUID, Long> PLAYER_ATTACK_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<String, Long> MOB_ATTACK_COOLDOWN = new ConcurrentHashMap<>();

    private CombatController() {
    }

    public static void handlePlayerAttack(EntityAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getTarget() instanceof Entity target)) {
            return;
        }
        if (!(target instanceof EntityCreature creature)) {
            return;
        }

        long now = System.currentTimeMillis();
        long next = PLAYER_ATTACK_COOLDOWN.getOrDefault(player.getUuid(), 0L);
        if (now < next) {
            return;
        }

        float damage = attackDamage(player);
        if (isCritical(player)) {
            damage *= 1.5f;
        }

        if (damageEntity(creature, damage)) {
            applyKnockback(player, creature, 6.0);
        }
        PLAYER_ATTACK_COOLDOWN.put(player.getUuid(), now + 550L);
    }

    public static void tickMobMeleeDamage(InstanceContainer instance, Set<EntityType> hostileMobs) {
        for (Entity entity : instance.getEntities()) {
            if (!(entity instanceof EntityCreature mob)) {
                continue;
            }
            if (!hostileMobs.contains(mob.getEntityType())) {
                continue;
            }

            Player target = findNearestPlayer(instance, mob, 2);
            if (target == null || target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            String key = mob.getEntityId() + ":" + target.getUuid();
            long now = System.currentTimeMillis();
            if (now < MOB_ATTACK_COOLDOWN.getOrDefault(key, 0L)) {
                continue;
            }

            float rawDamage = mobAttackDamage(mob.getEntityType());
            float reduced = applyArmorReduction(target, rawDamage);
            damagePlayer(target, reduced);
            MOB_ATTACK_COOLDOWN.put(key, now + 900L);
        }
    }

    public static void damagePlayer(Player player, float damage) {
        if (damage <= 0) {
            return;
        }

        Float health = invokeFloatGetter(player, "getHealth");
        if (health == null) {
            return;
        }

        float next = health - damage;
        if (next <= 0.0f) {
            invokeFloatSetter(player, "setHealth", 20.0f);
            player.teleport(player.getRespawnPoint());
            player.sendMessage("You died.");
            return;
        }
        invokeFloatSetter(player, "setHealth", next);
    }

    private static boolean damageEntity(Entity target, float damage) {
        Float health = invokeFloatGetter(target, "getHealth");
        if (health == null) {
            return false;
        }

        float next = health - damage;
        if (next <= 0.0f) {
            invokeFloatSetter(target, "setHealth", 0.0f);
        } else {
            invokeFloatSetter(target, "setHealth", next);
        }
        return true;
    }

    private static void applyKnockback(Player attacker, Entity target, double strength) {
        Vec direction = target.getPosition().asVec().sub(attacker.getPosition().asVec());
        if (Math.abs(direction.x()) < 0.01 && Math.abs(direction.z()) < 0.01) {
            return;
        }
        Vec horizontal = new Vec(direction.x(), 0, direction.z()).normalize();
        target.setVelocity(new Vec(horizontal.x() * strength, 3.2, horizontal.z() * strength));
    }

    private static float attackDamage(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return 1000.0f;
        }

        Material material = extractMaterial(mainHand(player));
        if (material == null) {
            return 1.0f;
        }

        if (material == Material.NETHERITE_SWORD) return 8.0f;
        if (material == Material.DIAMOND_SWORD) return 7.0f;
        if (material == Material.IRON_SWORD) return 6.0f;
        if (material == Material.STONE_SWORD) return 5.0f;
        if (material == Material.WOODEN_SWORD || material == Material.GOLDEN_SWORD) return 4.0f;

        if (material == Material.NETHERITE_AXE) return 10.0f;
        if (material == Material.DIAMOND_AXE) return 9.0f;
        if (material == Material.IRON_AXE) return 9.0f;
        if (material == Material.STONE_AXE) return 9.0f;
        if (material == Material.WOODEN_AXE || material == Material.GOLDEN_AXE) return 7.0f;

        return 1.0f;
    }

    private static boolean isCritical(Player player) {
        Boolean onGround = invokeBoolean(player, "isOnGround");
        return onGround != null && !onGround;
    }

    private static float applyArmorReduction(Player player, float damage) {
        int armorPoints = armorPoints(player);
        float reduced = damage * (1.0f - Math.min(20, armorPoints) / 25.0f);
        return Math.max(0.5f, reduced);
    }

    private static int armorPoints(Player player) {
        int points = 0;
        points += materialArmorPoints(extractMaterial(invokeObject(player, "getHelmet")));
        points += materialArmorPoints(extractMaterial(invokeObject(player, "getChestplate")));
        points += materialArmorPoints(extractMaterial(invokeObject(player, "getLeggings")));
        points += materialArmorPoints(extractMaterial(invokeObject(player, "getBoots")));
        return points;
    }

    private static int materialArmorPoints(Material material) {
        if (material == null) return 0;

        String name = material.name();
        if (name.endsWith("HELMET")) {
            if (name.startsWith("NETHERITE") || name.startsWith("DIAMOND")) return 3;
            if (name.startsWith("IRON") || name.startsWith("CHAINMAIL")) return 2;
            if (name.startsWith("GOLDEN")) return 2;
            if (name.startsWith("LEATHER")) return 1;
        }
        if (name.endsWith("CHESTPLATE")) {
            if (name.startsWith("NETHERITE") || name.startsWith("DIAMOND")) return 8;
            if (name.startsWith("IRON") || name.startsWith("CHAINMAIL")) return 6;
            if (name.startsWith("GOLDEN")) return 5;
            if (name.startsWith("LEATHER")) return 3;
        }
        if (name.endsWith("LEGGINGS")) {
            if (name.startsWith("NETHERITE") || name.startsWith("DIAMOND")) return 6;
            if (name.startsWith("IRON") || name.startsWith("CHAINMAIL")) return 5;
            if (name.startsWith("GOLDEN")) return 3;
            if (name.startsWith("LEATHER")) return 2;
        }
        if (name.endsWith("BOOTS")) {
            if (name.startsWith("NETHERITE") || name.startsWith("DIAMOND")) return 3;
            if (name.startsWith("IRON") || name.startsWith("CHAINMAIL")) return 2;
            if (name.startsWith("GOLDEN")) return 1;
            if (name.startsWith("LEATHER")) return 1;
        }
        return 0;
    }

    private static float mobAttackDamage(EntityType type) {
        if (type == EntityType.ZOMBIE) return 3.0f;
        if (type == EntityType.SKELETON) return 2.0f;
        if (type == EntityType.SPIDER) return 2.0f;
        if (type == EntityType.CREEPER) return 5.0f;
        if (type == EntityType.ENDERMAN) return 4.0f;
        if (type == EntityType.VINDICATOR) return 6.0f;
        if (type == EntityType.RAVAGER) return 8.0f;
        if (type == EntityType.WITHER) return 10.0f;
        if (type == EntityType.WARDEN) return 12.0f;
        return 2.0f;
    }

    private static Player findNearestPlayer(InstanceContainer instance, Entity source, int radius) {
        Player nearest = null;
        int bestSq = radius * radius;
        int sx = source.getPosition().blockX();
        int sz = source.getPosition().blockZ();

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getInstance() != instance) {
                continue;
            }
            int dx = player.getPosition().blockX() - sx;
            int dz = player.getPosition().blockZ() - sz;
            int sq = dx * dx + dz * dz;
            if (sq <= bestSq) {
                bestSq = sq;
                nearest = player;
            }
        }
        return nearest;
    }

    private static Object mainHand(Player player) {
        Object item = invokeObject(player, "getItemInMainHand");
        if (item != null) {
            return item;
        }
        return invokeObject(player, "getHeldItemMainHand");
    }

    private static Material extractMaterial(Object stack) {
        if (stack == null) {
            return null;
        }
        try {
            Object material = stack.getClass().getMethod("material").invoke(stack);
            return material instanceof Material m ? m : null;
        } catch (Exception ignored) {
            try {
                Object material = stack.getClass().getMethod("getMaterial").invoke(stack);
                return material instanceof Material m ? m : null;
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static Object invokeObject(Object instance, String method) {
        try {
            Method m = instance.getClass().getMethod(method);
            return m.invoke(instance);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float invokeFloatGetter(Object instance, String method) {
        try {
            Object value = instance.getClass().getMethod(method).invoke(instance);
            return value instanceof Float f ? f : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean invokeBoolean(Object instance, String method) {
        try {
            Object value = instance.getClass().getMethod(method).invoke(instance);
            return value instanceof Boolean b ? b : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void invokeFloatSetter(Object instance, String method, float value) {
        try {
            instance.getClass().getMethod(method, float.class).invoke(instance, value);
        } catch (Exception ignored) {
        }
    }
}