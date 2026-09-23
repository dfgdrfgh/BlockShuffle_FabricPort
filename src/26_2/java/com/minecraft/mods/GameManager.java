package com.minecraft.mods;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Server-side implementation of the original Paper game rules. */
public final class GameManager {
    private static final Map<UUID, PlayerData> PLAYERS = new LinkedHashMap<>();
    private static final List<Block> ALLOWED = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static MinecraftServer server;
    private static ServerBossEvent bar;
    private static boolean running;
    private static int round = 1;
    private static int initialPlayers;
    private static int ticks;
    private static int timeLeft;
    private static int roundTime;
    private static boolean halfAnnounced;
    private static int lastCountdown = -1;

    private GameManager() {}

    private static final class PlayerData {
        final UUID id;
        Block target;
        boolean completed;

        PlayerData(UUID id, Block target) {
            this.id = id;
            this.target = target;
        }
    }

    public static boolean running() { return running; }
    public static boolean participates(UUID id) { return PLAYERS.containsKey(id); }

    public static void start(MinecraftServer instance) {
        GameConfig config = GameConfig.get();
        int minimum = Math.max(1, config.integer("min-players", 2));
        if (instance.getPlayerList().getPlayers().size() < minimum) {
            broadcast(instance, Component.literal("Not enough players to start Block Shuffle. Minimum required: " + minimum)
                .withStyle(ChatFormatting.RED));
            return;
        }
        server = instance;
        clear();
        running = true;
        round = 1;
        rebuildCatalog(instance);
        broadcast(instance, message("messages.start", "&a🟢 Block Shuffle started!"));
        for (ServerPlayer player : instance.getPlayerList().getPlayers()) {
            Block target = pickBlock();
            PLAYERS.put(player.getUUID(), new PlayerData(player.getUUID(), target));
            player.sendSystemMessage(Component.literal("Your block: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable(target.getDescriptionId()).withStyle(ChatFormatting.GOLD)), false);
        }
        initialPlayers = PLAYERS.size();
        beginRound();
    }

    private static void rebuildCatalog(MinecraftServer instance) {
        ALLOWED.clear();
        GameConfig config = GameConfig.get();
        List<String> blacklist = config.list("blacklist");
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String name = id.getPath().toUpperCase(Locale.ROOT);
            if (!id.getNamespace().equals("minecraft") || blacklist.contains(name)) continue;
            if (!config.bool("allow.nether", true) && name.contains("NETHER")) continue;
            if (!config.bool("allow.end", false) && name.contains("END") && !name.contains("END_ROD")) continue;
            if (block == Blocks.AIR || block.asItem() == Items.AIR) continue;
            if (name.contains("WATER") || name.contains("LAVA") || name.endsWith("_AIR")) continue;
            if (block.defaultBlockState().getCollisionShape(instance.overworld(), BlockPos.ZERO).isEmpty()) continue;
            ALLOWED.add(block);
        }
        if (ALLOWED.isEmpty()) {
            BlockShuffle.LOGGER.warn("No allowed blocks found! Using stone as fallback");
            ALLOWED.add(Blocks.STONE);
        }
    }

    private static Block pickBlock() {
        GameConfig config = GameConfig.get();
        double reduction = Math.max(0, config.decimal("difficulty.weight-reduction-per-round", 0.15));
        long total = 0;
        for (Block block : ALLOWED) total += weight(block, config, reduction);
        if (total <= 0) return Blocks.STONE;
        long selected = RANDOM.nextLong(total);
        for (Block block : ALLOWED) {
            selected -= weight(block, config, reduction);
            if (selected < 0) return block;
        }
        return ALLOWED.getLast();
    }

    private static int weight(Block block, GameConfig config, double reduction) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath().toUpperCase(Locale.ROOT);
        int base = categoryWeight(name, config);
        return Math.max(1, (int) (base - (round - 1) * reduction));
    }

