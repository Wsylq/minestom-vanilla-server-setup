package com.example.minestom.command;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

public final class DimensionCommand extends Command {

    private final Instance overworld;
    private final Instance nether;
    private final Instance end;

    public DimensionCommand(Instance overworld, Instance nether, Instance end) {
        super("dimension", "dim");
        this.overworld = overworld;
        this.nether = nether;
        this.end = end;

        var nameArgument = ArgumentType.Word("name");

        setDefaultExecutor((sender, context) -> sender.sendMessage("Usage: /dimension <overworld|nether|end>"));

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return;
            }

            String name = context.get(nameArgument).toLowerCase();
            Instance target = switch (name) {
                case "overworld", "world" -> overworld;
                case "nether" -> nether;
                case "end" -> end;
                default -> null;
            };

            if (target == null) {
                sender.sendMessage("Unknown dimension.");
                return;
            }

            Pos targetPos = switch (name) {
                case "nether" -> new Pos(0.5, 64, 0.5);
                case "end" -> new Pos(0.5, 72, 0.5);
                default -> new Pos(0.5, 90, 0.5);
            };

            player.setInstance(target, targetPos);
            sender.sendMessage("Teleported to " + name + ".");
        }, nameArgument);
    }
}