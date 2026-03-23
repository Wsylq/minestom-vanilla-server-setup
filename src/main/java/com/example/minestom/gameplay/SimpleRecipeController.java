package com.example.minestom.gameplay;

import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.lang.reflect.Method;
import java.util.Map;

public final class SimpleRecipeController {

    private static final Map<Material, Material> LOG_TO_PLANKS = Map.of(
            Material.OAK_LOG, Material.OAK_PLANKS,
            Material.BIRCH_LOG, Material.BIRCH_PLANKS,
            Material.SPRUCE_LOG, Material.SPRUCE_PLANKS,
            Material.JUNGLE_LOG, Material.JUNGLE_PLANKS,
            Material.ACACIA_LOG, Material.ACACIA_PLANKS,
            Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS,
            Material.MANGROVE_LOG, Material.MANGROVE_PLANKS,
            Material.CHERRY_LOG, Material.CHERRY_PLANKS
    );

    private SimpleRecipeController() {
    }

    public static boolean tryCraftLogToPlanks(PlayerBlockInteractEvent event) {
        if (!event.getBlock().compare(Block.CRAFTING_TABLE)) {
            return false;
        }
        if (!isSneaking(event.getPlayer())) {
            return false;
        }

        Player player = event.getPlayer();
        Object held = mainHand(player);
        if (held == null) {
            return false;
        }

        Material heldMaterial = extractMaterial(held);
        Material output = LOG_TO_PLANKS.get(heldMaterial);
        if (output == null) {
            return false;
        }

        int amount = extractAmount(held);
        if (amount <= 0) {
            return false;
        }

        event.setCancelled(true);
        event.setBlockingItemUse(true);

        updateMainHand(player, heldMaterial, amount - 1);
        player.getInventory().addItemStack(ItemStack.of(output, 4));
        player.sendMessage("Crafted 4 " + output.name().toLowerCase() + ".");
        return true;
    }

    private static Object mainHand(Player player) {
        Object item = invokeObject(player, "getItemInMainHand");
        if (item != null) {
            return item;
        }
        return invokeObject(player, "getHeldItemMainHand");
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

    private static boolean isSneaking(Player player) {
        try {
            Object value = player.getClass().getMethod("isSneaking").invoke(player);
            return value instanceof Boolean b && b;
        } catch (Exception ignored) {
            return false;
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
}