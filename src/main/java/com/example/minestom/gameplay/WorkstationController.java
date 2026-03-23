package com.example.minestom.gameplay;

import net.kyori.adventure.text.Component;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkstationController {

    private static final Map<String, Inventory> BLOCK_INVENTORIES = new ConcurrentHashMap<>();

    private WorkstationController() {
    }

    public static boolean handleInteraction(PlayerBlockInteractEvent event) {
        Block block = event.getBlock();

        if (block.compare(Block.CRAFTING_TABLE)) {
            return open(event, "crafting", "CRAFTING", "WORKBENCH", "Crafting");
        }
        if (block.compare(Block.FURNACE)) {
            return open(event, "furnace", "FURNACE", "Furnace");
        }
        if (block.compare(Block.BLAST_FURNACE)) {
            return open(event, "blast_furnace", "BLAST_FURNACE", "FURNACE", "Blast Furnace");
        }
        if (block.compare(Block.SMOKER)) {
            return open(event, "smoker", "SMOKER", "FURNACE", "Smoker");
        }
        if (block.compare(Block.BREWING_STAND)) {
            return open(event, "brewing", "BREWING_STAND", "Brewing Stand");
        }
        if (block.compare(Block.STONECUTTER)) {
            return open(event, "stonecutter", "STONECUTTER", "Stonecutter");
        }
        if (block.compare(Block.SMITHING_TABLE)) {
            return open(event, "smithing", "SMITHING", "Smithing");
        }
        if (block.compare(Block.ANVIL) || block.compare(Block.CHIPPED_ANVIL) || block.compare(Block.DAMAGED_ANVIL)) {
            return open(event, "anvil", "ANVIL", "Anvil");
        }
        if (block.compare(Block.GRINDSTONE)) {
            return open(event, "grindstone", "GRINDSTONE", "Grindstone");
        }
        if (block.compare(Block.CARTOGRAPHY_TABLE)) {
            return open(event, "cartography", "CARTOGRAPHY", "Cartography Table");
        }
        if (block.compare(Block.LOOM)) {
            return open(event, "loom", "LOOM", "Loom");
        }

        return false;
    }

    private static boolean open(PlayerBlockInteractEvent event, String group, String typeName, String title) {
        return open(event, group, new String[] {typeName}, title);
    }

    private static boolean open(PlayerBlockInteractEvent event, String group, String typeA, String typeB, String title) {
        return open(event, group, new String[] {typeA, typeB}, title);
    }

    private static boolean open(PlayerBlockInteractEvent event, String group, String[] typeNames, String title) {
        event.setCancelled(true);
        event.setBlockingItemUse(true);

        String key = group + ":" + event.getBlockPosition().blockX() + ":" + event.getBlockPosition().blockY() + ":" + event.getBlockPosition().blockZ();
        Inventory inventory = BLOCK_INVENTORIES.computeIfAbsent(key, ignored ->
                new Inventory(resolveType(typeNames), Component.text(title))
        );
        event.getPlayer().openInventory(inventory);
        return true;
    }

    private static InventoryType resolveType(String[] names) {
        for (String name : names) {
            try {
                return InventoryType.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return InventoryType.CHEST_3_ROW;
    }
}