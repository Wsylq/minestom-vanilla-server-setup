package com.example.minestom.command;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.instance.Instance;

public final class TimeCommand extends Command {

    public TimeCommand(Instance overworld) {
        super("time");

        var valueArgument = ArgumentType.Word("value");

        setDefaultExecutor((sender, context) -> sender.sendMessage("Usage: /time <day|noon|night|midnight>"));

        addSyntax((sender, context) -> {
            String value = context.get(valueArgument).toLowerCase();
            Long time = switch (value) {
                case "day" -> 1000L;
                case "noon" -> 6000L;
                case "night" -> 13000L;
                case "midnight" -> 18000L;
                default -> null;
            };

            if (time == null) {
                sender.sendMessage("Unknown value. Try: day, noon, night, midnight.");
                return;
            }

            overworld.setTime(time);
            sender.sendMessage("Set world time to " + value + ".");
        }, valueArgument);
    }
}