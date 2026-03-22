package com.example.minestom.command;

import com.example.minestom.config.ServerState;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.instance.Instance;

public final class TimeCommand extends Command {

    // Backward-compatible overload so Main can pass shared server state,
    // while older call sites using only Instance still compile.
    public TimeCommand(Instance overworld) {
        this(overworld, null);
    }

    public TimeCommand(Instance overworld, ServerState state) {
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

            if (state != null) {
                state.setWorldTime(time);
            }
            overworld.setTime(time);
            sender.sendMessage("Set world time to " + value + ".");
        }, valueArgument);
    }
}