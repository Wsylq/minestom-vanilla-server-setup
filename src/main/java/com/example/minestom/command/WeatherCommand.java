package com.example.minestom.command;

import com.example.minestom.config.ServerState;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class WeatherCommand extends Command {

    public WeatherCommand(ServerState state) {
        super("weather");

        var valueArgument = ArgumentType.Word("value");

        setDefaultExecutor((sender, context) -> sender.sendMessage("Usage: /weather <clear|rain|thunder>"));

        addSyntax((sender, context) -> {
            String value = context.get(valueArgument).toLowerCase();
            ServerState.Weather weather = switch (value) {
                case "clear" -> ServerState.Weather.CLEAR;
                case "rain" -> ServerState.Weather.RAIN;
                case "thunder" -> ServerState.Weather.THUNDER;
                default -> null;
            };

            if (weather == null) {
                sender.sendMessage("Unknown weather value.");
                return;
            }

            state.setWeather(weather);
            sender.sendMessage("Weather set to " + value + ".");
        }, valueArgument);
    }
}