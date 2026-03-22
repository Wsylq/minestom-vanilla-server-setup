package com.example.minestom.command;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

public final class GamemodeCommand extends Command {

    public GamemodeCommand() {
        super("gamemode", "gm");

        var modeArgument = ArgumentType.Word("mode");

        setDefaultExecutor((sender, context) -> sender.sendMessage("Usage: /gamemode <survival|creative|adventure|spectator>"));

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return;
            }

            String modeValue = context.get(modeArgument).toLowerCase();
            GameMode mode = switch (modeValue) {
                case "survival", "s" -> GameMode.SURVIVAL;
                case "creative", "c" -> GameMode.CREATIVE;
                case "adventure", "a" -> GameMode.ADVENTURE;
                case "spectator", "sp" -> GameMode.SPECTATOR;
                default -> null;
            };

            if (mode == null) {
                sender.sendMessage("Unknown mode.");
                return;
            }

            player.setGameMode(mode);
            sender.sendMessage("Your gamemode is now " + mode.name().toLowerCase() + ".");
        }, modeArgument);
    }
}