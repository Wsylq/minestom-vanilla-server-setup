package com.example.minestom.gameplay;

import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;

public final class BlockDropTable {

    private BlockDropTable() {
    }

    public static Material materialForBlock(Block block) {
        if (block.compare(Block.GRASS_BLOCK)) return Material.GRASS_BLOCK;
        if (block.compare(Block.DIRT)) return Material.DIRT;
        if (block.compare(Block.STONE)) return Material.COBBLESTONE;
        if (block.compare(Block.DEEPSLATE)) return Material.COBBLED_DEEPSLATE;
        if (block.compare(Block.SAND)) return Material.SAND;
        if (block.compare(Block.COAL_ORE)) return Material.COAL;
        if (block.compare(Block.IRON_ORE)) return Material.RAW_IRON;
        if (block.compare(Block.COPPER_ORE)) return Material.RAW_COPPER;
        if (block.compare(Block.GOLD_ORE)) return Material.RAW_GOLD;
        if (block.compare(Block.REDSTONE_ORE)) return Material.REDSTONE;
        if (block.compare(Block.LAPIS_ORE)) return Material.LAPIS_LAZULI;
        if (block.compare(Block.DIAMOND_ORE)) return Material.DIAMOND;
        if (block.compare(Block.DEEPSLATE_COAL_ORE)) return Material.COAL;
        if (block.compare(Block.DEEPSLATE_IRON_ORE)) return Material.RAW_IRON;
        if (block.compare(Block.DEEPSLATE_COPPER_ORE)) return Material.RAW_COPPER;
        if (block.compare(Block.DEEPSLATE_GOLD_ORE)) return Material.RAW_GOLD;
        if (block.compare(Block.DEEPSLATE_REDSTONE_ORE)) return Material.REDSTONE;
        if (block.compare(Block.DEEPSLATE_LAPIS_ORE)) return Material.LAPIS_LAZULI;
        if (block.compare(Block.DEEPSLATE_DIAMOND_ORE)) return Material.DIAMOND;
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
}