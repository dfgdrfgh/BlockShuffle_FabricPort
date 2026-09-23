package com.minecraft.mods;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
    private static ServerBossBar bar;
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
        if (instance.getPlayerManager().getPlayerList().size() < minimum) {
            broadcast(instance, Text.literal("Not enough players to start Block Shuffle. Minimum required: " + minimum)
                .formatted(Formatting.RED));
            return;
        }
        server = instance;
        clear();
        running = true;
        round = 1;
        rebuildCatalog(instance);
        broadcast(instance, message("messages.start", "&a🟢 Block Shuffle started!"));
        for (ServerPlayerEntity player : instance.getPlayerManager().getPlayerList()) {
            Block target = pickBlock();
            PLAYERS.put(player.getUuid(), new PlayerData(player.getUuid(), target));
            player.sendMessage(Text.literal("Your block: ").formatted(Formatting.YELLOW)
                .append(Text.translatable(target.getTranslationKey()).formatted(Formatting.GOLD)), false);
        }
        initialPlayers = PLAYERS.size();
        beginRound();
    }

    private static void rebuildCatalog(MinecraftServer instance) {
        ALLOWED.clear();
        GameConfig config = GameConfig.get();
        List<String> blacklist = config.list("blacklist");
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            String name = id.getPath().toUpperCase(Locale.ROOT);
            if (!id.getNamespace().equals("minecraft") || blacklist.contains(name)) continue;
            if (!config.bool("allow.nether", true) && name.contains("NETHER")) continue;
            if (!config.bool("allow.end", false) && name.contains("END") && !name.contains("END_ROD")) continue;
            if (block == Blocks.AIR || block.asItem() == Items.AIR) continue;
            if (name.contains("WATER") || name.contains("LAVA") || name.endsWith("_AIR")) continue;
            if (block.getDefaultState().getCollisionShape(instance.getOverworld(), BlockPos.ORIGIN).isEmpty()) continue;
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
        String name = Registries.BLOCK.getId(block).getPath().toUpperCase(Locale.ROOT);
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
            bar = new ServerBossBar(barTitle(), BossBar.Color.GREEN, BossBar.Style.PROGRESS);
            for (PlayerData data : PLAYERS.values()) {
                ServerPlayerEntity player = player(data.id);
                if (player != null) bar.addPlayer(player);
            }
        }
    }

    private static Text barTitle() {
        return Text.literal("Round " + round + " - Time Left: " + String.format("%02d:%02d", timeLeft / 60, timeLeft % 60));
    }

    public static void tick(MinecraftServer instance) {
        if (!running || instance != server) return;
        for (PlayerData data : new ArrayList<>(PLAYERS.values())) {
            if (data.completed) continue;
            ServerPlayerEntity player = player(data.id);
            if (player == null) continue;
            BlockPos below = player.getBlockPos().down();
            if (player.getEntityWorld().getBlockState(below).isOf(data.target)) {
                data.completed = true;
                player.sendMessage(Text.literal("✔ You are standing on the given block!").formatted(Formatting.GREEN), false);
            }
        }
        if (PLAYERS.isEmpty()) { stop("Game ended - all players have left the game."); return; }
        if (PLAYERS.values().stream().allMatch(data -> data.completed)) { endRound(); return; }
        if (++ticks < 20) return;
        ticks = 0;
        timeLeft--;
        if (!halfAnnounced && timeLeft <= roundTime / 2) {
            broadcast(server, Text.literal("Half time! " + formatTime(timeLeft) + " remaining.").formatted(Formatting.YELLOW));
            halfAnnounced = true;
        }
        if (timeLeft <= 0) { endRound(); return; }
        for (PlayerData data : PLAYERS.values()) {
            ServerPlayerEntity player = player(data.id);
            if (player == null) continue;
            if (data.completed) {
                player.sendMessage(Text.literal("✔ Completed!").formatted(Formatting.GREEN), true);
            } else if (timeLeft <= 10) {
                player.sendMessage(Text.literal("⚠ " + timeLeft + " ⚠").formatted(Formatting.RED), true);
            } else {
                player.sendMessage(Text.literal("Find: ").formatted(Formatting.YELLOW)
                    .append(Text.translatable(data.target.getTranslationKey()).formatted(Formatting.GOLD)), true);
            }
        }
        lastCountdown = timeLeft <= 10 ? timeLeft : -1;
        if (bar != null) {
            bar.setPercent(Math.max(0f, Math.min(1f, (float) timeLeft / roundTime)));
            bar.setName(barTitle());
            bar.setColor(timeLeft <= 30 ? BossBar.Color.RED : timeLeft <= 60 ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
        }
    }

    private static String formatTime(int seconds) { return String.format("%02d:%02d", seconds / 60, seconds % 60); }

    private static void endRound() {
        List<UUID> failed = new ArrayList<>();
        for (PlayerData data : PLAYERS.values()) {
            if (!data.completed) {
                failed.add(data.id);
                ServerPlayerEntity player = player(data.id);
                if (player != null) player.sendMessage(message("messages.fail", "&cYou failed this round!"), false);
            }
        }
        failed.forEach(PLAYERS::remove);
        refreshBarPlayers();
        if (PLAYERS.isEmpty()) { stop("All players failed! Game ended."); return; }
        if (PLAYERS.size() == 1 && initialPlayers > 1) { win(PLAYERS.values().iterator().next()); return; }
        round++;
        broadcast(server, Text.literal("Round " + round + "!").formatted(Formatting.YELLOW));
        for (PlayerData data : PLAYERS.values()) {
            data.target = pickBlock();
            data.completed = false;
            ServerPlayerEntity player = player(data.id);
            if (player != null) player.sendMessage(Text.literal("New block: ").formatted(Formatting.GREEN)
                .append(Text.translatable(data.target.getTranslationKey()).formatted(Formatting.GOLD)), false);
        }
        beginRound();
    }

    private static void win(PlayerData data) {
        broadcast(server, message("messages.win", "&6🏆 {player} won the game!"));
        clear();
    }

    private static Text message(String key, String fallback) {
        String raw = GameConfig.get().string(key, fallback);
        if (key.equals("messages.win") && PLAYERS.size() == 1) {
            ServerPlayerEntity winner = player(PLAYERS.values().iterator().next().id);
            raw = raw.replace("{player}", winner == null ? "Someone" : winner.getName().getString());
        }
        MutableText result = Text.empty();
        Formatting color = Formatting.WHITE;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '&' && i + 1 < raw.length()) {
                Formatting next = Formatting.byCode(raw.charAt(i + 1));
                if (next != null) {
                    if (!part.isEmpty()) { result.append(Text.literal(part.toString()).formatted(color)); part.setLength(0); }
                    color = next;
                    i++;
                    continue;
                }
            }
            part.append(raw.charAt(i));
        }
        if (!part.isEmpty()) result.append(Text.literal(part.toString()).formatted(color));
        return result;
    }

    public static void quit(UUID id, String name) {
        if (PLAYERS.remove(id) == null) return;
        broadcast(server, Text.literal(name + " has left this Block Shuffle game.").formatted(Formatting.YELLOW));
        afterDeparture();
    }

    public static void disconnect(UUID id, String name) {
        if (!running || PLAYERS.remove(id) == null) return;
        broadcast(server, Text.literal(name + " has disconnected from the game.").formatted(Formatting.YELLOW));
        afterDeparture();
    }

    private static void afterDeparture() {
        refreshBarPlayers();
        if (PLAYERS.isEmpty()) { stop("Game ended - no players remaining."); return; }
        if (PLAYERS.size() == 1 && initialPlayers > 1) win(PLAYERS.values().iterator().next());
    }

    public static void stop(String reason) {
        if (server != null && reason != null) broadcast(server, Text.literal(reason).formatted(Formatting.RED));
        clear();
    }

    public static void cleanup() { clear(); server = null; }

    private static void clear() {
        running = false;
        removeBar();
        if (server != null) for (PlayerData data : PLAYERS.values()) {
            ServerPlayerEntity player = player(data.id);
            if (player != null) player.sendMessage(Text.empty(), true);
        }
        PLAYERS.clear();
        ALLOWED.clear();
        round = 1;
        ticks = 0;
    }

    private static void removeBar() {
        if (bar != null) { bar.clearPlayers(); bar = null; }
    }

    private static void refreshBarPlayers() {
        if (bar == null) return;
        for (ServerPlayerEntity player : new ArrayList<>(bar.getPlayers())) {
            if (!PLAYERS.containsKey(player.getUuid())) bar.removePlayer(player);
        }
    }

    private static void broadcast(MinecraftServer instance, Text message) {
        instance.getPlayerManager().broadcast(message, false);
    }

    private static ServerPlayerEntity player(UUID id) {
        return server == null ? null : server.getPlayerManager().getPlayer(id);
    }
}
