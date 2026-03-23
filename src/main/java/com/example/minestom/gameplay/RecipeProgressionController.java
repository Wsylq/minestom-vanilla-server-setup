package com.example.minestom.gameplay;

import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeProgressionController {

    private static final Map<UUID, Set<String>> UNLOCKED = new ConcurrentHashMap<>();

    private RecipeProgressionController() {
    }

    public static void onPickup(Player player, Material material) {
        if (material == null) {
            return;
        }

        unlockIfMatch(player, material, Material.OAK_LOG, "Planks");
        unlockIfMatch(player, material, Material.COBBLESTONE, "Stone Tools");
        unlockIfMatch(player, material, Material.IRON_INGOT, "Iron Gear");
        unlockIfMatch(player, material, Material.GOLD_INGOT, "Golden Gear");
        unlockIfMatch(player, material, Material.DIAMOND, "Diamond Gear");
        unlockIfMatch(player, material, Material.BLAZE_ROD, "Brewing");
        unlockIfMatch(player, material, Material.NETHERITE_SCRAP, "Netherite Smithing");
    }

    private static void unlockIfMatch(Player player, Material picked, Material trigger, String recipeGroup) {
        if (picked != trigger) {
            return;
        }

        Set<String> unlocked = UNLOCKED.computeIfAbsent(player.getUuid(), ignored -> ConcurrentHashMap.newKeySet());
        if (unlocked.add(recipeGroup)) {
            player.sendMessage("Recipe unlocked: " + recipeGroup);
        }
    }
}