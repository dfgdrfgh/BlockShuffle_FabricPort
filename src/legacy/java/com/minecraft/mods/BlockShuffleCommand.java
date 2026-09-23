package com.minecraft.mods;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class BlockShuffleCommand {
    private BlockShuffleCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> root = dispatcher.register(CommandManager.literal("blockshuffle")
            .executes(context -> help(context.getSource()))
            .then(CommandManager.literal("start").executes(context -> start(context.getSource())))
            .then(CommandManager.literal("quit").executes(context -> quit(context.getSource())))
            .then(CommandManager.literal("help").executes(context -> help(context.getSource())))
            .then(CommandManager.literal("about").executes(context -> about(context.getSource())))
            .then(CommandManager.literal("stop")
                .requires(BlockShuffleCommand::operator)
                .executes(context -> stop(context.getSource())))
            .then(CommandManager.literal("reload")
                .requires(BlockShuffleCommand::operator)
                .executes(context -> reload(context.getSource()))));
        dispatcher.register(CommandManager.literal("bs").redirect(root));
    }

    private static boolean operator(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private static int start(ServerCommandSource source) {
        if (GameManager.running()) {
            source.sendError(Text.literal("Game already started!"));
            return 0;
        }
        GameManager.start(source.getServer());
        return 1;
    }

    private static int quit(ServerCommandSource source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can quit the game!"));
            return 0;
        }
        if (!GameManager.running()) {
            source.sendError(Text.literal("No game is currently running!"));
            return 0;
        }
        if (!GameManager.participates(player.getUuid())) {
            source.sendError(Text.literal("You are not in the game!"));
            return 0;
        }
        GameManager.quit(player.getUuid(), player.getName().getString());
        return 1;
    }

    private static int stop(ServerCommandSource source) {
        GameManager.stop("Game ended by an operator.");
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        GameConfig.load();
        source.sendFeedback(() -> Text.literal("Block Shuffle configuration reloaded."), false);
        return 1;
    }

    private static int help(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Block Shuffle: /bs <start|quit|help|about>").formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> Text.literal("Operators: /bs <stop|reload>").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int about(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Block Shuffle Fabric port of Mohammad Faizan's Paper plugin."), false);
        return 1;
    }
}
