package com.example.minestom.command;

import com.example.minestom.config.ServerState;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class GameruleCommand extends Command {

    public GameruleCommand(ServerState state) {
        super("gamerule");

        var keyArgument = ArgumentType.Word("rule");
        var valueArgument = ArgumentType.Word("value");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /gamerule <doDaylightCycle|doMobSpawning|doWeatherCycle|keepInventory> <true|false>");
        });

        addSyntax((sender, context) -> {
            String key = context.get(keyArgument);
            String rawValue = context.get(valueArgument).toLowerCase();
            if (!rawValue.equals("true") && !rawValue.equals("false")) {
                sender.sendMessage("Value must be true or false.");
                return;
            }

            boolean value = Boolean.parseBoolean(rawValue);
            try {
                state.setRule(key, value);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(exception.getMessage());
                return;
            }

            sender.sendMessage("Set gamerule " + key + " to " + value + ".");
        }, keyArgument, valueArgument);
    }
}