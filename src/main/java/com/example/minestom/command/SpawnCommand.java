package com.example.minestom.command;

import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class SpawnCommand extends Command {

    private static final Pos SPAWN = new Pos(0.5, 90, 0.5);

    public SpawnCommand() {
        super("spawn");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return;
            }

            player.teleport(SPAWN);
            player.sendMessage("Teleported to spawn.");
        });
    }
}