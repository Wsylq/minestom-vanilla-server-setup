package com.example.minestom.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

public final class EndGenerator implements Generator {

    @Override
    public void generate(GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point size = unit.size();
        var modifier = unit.modifier();

        for (int x = 0; x < size.blockX(); x++) {
            int worldX = start.blockX() + x;
            for (int z = 0; z < size.blockZ(); z++) {
                int worldZ = start.blockZ() + z;

                double distance = Math.sqrt(worldX * (double) worldX + worldZ * (double) worldZ);
                int islandHeight = 52 + (int) Math.round(Math.cos(distance * 0.035) * 8);
                if (distance > 380) {
                    islandHeight = 32 + (int) Math.round(Math.cos(distance * 0.023) * 6);
                }

                int minY = start.blockY();
                int maxY = start.blockY() + size.blockY() - 1;
                int top = Math.min(islandHeight, maxY);

                for (int y = Math.max(minY, -64); y <= top; y++) {
                    if (islandDensity(worldX, y, worldZ, islandHeight) > 0.43) {
                        modifier.setBlock(new Vec(worldX, y, worldZ), Block.END_STONE);
                    }
                }
            }
        }
    }

    private static double islandDensity(int x, int y, int z, int height) {
        double vertical = 1.0 - Math.abs(y - height) / 34.0;
        double waves = Math.sin(x * 0.045) * 0.35 + Math.cos(z * 0.045) * 0.35;
        double detail = Math.sin((x + z) * 0.065 + y * 0.09) * 0.22;
        return vertical + waves + detail;
    }
}