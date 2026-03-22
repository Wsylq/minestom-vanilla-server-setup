package com.example.minestom.command;

import com.example.minestom.config.ServerState;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class WorldBorderCommand extends Command {

    public WorldBorderCommand(ServerState state) {
        super("worldborder");

        var radiusArgument = ArgumentType.Integer("radius");

        setDefaultExecutor((sender, context) -> sender.sendMessage("Usage: /worldborder <radius>"));

        addSyntax((sender, context) -> {
            int radius = context.get(radiusArgument);
            try {
                state.setWorldBorderRadius(radius);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(exception.getMessage());
                return;
            }

            sender.sendMessage("World border radius is now " + radius + " blocks.");
        }, radiusArgument);
    }
}