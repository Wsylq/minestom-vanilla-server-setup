package com.example.minestom.persistence;

import com.example.minestom.config.RuntimeSettings;
import com.example.minestom.config.ServerState;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RemoteSyncClient {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static volatile boolean enabled;
    private static volatile String baseUrl;
    private static volatile String authToken;
    private static volatile String serverId;
    private static volatile String clusterId;
    private static volatile String authoritativeServerId;
    private static volatile boolean authoritativeNode;

    private static volatile long lastBlockEventId;
    private static volatile long lastChatEventId;

    private RemoteSyncClient() {
    }

    public static void initialize(RuntimeSettings settings, ServerState state) {
        enabled = settings.remoteSyncEnabled() && settings.remoteSyncUrl() != null && !settings.remoteSyncUrl().isBlank();
        if (!enabled) {
            return;
        }

        authoritativeNode = settings.authoritativeNode();
        baseUrl = trimTrailingSlash(settings.remoteSyncUrl().trim());
        authToken = settings.remoteSyncToken() == null ? "" : settings.remoteSyncToken().trim();
        serverId = blankToDefault(settings.remoteSyncServerId(), "default-node");
        clusterId = blankToDefault(settings.remoteSyncClusterId(), "default");
        authoritativeServerId = blankToDefault(settings.remoteSyncAuthoritativeServerId(), "authoritative");

        if (authoritativeNode) {
            authoritativeServerId = serverId;
        }

        pullServerState(state);
    }

    public static void pushSnapshot(ServerState state, List<Player> players) {
        if (!enabled || !authoritativeNode) {
            return;
        }

        String payload = toServerSnapshot(state, players);
        postJson("/api/server-state", payload);
    }

    public static void applyPlayerState(Player player) {
        if (!enabled) {
            return;
        }

        String playerId = resolvePlayerId(player);
        String url = baseUrl + "/api/player-state/"
                + URLEncoder.encode(authoritativeServerId, StandardCharsets.UTF_8)
                + "/"
                + URLEncoder.encode(playerId, StandardCharsets.UTF_8);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET();
            withAuth(builder);

            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return;
            }

            String body = response.body();
            Double x = parseDouble(body, "x");
            Double y = parseDouble(body, "y");
            Double z = parseDouble(body, "z");
            if (x != null && y != null && z != null) {
                float yaw = parseFloat(body, "yaw", 0f);
                float pitch = parseFloat(body, "pitch", 0f);
                player.teleport(new Pos(x, y, z, yaw, pitch));
            }

            String gameModeRaw = parseString(body, "gamemode");
            if (gameModeRaw != null) {
                try {
                    player.setGameMode(GameMode.valueOf(gameModeRaw.toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                }
            }

            Integer food = parseInteger(body, "food");
            if (food != null) {
                setInt(player, "setFood", food);
                setInt(player, "setFoodLevel", food);
            }

            Double health = parseDouble(body, "health");
            if (health != null) {
                setFloat(player, "setHealth", health.floatValue());
            }

            String inventoryBlob = parseString(body, "inventory");
            if (inventoryBlob != null && !inventoryBlob.isBlank()) {
                applyInventory(player, inventoryBlob);
            }
        } catch (Exception ignored) {
            // Remote sync is optional, so failures should not block gameplay.
        }
    }

    public static void publishBlockUpdate(String dimension, int x, int y, int z, String blockNamespace) {
        if (!enabled || !authoritativeNode) {
            return;
        }
        String safeDimension = dimension == null || dimension.isBlank() ? "overworld" : dimension;
        String safeBlock = blockNamespace == null || blockNamespace.isBlank() ? "minecraft:air" : blockNamespace;

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"clusterId\":\"").append(escape(clusterId)).append("\",");
        json.append("\"sourceServerId\":\"").append(escape(serverId)).append("\",");
        json.append("\"dimension\":\"").append(escape(safeDimension)).append("\",");
        json.append("\"x\":").append(x).append(",");
        json.append("\"y\":").append(y).append(",");
        json.append("\"z\":").append(z).append(",");
        json.append("\"block\":\"").append(escape(safeBlock)).append("\"");
        json.append("}");
        postJson("/api/block-update", json.toString());
    }

    public static void publishChat(String username, String message) {
        if (!enabled || !authoritativeNode || username == null || username.isBlank() || message == null || message.isBlank()) {
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"clusterId\":\"").append(escape(clusterId)).append("\",");
        json.append("\"sourceServerId\":\"").append(escape(serverId)).append("\",");
        json.append("\"username\":\"").append(escape(username)).append("\",");
        json.append("\"message\":\"").append(escape(message)).append("\"");
        json.append("}");
        postJson("/api/chat-message", json.toString());
    }

    public static void pollAndApplyRemoteChanges(Map<String, InstanceContainer> instancesByDimension) {
        if (!enabled || authoritativeNode || instancesByDimension == null || instancesByDimension.isEmpty()) {
            return;
        }
        pollBlockUpdates(instancesByDimension);
        pollChat();
    }

    private static void pullServerState(ServerState state) {
        String url = baseUrl + "/api/server-state/" + URLEncoder.encode(authoritativeServerId, StandardCharsets.UTF_8);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET();
            withAuth(builder);

            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return;
            }

            String body = response.body();
            Long worldTime = parseLong(body, "worldTime");
            if (worldTime != null) {
                state.setWorldTime(worldTime);
            }

            Integer worldBorder = parseInteger(body, "worldBorderRadius");
            if (worldBorder != null) {
                try {
                    state.setWorldBorderRadius(worldBorder);
                } catch (Exception ignored) {
                }
            }

            Boolean doDaylightCycle = parseBoolean(body, "doDaylightCycle");
            if (doDaylightCycle != null) {
                state.setRule("dodaylightcycle", doDaylightCycle);
            }

            Boolean doMobSpawning = parseBoolean(body, "doMobSpawning");
            if (doMobSpawning != null) {
                state.setRule("domobspawning", doMobSpawning);
            }

            Boolean doWeatherCycle = parseBoolean(body, "doWeatherCycle");
            if (doWeatherCycle != null) {
                state.setRule("doweathercycle", doWeatherCycle);
            }

            Boolean keepInventory = parseBoolean(body, "keepInventory");
            if (keepInventory != null) {
                state.setRule("keepinventory", keepInventory);
            }

            String weather = parseString(body, "weather");
            if (weather != null) {
                try {
                    state.setWeather(ServerState.Weather.valueOf(weather.toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            // Remote sync is optional, so failures should not block startup.
        }
    }

    private static void pollBlockUpdates(Map<String, InstanceContainer> instancesByDimension) {
        String url = baseUrl + "/api/block-updates/"
                + URLEncoder.encode(clusterId, StandardCharsets.UTF_8)
                + "?since_id=" + lastBlockEventId;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET();
            withAuth(builder);
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return;
            }

            String[] lines = response.body().split("\\R");
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (line.startsWith("next_since:")) {
                    lastBlockEventId = parseLongSafe(line.substring("next_since:".length()), lastBlockEventId);
                    continue;
                }
                if (!line.startsWith("event:")) {
                    continue;
                }

                String payload = line.substring("event:".length());
                String[] parts = payload.split("\\|", -1);
                if (parts.length < 7) {
                    continue;
                }

                long eventId = parseLongSafe(parts[0], -1);
                String source = parts[1];
                String dimension = parts[2];
                int x = parseIntSafe(parts[3], 0);
                int y = parseIntSafe(parts[4], 0);
                int z = parseIntSafe(parts[5], 0);
                String blockNamespace = unescapePipe(parts[6]);

                if (eventId > lastBlockEventId) {
                    lastBlockEventId = eventId;
                }
                if (serverId.equals(source)) {
                    continue;
                }

                InstanceContainer instance = instancesByDimension.get(dimension);
                if (instance == null) {
                    continue;
                }

                Block block = blockFromNamespace(blockNamespace);
                instance.setBlock(x, y, z, block == null ? Block.AIR : block);
            }
        } catch (Exception ignored) {
            // Remote sync is optional.
        }
    }

    private static void pollChat() {
        String url = baseUrl + "/api/chat-feed/"
                + URLEncoder.encode(clusterId, StandardCharsets.UTF_8)
                + "?since_id=" + lastChatEventId;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET();
            withAuth(builder);
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return;
            }

            String[] lines = response.body().split("\\R");
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                if (line.startsWith("next_since:")) {
                    lastChatEventId = parseLongSafe(line.substring("next_since:".length()), lastChatEventId);
                    continue;
                }
                if (!line.startsWith("event:")) {
                    continue;
                }

                String payload = line.substring("event:".length());
                String[] parts = payload.split("\\|", -1);
                if (parts.length < 4) {
                    continue;
                }

                long eventId = parseLongSafe(parts[0], -1);
                String source = parts[1];
                String username = unescapePipe(parts[2]);
                String message = unescapePipe(parts[3]);
                if (eventId > lastChatEventId) {
                    lastChatEventId = eventId;
                }
                if (serverId.equals(source)) {
                    continue;
                }
                String chatLine = "[Cluster] <" + username + "> " + message;
                for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    player.sendMessage(chatLine);
                }
            }
        } catch (Exception ignored) {
            // Remote sync is optional.
        }
    }

    private static Block blockFromNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return Block.AIR;
        }
        try {
            return Block.fromNamespaceId(namespace);
        } catch (Exception ignored) {
            return Block.AIR;
        }
    }

    private static void applyInventory(Player player, String inventoryBlob) {
        Object inventoryObj = invoke(player, "getInventory");
        if (inventoryObj == null) {
            return;
        }

        int size = readInt(inventoryObj, "getSize", 0);
        if (size <= 0) {
            return;
        }

        for (String itemToken : inventoryBlob.split(";")) {
            if (itemToken == null || itemToken.isBlank()) {
                continue;
            }
            String[] slotAndData = itemToken.split("=", 2);
            if (slotAndData.length != 2) {
                continue;
            }
            int slot = parseIntSafe(slotAndData[0], -1);
            if (slot < 0 || slot >= size) {
                continue;
            }

            String[] materialAndAmount = slotAndData[1].split("\\*", 2);
            if (materialAndAmount.length != 2) {
                continue;
            }
            Material material = materialFromId(materialAndAmount[0]);
            int amount = parseIntSafe(materialAndAmount[1], 0);
            if (material == null || amount <= 0) {
                continue;
            }

            try {
                ItemStack stack = ItemStack.of(material, amount);
                inventoryObj.getClass().getMethod("setItemStack", int.class, ItemStack.class).invoke(inventoryObj, slot, stack);
            } catch (Exception ignored) {
            }
        }
    }

    private static String toServerSnapshot(ServerState state, List<Player> players) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"serverId\":\"").append(escape(serverId)).append("\",");
        json.append("\"sourceServerId\":\"").append(escape(serverId)).append("\",");
        json.append("\"worldTime\":").append(state.getWorldTime()).append(",");
        json.append("\"worldBorderRadius\":").append(state.getWorldBorderRadius()).append(",");
        json.append("\"weather\":\"").append(state.getWeather().name()).append("\",");
        json.append("\"doDaylightCycle\":").append(state.doDaylightCycle()).append(",");
        json.append("\"doMobSpawning\":").append(state.doMobSpawning()).append(",");
        json.append("\"doWeatherCycle\":").append(state.doWeatherCycle()).append(",");
        json.append("\"keepInventory\":").append(state.keepInventory()).append(",");
        json.append("\"players\":[");

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{");
            json.append("\"uuid\":\"").append(escape(String.valueOf(player.getUuid()))).append("\",");
            json.append("\"username\":\"").append(escape(player.getUsername())).append("\",");
            json.append("\"x\":").append(player.getPosition().x()).append(",");
            json.append("\"y\":").append(player.getPosition().y()).append(",");
            json.append("\"z\":").append(player.getPosition().z()).append(",");
            json.append("\"yaw\":").append(player.getPosition().yaw()).append(",");
            json.append("\"pitch\":").append(player.getPosition().pitch()).append(",");
            json.append("\"gamemode\":\"").append(escape(player.getGameMode().name())).append("\",");
            json.append("\"health\":").append(readFloat(player, "getHealth", 20f)).append(",");
            json.append("\"food\":").append(readInt(player, "getFood", readInt(player, "getFoodLevel", 20))).append(",");
            json.append("\"inventory\":\"").append(escape(extractInventory(player))).append("\"");
            json.append("}");
        }

        json.append("]}");
        return json.toString();
    }

    private static String extractInventory(Player player) {
        Object inventoryObj = invoke(player, "getInventory");
        if (inventoryObj == null) {
            return "";
        }

        int size = readInt(inventoryObj, "getSize", 0);
        if (size <= 0) {
            return "";
        }

        StringBuilder encoded = new StringBuilder();
        for (int slot = 0; slot < size; slot++) {
            try {
                Object itemObj = inventoryObj.getClass().getMethod("getItemStack", int.class).invoke(inventoryObj, slot);
                if (!(itemObj instanceof ItemStack stack)) {
                    continue;
                }
                int amount = readInt(stack, "amount", 0);
                if (amount <= 0) {
                    continue;
                }

                Material material = readMaterial(stack);
                String materialId = materialId(material);
                if (materialId == null || materialId.isBlank() || "minecraft:air".equals(materialId)) {
                    continue;
                }

                if (encoded.length() > 0) {
                    encoded.append(';');
                }
                encoded.append(slot)
                        .append('=')
                        .append(materialId)
                        .append('*')
                        .append(amount);
            } catch (Exception ignored) {
            }
        }
        return encoded.toString();
    }

    private static Material readMaterial(ItemStack stack) {
        try {
            Object value = stack.getClass().getMethod("material").invoke(stack);
            return value instanceof Material material ? material : null;
        } catch (Exception ignored) {
            try {
                Object value = stack.getClass().getMethod("getMaterial").invoke(stack);
                return value instanceof Material material ? material : null;
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static String materialId(Material material) {
        if (material == null) {
            return "";
        }
        try {
            Object namespace = material.getClass().getMethod("namespace").invoke(material);
            if (namespace != null) {
                return namespace.toString();
            }
        } catch (Exception ignored) {
        }

        String raw = material.toString();
        if (raw.contains(":")) {
            return raw;
        }
        return "minecraft:" + raw.toLowerCase(Locale.ROOT);
    }

    private static Material materialFromId(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return null;
        }
        try {
            return Material.fromNamespaceId(materialId.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolvePlayerId(Player player) {
        String username = player.getUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }
        return player.getUuid().toString();
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void postJson(String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            withAuth(builder);
            CLIENT.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Remote sync is optional.
        }
    }

    private static void withAuth(HttpRequest.Builder builder) {
        if (authToken != null && !authToken.isBlank()) {
            builder.header("X-Auth-Token", authToken);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLongSafe(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(Object instance, String methodName, int fallback) {
        try {
            Object value = instance.getClass().getMethod(methodName).invoke(instance);
            return value instanceof Integer integer ? integer : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float readFloat(Object instance, String methodName, float fallback) {
        try {
            Object value = instance.getClass().getMethod(methodName).invoke(instance);
            return value instanceof Float f ? f : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void setInt(Object instance, String methodName, int value) {
        try {
            instance.getClass().getMethod(methodName, int.class).invoke(instance, value);
        } catch (Exception ignored) {
        }
    }

    private static void setFloat(Object instance, String methodName, float value) {
        try {
            instance.getClass().getMethod(methodName, float.class).invoke(instance, value);
        } catch (Exception ignored) {
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescapePipe(String raw) {
        return raw.replace("%7C", "|").replace("%0A", "\n");
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String parseString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return null;
        }
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote).replace("\\\"", "\"");
    }

    private static Long parseLong(String json, String key) {
        String raw = parseRawNumber(json, key);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String json, String key) {
        String raw = parseRawNumber(json, key);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double parseDouble(String json, String key) {
        String raw = parseRawNumber(json, key);
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static float parseFloat(String json, String key, float fallback) {
        Double value = parseDouble(json, key);
        return value == null ? fallback : value.floatValue();
    }

    private static Boolean parseBoolean(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (json.startsWith("true", start)) {
            return true;
        }
        if (json.startsWith("false", start)) {
            return false;
        }
        return null;
    }

    private static String parseRawNumber(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (!(Character.isDigit(c) || c == '-' || c == '+' || c == '.')) {
                break;
            }
            end++;
        }
        if (start == end) {
            return null;
        }
        return json.substring(start, end);
    }
}