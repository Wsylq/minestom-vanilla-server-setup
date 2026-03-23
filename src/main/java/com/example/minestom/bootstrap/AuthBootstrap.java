package com.example.minestom.bootstrap;

public final class AuthBootstrap {

    private AuthBootstrap() {
    }

    public static void configure(boolean onlineMode) {
        if (!onlineMode) {
            System.out.println("Online-mode disabled. Cracked/offline clients are allowed.");
            return;
        }

        try {
            Class<?> mojangAuth = Class.forName("net.minestom.server.extras.MojangAuth");
            mojangAuth.getMethod("init").invoke(null);
            System.out.println("Online-mode enabled. Mojang auth active.");
        } catch (ClassNotFoundException ignored) {
            System.err.println("Online-mode requested but MojangAuth extra is not present on this Minestom line.");
        } catch (Exception exception) {
            System.err.println("Could not initialize Mojang auth: " + exception.getMessage());
        }
    }
}