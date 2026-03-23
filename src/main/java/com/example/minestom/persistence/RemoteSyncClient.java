package com.example.minestom.persistence;

import com.example.minestom.config.RuntimeSettings;
import com.example.minestom.config.ServerState;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class RemoteSyncClient {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static volatile boolean enabled;
    private static volatile String baseUrl;
    private static volatile String authToken;
    private static volatile String serverId;

    private RemoteSyncClient() {
    }

    public static void initialize(RuntimeSettings settings, ServerState state) {
        enabled = settings.remoteSyncEnabled() && settings.remoteSyncUrl() != null && !settings.remoteSyncUrl().isBlank();
        if (!enabled) {
            return;
        }

        baseUrl = trimTrailingSlash(settings.remoteSyncUrl().trim());
        authToken = settings.remoteSyncToken() == null ? "" : settings.remoteSyncToken().trim();
        serverId = settings.remoteSyncServerId() == null || settings.remoteSyncServerId().isBlank()
                ? "default"
                : settings.remoteSyncServerId().trim();

        pullServerState(state);
    }

    public static void pushSnapshot(ServerState state, List<Player> players) {
        if (!enabled) {
            return;
        }

        String payload = toServerSnapshot(state, players);
        postJson("/api/server-state", payload);
    }

    public static void applyPlayerState(Player player) {
        if (!enabled) {
            return;
        }

        String playerId = player.getUsername() == null || player.getUsername().isBlank()
                ? player.getUuid().toString()
                : player.getUsername();

        String encodedServerId = URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        String encodedPlayerId = URLEncoder.encode(playerId, StandardCharsets.UTF_8);
        String url = baseUrl + "/api/player-state/" + encodedServerId + "/" + encodedPlayerId;

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
        } catch (Exception ignored) {
            // Remote sync is optional, so failures should not block gameplay.
        }
    }

    private static void pullServerState(ServerState state) {
        String encodedServerId = URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        String url = baseUrl + "/api/server-state/" + encodedServerId;

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
            // Remote sync is optional, so failures should not block game loop.
        }
    }

    private static void withAuth(HttpRequest.Builder builder) {
        if (authToken != null && !authToken.isBlank()) {
            builder.header("X-Auth-Token", authToken);
        }
    }

    private static String toServerSnapshot(ServerState state, List<Player> players) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"serverId\":\"").append(escape(serverId)).append("\",");
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
            json.append("\"food\":").append(readInt(player, "getFood", readInt(player, "getFoodLevel", 20)));
            json.append("}");
        }

        json.append("]}");
        return json.toString();
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
        return json.substring(startQuote + 1, endQuote);
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