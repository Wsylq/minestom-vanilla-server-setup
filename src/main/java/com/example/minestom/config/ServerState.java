package com.example.minestom.config;

import java.util.Locale;

public final class ServerState {

    private volatile boolean doDaylightCycle = true;
    private volatile boolean doMobSpawning = true;
    private volatile boolean doWeatherCycle = true;
    private volatile boolean keepInventory = false;

    private volatile long worldTime = 1000L;
    private volatile int worldBorderRadius = 1200;
    private volatile Weather weather = Weather.CLEAR;

    public boolean getRule(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "dodaylightcycle" -> doDaylightCycle;
            case "domobspawning" -> doMobSpawning;
            case "doweathercycle" -> doWeatherCycle;
            case "keepinventory" -> keepInventory;
            default -> throw new IllegalArgumentException("Unknown gamerule: " + key);
        };
    }

    public void setRule(String key, boolean value) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "dodaylightcycle" -> doDaylightCycle = value;
            case "domobspawning" -> doMobSpawning = value;
            case "doweathercycle" -> doWeatherCycle = value;
            case "keepinventory" -> keepInventory = value;
            default -> throw new IllegalArgumentException("Unknown gamerule: " + key);
        }
    }

    public boolean doDaylightCycle() {
        return doDaylightCycle;
    }

    public boolean doMobSpawning() {
        return doMobSpawning;
    }

    public boolean doWeatherCycle() {
        return doWeatherCycle;
    }

    public boolean keepInventory() {
        return keepInventory;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public void setWorldTime(long worldTime) {
        this.worldTime = normalizeTime(worldTime);
    }

    public void tickTime(int ticks) {
        this.worldTime = normalizeTime(this.worldTime + ticks);
    }

    public int getWorldBorderRadius() {
        return worldBorderRadius;
    }

    public void setWorldBorderRadius(int worldBorderRadius) {
        if (worldBorderRadius < 32 || worldBorderRadius > 20000) {
            throw new IllegalArgumentException("World border radius must be 32..20000");
        }
        this.worldBorderRadius = worldBorderRadius;
    }

    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = weather;
    }

    public boolean isNight() {
        return worldTime >= 13000L && worldTime <= 23000L;
    }

    private static long normalizeTime(long input) {
        long normalized = input % 24000L;
        if (normalized < 0) {
            normalized += 24000L;
        }
        return normalized;
    }

    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }
}