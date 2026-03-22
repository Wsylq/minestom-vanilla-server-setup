package com.example.minestom.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

public final class NetherGenerator implements Generator {

    private static final int FLOOR_Y = 28;
    private static final int CEILING_Y = 118;
    private static final long SEED = 95_001_731L;

    @Override
    public void generate(GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point size = unit.size();
        var modifier = unit.modifier();

        int minY = start.blockY();
        int maxY = start.blockY() + size.blockY() - 1;

        for (int x = 0; x < size.blockX(); x++) {
            int worldX = start.blockX() + x;
            for (int z = 0; z < size.blockZ(); z++) {
                int worldZ = start.blockZ() + z;

                int floor = FLOOR_Y + (int) Math.round(Math.sin(worldX * 0.03) * 6 + Math.cos(worldZ * 0.03) * 6);
                int ceiling = CEILING_Y + (int) Math.round(Math.sin((worldX + worldZ) * 0.02) * 5);

                for (int y = Math.max(minY, -64); y <= Math.min(maxY, 128); y++) {
                    Block block = Block.AIR;

                    if (y <= floor) {
                        block = y <= -63 ? Block.BEDROCK : pickNetherStone(worldX, y, worldZ);
                    } else if (y >= ceiling) {
                        block = y >= 127 ? Block.BEDROCK : Block.NETHERRACK;
                    } else if (y < 32 && noise(worldX, y, worldZ, 11) > 0.78) {
                        block = Block.LAVA;
                    }

                    if (block != Block.AIR) {
                        modifier.setBlock(new Vec(worldX, y, worldZ), block);
                    }
                }
            }
        }
    }

    private static Block pickNetherStone(int x, int y, int z) {
        double quartz = noise(x, y, z, 101);
        double glow = noise(x, y, z, 202);
        if (quartz > 0.89) {
            return Block.NETHER_QUARTZ_ORE;
        }
        if (glow > 0.93) {
            return Block.GLOWSTONE;
        }
        if (y < 20 && noise(x, y, z, 303) > 0.95) {
            return Block.ANCIENT_DEBRIS;
        }
        return Block.NETHERRACK;
    }

    private static double noise(int x, int y, int z, int salt) {
        long h = SEED;
        h ^= (long) x * 341_873_128_712L;
        h ^= (long) y * 132_897_987_541L;
        h ^= (long) z * 42_317_861L;
        h ^= (long) salt * 9_876_543L;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (h & 0x7fffffffL) / (double) Integer.MAX_VALUE;
    }
}