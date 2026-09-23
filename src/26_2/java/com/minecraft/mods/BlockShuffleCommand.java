package com.minecraft.mods;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class BlockShuffleCommand {
    private BlockShuffleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("blockshuffle")
            .executes(context -> help(context.getSource()))
            .then(Commands.literal("start").executes(context -> start(context.getSource())))
            .then(Commands.literal("quit").executes(context -> quit(context.getSource())))
            .then(Commands.literal("help").executes(context -> help(context.getSource())))
            .then(Commands.literal("about").executes(context -> about(context.getSource())))
            .then(Commands.literal("stop")
                .requires(BlockShuffleCommand::operator)
                .executes(context -> stop(context.getSource())))
            .then(Commands.literal("reload")
                .requires(BlockShuffleCommand::operator)
                .executes(context -> reload(context.getSource()))));
        dispatcher.register(Commands.literal("bs").redirect(root));
    }

    private static boolean operator(CommandSourceStack source) {
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
    }

    private static int start(CommandSourceStack source) {
        if (GameManager.running()) {
            source.sendFailure(Component.literal("Game already started!"));
            return 0;
        }
        GameManager.start(source.getServer());
        return 1;
    }

    private static int quit(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can quit the game!"));
            return 0;
        }
        if (!GameManager.running()) {
            source.sendFailure(Component.literal("No game is currently running!"));
            return 0;
        }
        if (!GameManager.participates(player.getUUID())) {
            source.sendFailure(Component.literal("You are not in the game!"));
            return 0;
        }
        GameManager.quit(player.getUUID(), player.getName().getString());
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        GameManager.stop("Game ended by an operator.");
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        GameConfig.load();
        source.sendSuccess(() -> Component.literal("Block Shuffle configuration reloaded."), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Block Shuffle: /bs <start|quit|help|about>").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Operators: /bs <stop|reload>").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int about(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Block Shuffle Fabric port of Mohammad Faizan's Paper plugin."), false);
        return 1;
    }
}
