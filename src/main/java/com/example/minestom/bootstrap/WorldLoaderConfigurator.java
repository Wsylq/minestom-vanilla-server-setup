package com.example.minestom.bootstrap;

import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.anvil.AnvilLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WorldLoaderConfigurator {

    private WorldLoaderConfigurator() {
    }

    public static void attach(InstanceContainer instance, String worldPath) {
        if (worldPath == null || worldPath.isBlank()) {
            return;
        }

        Path path = Path.of(worldPath);
        if (!Files.exists(path)) {
            System.err.println("WORLD_PATH was provided, but it does not exist: " + worldPath);
            return;
        }

        Path regionPath = path.resolve("region");
        if (!Files.isDirectory(regionPath)) {
            System.err.println("WORLD_PATH has no region folder. Falling back to generated world: " + worldPath);
            return;
        }

        // If a chunk does not exist in the anvil world, the configured generator is still used.
        instance.setChunkLoader(new AnvilLoader(path));
        System.out.println("Loading world from anvil path: " + worldPath);
    }
}