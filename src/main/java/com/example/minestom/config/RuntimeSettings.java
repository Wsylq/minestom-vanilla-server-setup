package com.example.minestom.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record RuntimeSettings(
        String bindAddress,
        int bindPort,
        String worldPath,
        boolean onlineMode,
        String motd,
        boolean authoritativeNode,
        String authoritativeJoinAddress,
        int persistIntervalSeconds,
        boolean remoteSyncEnabled,
        String remoteSyncUrl,
        String remoteSyncToken,
        String remoteSyncServerId,
        String remoteSyncClusterId,
        String remoteSyncAuthoritativeServerId
) {

    private static final String FILE_NAME = "server.properties";

    public static RuntimeSettings load() {
        Properties fileProperties = loadOrCreateServerProperties();

        String bindAddress = firstNonBlank(
                System.getenv("SERVER_IP"),
                System.getProperty("server.address"),
                fileProperties.getProperty("server-ip", "0.0.0.0")
        );

        String rawPort = firstNonBlank(
                System.getenv("SERVER_PORT"),
                System.getenv("PORT"),
                System.getProperty("server.port"),
                fileProperties.getProperty("server-port", "25565")
        );

        int bindPort = parsePort(rawPort);
        String worldPath = firstNonBlank(
                System.getenv("WORLD_PATH"),
                System.getProperty("server.world-path"),
                fileProperties.getProperty("world-path", "")
        );
        boolean onlineMode = parseBoolean(
                firstNonBlank(
                        System.getenv("ONLINE_MODE"),
                        System.getProperty("server.online-mode"),
                        fileProperties.getProperty("online-mode", "false")
                )
        );
        String motd = firstNonBlank(
                System.getenv("MOTD"),
                System.getProperty("server.motd"),
                fileProperties.getProperty("motd", "Minestom Vanilla-like Server")
        );

        boolean authoritativeNode = parseBoolean(
                firstNonBlank(
                        System.getenv("AUTHORITATIVE_NODE"),
                        System.getProperty("server.authoritative-node"),
                        fileProperties.getProperty("authoritative-node", "true")
                )
        );

        String authoritativeJoinAddress = firstNonBlank(
                System.getenv("AUTHORITATIVE_JOIN_ADDRESS"),
                System.getProperty("server.authoritative-join-address"),
                fileProperties.getProperty("authoritative-join-address", "")
        );

        int persistIntervalSeconds = parsePositiveSeconds(
                firstNonBlank(
                        System.getenv("PERSIST_INTERVAL_SECONDS"),
                        System.getProperty("server.persist-interval-seconds"),
                        fileProperties.getProperty("persist-interval-seconds", "30")
                ),
                30
        );

        boolean remoteSyncEnabled = parseBoolean(
                firstNonBlank(
                        System.getenv("REMOTE_SYNC_ENABLED"),
                        System.getProperty("server.remote-sync-enabled"),
                        fileProperties.getProperty("remote-sync-enabled", "false")
                )
        );

        String remoteSyncUrl = firstNonBlank(
                System.getenv("REMOTE_SYNC_URL"),
                System.getProperty("server.remote-sync-url"),
                fileProperties.getProperty("remote-sync-url", "")
        );

        String remoteSyncToken = firstNonBlank(
                System.getenv("REMOTE_SYNC_TOKEN"),
                System.getProperty("server.remote-sync-token"),
                fileProperties.getProperty("remote-sync-token", "")
        );

        String remoteSyncServerId = firstNonBlank(
                System.getenv("REMOTE_SYNC_SERVER_ID"),
                System.getProperty("server.remote-sync-server-id"),
                fileProperties.getProperty("remote-sync-server-id", "default")
        );

        String remoteSyncClusterId = firstNonBlank(
                System.getenv("REMOTE_SYNC_CLUSTER_ID"),
                System.getProperty("server.remote-sync-cluster-id"),
                fileProperties.getProperty("remote-sync-cluster-id", "default")
        );

        String remoteSyncAuthoritativeServerId = firstNonBlank(
                System.getenv("REMOTE_SYNC_AUTHORITATIVE_SERVER_ID"),
                System.getProperty("server.remote-sync-authoritative-server-id"),
                fileProperties.getProperty("remote-sync-authoritative-server-id", "authoritative")
        );

        return new RuntimeSettings(
                bindAddress,
                bindPort,
                worldPath,
                onlineMode,
                motd,
                authoritativeNode,
                authoritativeJoinAddress,
                persistIntervalSeconds,
                remoteSyncEnabled,
                remoteSyncUrl,
                remoteSyncToken,
                remoteSyncServerId,
                remoteSyncClusterId,
                remoteSyncAuthoritativeServerId
        );
    }

    private static Properties loadOrCreateServerProperties() {
        Path path = Path.of(FILE_NAME);
        Properties defaults = defaultProperties();

        if (!Files.exists(path)) {
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                defaults.store(outputStream, "Minestom server properties");
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create " + FILE_NAME, exception);
            }
            return defaults;
        }

        Properties loaded = new Properties();
        loaded.putAll(defaults);
        try (InputStream inputStream = Files.newInputStream(path)) {
            loaded.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + FILE_NAME, exception);
        }
        return loaded;
    }

    private static Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("server-ip", "0.0.0.0");
        properties.setProperty("server-port", "25565");
        properties.setProperty("online-mode", "false");
        properties.setProperty("motd", "Minestom Vanilla-like Server");
        properties.setProperty("authoritative-node", "true");
        properties.setProperty("authoritative-join-address", "");
        properties.setProperty("persist-interval-seconds", "30");
        properties.setProperty("world-path", "");
        properties.setProperty("remote-sync-enabled", "false");
        properties.setProperty("remote-sync-url", "");
        properties.setProperty("remote-sync-token", "");
        properties.setProperty("remote-sync-server-id", "default");
        properties.setProperty("remote-sync-cluster-id", "default");
        properties.setProperty("remote-sync-authoritative-server-id", "authoritative");
        return properties;
    }

    private static int parsePositiveSeconds(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                return fallback;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parsePort(String rawPort) {
        try {
            int port = Integer.parseInt(rawPort.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid port value: " + rawPort);
        }
    }

    private static boolean parseBoolean(String raw) {
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}