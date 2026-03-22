package com.example.minestom.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

public final class OverworldGenerator implements Generator {

    private static final int MIN_WORLD_Y = -64;
    private static final int SEA_LEVEL = 62;
    private static final int BASE_HEIGHT = 68;

    @Override
    public void generate(GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point size = unit.size();
        var modifier = unit.modifier();

        for (int x = 0; x < size.blockX(); x++) {
            int worldX = start.blockX() + x;

            for (int z = 0; z < size.blockZ(); z++) {
                int worldZ = start.blockZ() + z;
                int top = computeHeight(worldX, worldZ);

                int minY = start.blockY();
                int maxY = start.blockY() + size.blockY() - 1;

                int terrainMax = Math.min(top, maxY);
                for (int y = minY; y <= terrainMax; y++) {
                    modifier.setBlock(new Vec(worldX, y, worldZ), selectBlock(y, top));
                }

                if (top < SEA_LEVEL) {
                    int waterStart = Math.max(top + 1, minY);
                    int waterEnd = Math.min(SEA_LEVEL, maxY);
                    for (int y = waterStart; y <= waterEnd; y++) {
                        modifier.setBlock(new Vec(worldX, y, worldZ), Block.WATER);
                    }
                }
            }
        }
    }

    private static int computeHeight(int x, int z) {
        double continental = Math.sin(x * 0.0125) * 9 + Math.cos(z * 0.0125) * 9;
        double erosion = Math.sin((x + z) * 0.035) * 3.0;
        double ridges = Math.cos((x - z) * 0.0225) * 4.0;
        return BASE_HEIGHT + (int) Math.round(continental + erosion + ridges);
    }

    private static Block selectBlock(int y, int surfaceY) {
        if (y <= MIN_WORLD_Y) {
            return Block.BEDROCK;
        }
        if (y == surfaceY) {
            return surfaceY <= SEA_LEVEL ? Block.SAND : Block.GRASS_BLOCK;
        }
        if (y >= surfaceY - 3) {
            return surfaceY <= SEA_LEVEL ? Block.SAND : Block.DIRT;
        }
        return Block.STONE;
    }
}