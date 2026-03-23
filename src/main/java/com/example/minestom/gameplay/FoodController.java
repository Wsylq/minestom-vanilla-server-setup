package com.example.minestom.gameplay;

import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.lang.reflect.Method;
import java.util.Map;

public final class FoodController {

    private static final Map<Material, Integer> FOOD_VALUES = Map.ofEntries(
            Map.entry(Material.APPLE, 4),
            Map.entry(Material.BREAD, 5),
            Map.entry(Material.BAKED_POTATO, 5),
            Map.entry(Material.COOKED_BEEF, 8),
            Map.entry(Material.COOKED_PORKCHOP, 8),
            Map.entry(Material.COOKED_CHICKEN, 6),
            Map.entry(Material.COOKED_MUTTON, 6),
            Map.entry(Material.COOKED_RABBIT, 5),
            Map.entry(Material.COOKED_SALMON, 6),
            Map.entry(Material.COOKED_COD, 5),
            Map.entry(Material.BEEF, 3),
            Map.entry(Material.PORKCHOP, 3),
            Map.entry(Material.CHICKEN, 2),
            Map.entry(Material.MUTTON, 2),
            Map.entry(Material.CARROT, 3),
            Map.entry(Material.POTATO, 1),
            Map.entry(Material.SWEET_BERRIES, 2)
    );

    private FoodController() {
    }

    public static boolean tryEatFromMainHand(Player player) {
        Object held = mainHand(player);
        if (held == null) {
            return false;
        }

        Material material = extractMaterial(held);
        Integer foodValue = FOOD_VALUES.get(material);
        if (foodValue == null) {
            return false;
        }

        int amount = extractAmount(held);
        if (amount <= 0) {
            return false;
        }

        int currentFood = currentFood(player);
        if (currentFood >= 20) {
            return false;
        }

        int nextFood = Math.min(20, currentFood + foodValue);
        setFood(player, nextFood);

        Float health = invokeFloatGetter(player, "getHealth");
        if (health != null && health < 20f) {
            invokeFloatSetter(player, "setHealth", Math.min(20f, health + foodValue * 0.4f));
        }

        updateMainHand(player, material, amount - 1);
        return true;
    }

    private static Object mainHand(Player player) {
        Object item = invokeObject(player, "getItemInMainHand");
        if (item != null) {
            return item;
        }
        return invokeObject(player, "getHeldItemMainHand");
    }

    private static int currentFood(Player player) {
        Integer food = invokeIntGetter(player, "getFood");
        if (food == null) {
            food = invokeIntGetter(player, "getFoodLevel");
        }
        return food == null ? 20 : food;
    }

    private static void setFood(Player player, int value) {
        invokeIntSetter(player, "setFood", value);
        invokeIntSetter(player, "setFoodLevel", value);
    }

    private static void updateMainHand(Player player, Material material, int amount) {
        ItemStack next = amount <= 0 ? ItemStack.of(Material.AIR) : ItemStack.of(material, amount);

        try {
            Method method = player.getClass().getMethod("setItemInMainHand", ItemStack.class);
            method.invoke(player, next);
            return;
        } catch (Exception ignored) {
        }

        try {
            Method method = player.getClass().getMethod("setHeldItem", ItemStack.class);
            method.invoke(player, next);
        } catch (Exception ignored) {
        }
    }

    private static Material extractMaterial(Object stack) {
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

    private static int extractAmount(Object stack) {
        try {
            Object value = stack.getClass().getMethod("amount").invoke(stack);
            return value instanceof Integer integer ? integer : 0;
        } catch (Exception ignored) {
            try {
                Object value = stack.getClass().getMethod("getAmount").invoke(stack);
                return value instanceof Integer integer ? integer : 0;
            } catch (Exception ignoredAgain) {
                return 0;
            }
        }
    }

    private static Object invokeObject(Object instance, String methodName) {
        try {
            Method method = instance.getClass().getMethod(methodName);
            return method.invoke(instance);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer invokeIntGetter(Object instance, String methodName) {
        try {
            Object value = instance.getClass().getMethod(methodName).invoke(instance);
            return value instanceof Integer integer ? integer : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float invokeFloatGetter(Object instance, String methodName) {
        try {
            Object value = instance.getClass().getMethod(methodName).invoke(instance);
            return value instanceof Float floatValue ? floatValue : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void invokeIntSetter(Object instance, String methodName, int value) {
        try {
            instance.getClass().getMethod(methodName, int.class).invoke(instance, value);
        } catch (Exception ignored) {
        }
    }

    private static void invokeFloatSetter(Object instance, String methodName, float value) {
        try {
            instance.getClass().getMethod(methodName, float.class).invoke(instance, value);
        } catch (Exception ignored) {
        }
    }
}