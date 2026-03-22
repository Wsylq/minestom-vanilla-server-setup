package com.example.minestom.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.Block.Setter;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

public final class OverworldGenerator implements Generator {

    private static final int MIN_WORLD_Y = -64;
    private static final int SEA_LEVEL = 62;
    private static final int BASE_HEIGHT = 68;
    private static final long WORLD_SEED = 741_337_114L;

    @Override
    public void generate(GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point size = unit.size();
        var modifier = unit.modifier();

        int[][] heightMap = new int[size.blockX()][size.blockZ()];

        for (int x = 0; x < size.blockX(); x++) {
            int worldX = start.blockX() + x;

            for (int z = 0; z < size.blockZ(); z++) {
                int worldZ = start.blockZ() + z;
                int top = computeHeight(worldX, worldZ);
                heightMap[x][z] = top;

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

        decorateSurface(unit, start, size, heightMap);
        generateChunkStructure(unit, start, size, heightMap);
    }

    private static int computeHeight(int x, int z) {
        double continental = Math.sin(x * 0.0125) * 9 + Math.cos(z * 0.0125) * 9;
        double erosion = Math.sin((x + z) * 0.035) * 3.0;
        double ridges = Math.cos((x - z) * 0.0225) * 4.0;
        return BASE_HEIGHT + (int) Math.round(continental + erosion + ridges);
    }

    private static void decorateSurface(GenerationUnit unit, Point start, Point size, int[][] heightMap) {
        int minY = start.blockY();
        int maxY = start.blockY() + size.blockY() - 1;
        var modifier = unit.modifier();

        for (int x = 2; x < size.blockX() - 2; x++) {
            int worldX = start.blockX() + x;
            for (int z = 2; z < size.blockZ() - 2; z++) {
                int worldZ = start.blockZ() + z;
                int surfaceY = heightMap[x][z];

                if (surfaceY <= SEA_LEVEL + 1) {
                    continue;
                }

                if (rand01(worldX, worldZ, 1007) < 0.035) {
                    placeTree(modifier, worldX, surfaceY + 1, worldZ, minY, maxY);
                    continue;
                }

                if (isWithin(surfaceY + 1, minY, maxY)) {
                    if (rand01(worldX, worldZ, 2011) < 0.14) {
                        modifier.setBlock(new Vec(worldX, surfaceY + 1, worldZ), Block.SHORT_GRASS);
                    } else if (rand01(worldX, worldZ, 3037) < 0.02) {
                        modifier.setBlock(new Vec(worldX, surfaceY + 1, worldZ), pickFlower(worldX, worldZ));
                    }
                }
            }
        }
    }

    private static void generateChunkStructure(GenerationUnit unit, Point start, Point size, int[][] heightMap) {
        int chunkX = Math.floorDiv(start.blockX(), 16);
        int chunkZ = Math.floorDiv(start.blockZ(), 16);

        if (hashed(chunkX, chunkZ, 9_911) % 230 != 0) {
            return;
        }

        int centerX = start.blockX() + 8;
        int centerZ = start.blockZ() + 8;
        int surfaceY = heightMap[8][8];
        if (surfaceY <= SEA_LEVEL + 2) {
            return;
        }

        int minY = start.blockY();
        int maxY = start.blockY() + size.blockY() - 1;
        var modifier = unit.modifier();

        // Small survival hut so exploration finds useful landmarks without external libs.
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                setIfInRange(modifier, x, surfaceY + 1, z, minY, maxY, Block.COBBLESTONE);
            }
        }

        for (int y = surfaceY + 2; y <= surfaceY + 4; y++) {
            for (int x = centerX - 2; x <= centerX + 2; x++) {
                for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                    boolean border = x == centerX - 2 || x == centerX + 2 || z == centerZ - 2 || z == centerZ + 2;
                    if (border) {
                        setIfInRange(modifier, x, y, z, minY, maxY, Block.OAK_PLANKS);
                    } else {
                        setIfInRange(modifier, x, y, z, minY, maxY, Block.AIR);
                    }
                }
            }
        }

        for (int x = centerX - 3; x <= centerX + 3; x++) {
            for (int z = centerZ - 3; z <= centerZ + 3; z++) {
                setIfInRange(modifier, x, surfaceY + 5, z, minY, maxY, Block.OAK_SLAB);
            }
        }

        setIfInRange(modifier, centerX, surfaceY + 2, centerZ - 2, minY, maxY, Block.AIR);
        setIfInRange(modifier, centerX, surfaceY + 3, centerZ - 2, minY, maxY, Block.AIR);
        setIfInRange(modifier, centerX + 1, surfaceY + 2, centerZ + 1, minY, maxY, Block.CHEST);
        setIfInRange(modifier, centerX - 1, surfaceY + 2, centerZ + 1, minY, maxY, Block.CRAFTING_TABLE);
        setIfInRange(modifier, centerX, surfaceY + 2, centerZ + 1, minY, maxY, Block.FURNACE);
    }

    private static void placeTree(Setter modifier, int baseX, int baseY, int baseZ, int minY, int maxY) {
        int trunkHeight = 4 + Math.floorMod(hashed(baseX, baseZ, 451), 3);
        for (int y = 0; y < trunkHeight; y++) {
            setIfInRange(modifier, baseX, baseY + y, baseZ, minY, maxY, Block.OAK_LOG);
        }

        int crownY = baseY + trunkHeight - 1;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -2; y <= 1; y++) {
                    int distance = Math.abs(x) + Math.abs(z) + Math.abs(y);
                    if (distance > 4) {
                        continue;
                    }
                    if (Math.abs(x) == 2 && Math.abs(z) == 2 && y <= 0) {
                        continue;
                    }
                    setIfInRange(modifier, baseX + x, crownY + y, baseZ + z, minY, maxY, Block.OAK_LEAVES);
                }
            }
        }

        setIfInRange(modifier, baseX, crownY + 2, baseZ, minY, maxY, Block.OAK_LEAVES);
    }

    private static Block pickFlower(int x, int z) {
        int idx = Math.floorMod(hashed(x, z, 7_777), 5);
        return switch (idx) {
            case 0 -> Block.DANDELION;
            case 1 -> Block.POPPY;
            case 2 -> Block.AZURE_BLUET;
            case 3 -> Block.OXEYE_DAISY;
            default -> Block.CORNFLOWER;
        };
    }

    private static boolean isWithin(int y, int minY, int maxY) {
        return y >= minY && y <= maxY;
    }

    private static void setIfInRange(Setter modifier, int x, int y, int z, int minY, int maxY, Block block) {
        if (isWithin(y, minY, maxY)) {
            modifier.setBlock(new Vec(x, y, z), block);
        }
    }

    private static int hashed(int x, int z, int salt) {
        long h = WORLD_SEED;
        h ^= (long) x * 341_873_128_712L;
        h ^= (long) z * 132_897_987_541L;
        h ^= (long) salt * 42_317_861L;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h = h ^ (h >>> 33);
        return (int) h;
    }

    private static double rand01(int x, int z, int salt) {
        return (hashed(x, z, salt) & 0x7fffffff) / (double) Integer.MAX_VALUE;
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