    private static int categoryWeight(String name, GameConfig config) {
        int custom = config.blockWeight(name, Integer.MIN_VALUE);
        if (custom != Integer.MIN_VALUE) return Math.max(0, custom);
        String category;
        int fallback;
        if (name.equals("STONE") || name.equals("COBBLESTONE") || name.equals("DIRT") || name.equals("GRASS_BLOCK")) {
            category = "common-blocks"; fallback = 10;
        } else if (contains(name, "EMERALD", "ANCIENT_DEBRIS", "NETHERITE")) {
            category = "ultra-rare"; fallback = 1;
        } else if (contains(name, "DIAMOND", "LAPIS", "REDSTONE_ORE")) {
            category = "rare-ores"; fallback = 2;
        } else if (contains(name, "IRON_ORE", "COPPER_ORE", "GOLD_ORE")) {
            category = "uncommon-ores"; fallback = 4;
        } else if (name.contains("COAL_ORE")) {
            category = "common-ores"; fallback = 6;
        } else if (name.contains("NETHER") && !name.contains("NETHERITE")) {
            category = "nether-blocks"; fallback = 5;
        } else if (name.contains("END") && !name.contains("END_ROD")) {
            category = "end-blocks"; fallback = 3;
        } else if (contains(name, "GLASS", "STAINED", "WOOL", "CARPET")) {
            category = "decorative"; fallback = 7;
        } else if (contains(name, "STONE", "BRICK", "CONCRETE", "TERRACOTTA", "LOG", "PLANK", "WOOD",
            "SAND", "GRAVEL", "CLAY", "DIRT", "GRASS")) {
            category = "building-blocks"; fallback = 8;
        } else {
            category = "default"; fallback = 5;
        }
        return Math.max(0, config.integer("weight-categories." + category, fallback));
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static void beginRound() {
        roundTime = Math.max(5, GameConfig.get().integer("round-time", 300));
        timeLeft = roundTime;
        ticks = 0;
        halfAnnounced = false;
        lastCountdown = -1;
        removeBar();
        if (GameConfig.get().bool("allow-bossbar", true)) {
            bar = new ServerBossEvent(UUID.randomUUID(), barTitle(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            for (PlayerData data : PLAYERS.values()) {
                ServerPlayer player = player(data.id);
                if (player != null) bar.addPlayer(player);
            }
        }
    }

    private static Component barTitle() {
        return Component.literal("Round " + round + " - Time Left: " + String.format("%02d:%02d", timeLeft / 60, timeLeft % 60));
    }

    public static void tick(MinecraftServer instance) {
        if (!running || instance != server) return;
        for (PlayerData data : new ArrayList<>(PLAYERS.values())) {
            if (data.completed) continue;
            ServerPlayer player = player(data.id);
            if (player == null) continue;
            BlockPos below = player.blockPosition().below();
            if (player.level().getBlockState(below).is(data.target)) {
                data.completed = true;
                player.sendSystemMessage(Component.literal("✔ You are standing on the given block!").withStyle(ChatFormatting.GREEN), false);
            }
        }
        if (PLAYERS.isEmpty()) { stop("Game ended - all players have left the game."); return; }
        if (PLAYERS.values().stream().allMatch(data -> data.completed)) { endRound(); return; }
        if (++ticks < 20) return;
        ticks = 0;
        timeLeft--;
        if (!halfAnnounced && timeLeft <= roundTime / 2) {
            broadcast(server, Component.literal("Half time! " + formatTime(timeLeft) + " remaining.").withStyle(ChatFormatting.YELLOW));
            halfAnnounced = true;
        }
        if (timeLeft <= 0) { endRound(); return; }
        for (PlayerData data : PLAYERS.values()) {
            ServerPlayer player = player(data.id);
            if (player == null) continue;
            if (data.completed) {
                player.sendSystemMessage(Component.literal("✔ Completed!").withStyle(ChatFormatting.GREEN), true);
            } else if (timeLeft <= 10) {
                player.sendSystemMessage(Component.literal("⚠ " + timeLeft + " ⚠").withStyle(ChatFormatting.RED), true);
            } else {
                player.sendSystemMessage(Component.literal("Find: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable(data.target.getDescriptionId()).withStyle(ChatFormatting.GOLD)), true);
            }
        }
        lastCountdown = timeLeft <= 10 ? timeLeft : -1;
        if (bar != null) {
            bar.setProgress(Math.max(0f, Math.min(1f, (float) timeLeft / roundTime)));
            bar.setName(barTitle());
            bar.setColor(timeLeft <= 30 ? BossEvent.BossBarColor.RED : timeLeft <= 60 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        }
    }

    private static String formatTime(int seconds) { return String.format("%02d:%02d", seconds / 60, seconds % 60); }

    private static void endRound() {
        List<UUID> failed = new ArrayList<>();
        for (PlayerData data : PLAYERS.values()) {
            if (!data.completed) {
                failed.add(data.id);
                ServerPlayer player = player(data.id);
                if (player != null) player.sendSystemMessage(message("messages.fail", "&cYou failed this round!"), false);
            }
        }
        failed.forEach(PLAYERS::remove);
        refreshBarPlayers();
        if (PLAYERS.isEmpty()) { stop("All players failed! Game ended."); return; }
        if (PLAYERS.size() == 1 && initialPlayers > 1) { win(PLAYERS.values().iterator().next()); return; }
        round++;
        broadcast(server, Component.literal("Round " + round + "!").withStyle(ChatFormatting.YELLOW));
        for (PlayerData data : PLAYERS.values()) {
            data.target = pickBlock();
            data.completed = false;
            ServerPlayer player = player(data.id);
            if (player != null) player.sendSystemMessage(Component.literal("New block: ").withStyle(ChatFormatting.GREEN)
                .append(Component.translatable(data.target.getDescriptionId()).withStyle(ChatFormatting.GOLD)), false);
        }
        beginRound();
    }

    private static void win(PlayerData data) {
        broadcast(server, message("messages.win", "&6🏆 {player} won the game!"));
        clear();
    }

    private static Component message(String key, String fallback) {
        String raw = GameConfig.get().string(key, fallback);
        if (key.equals("messages.win") && PLAYERS.size() == 1) {
            ServerPlayer winner = player(PLAYERS.values().iterator().next().id);
            raw = raw.replace("{player}", winner == null ? "Someone" : winner.getName().getString());
        }
        MutableComponent result = Component.empty();
        ChatFormatting color = ChatFormatting.WHITE;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '&' && i + 1 < raw.length()) {
                ChatFormatting next = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (next != null) {
                    if (!part.isEmpty()) { result.append(Component.literal(part.toString()).withStyle(color)); part.setLength(0); }
                    color = next;
                    i++;
                    continue;
                }
            }
            part.append(raw.charAt(i));
        }
        if (!part.isEmpty()) result.append(Component.literal(part.toString()).withStyle(color));
        return result;
    }

    public static void quit(UUID id, String name) {
        if (PLAYERS.remove(id) == null) return;
        broadcast(server, Component.literal(name + " has left this Block Shuffle game.").withStyle(ChatFormatting.YELLOW));
        afterDeparture();
    }

    public static void disconnect(UUID id, String name) {
        if (!running || PLAYERS.remove(id) == null) return;
        broadcast(server, Component.literal(name + " has disconnected from the game.").withStyle(ChatFormatting.YELLOW));
        afterDeparture();
    }

    private static void afterDeparture() {
        refreshBarPlayers();
        if (PLAYERS.isEmpty()) { stop("Game ended - no players remaining."); return; }
        if (PLAYERS.size() == 1 && initialPlayers > 1) win(PLAYERS.values().iterator().next());
    }

    public static void stop(String reason) {
        if (server != null && reason != null) broadcast(server, Component.literal(reason).withStyle(ChatFormatting.RED));
        clear();
    }

    public static void cleanup() { clear(); server = null; }

    private static void clear() {
        running = false;
        removeBar();
        if (server != null) for (PlayerData data : PLAYERS.values()) {
            ServerPlayer player = player(data.id);
            if (player != null) player.sendSystemMessage(Component.empty(), true);
        }
        PLAYERS.clear();
        ALLOWED.clear();
        round = 1;
        ticks = 0;
    }

    private static void removeBar() {
        if (bar != null) { bar.removeAllPlayers(); bar = null; }
    }

    private static void refreshBarPlayers() {
        if (bar == null) return;
        for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
            if (!PLAYERS.containsKey(player.getUUID())) bar.removePlayer(player);
        }
    }

    private static void broadcast(MinecraftServer instance, Component message) {
        instance.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static ServerPlayer player(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }
}
