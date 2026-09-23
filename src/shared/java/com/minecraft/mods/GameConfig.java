package com.minecraft.mods;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the Paper plugin's config.yml keys so existing settings can be copied over. */
public final class GameConfig {
    private static GameConfig current;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();

    private GameConfig() {}

    public static GameConfig get() {
        if (current == null) load();
        return current;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("blockshuffle").resolve("config.yml");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                try (InputStream resource = GameConfig.class.getResourceAsStream("/config.yml")) {
                    if (resource == null) throw new IOException("Missing default config.yml");
                    Files.copy(resource, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            GameConfig parsed = new GameConfig();
            parsed.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
            current = parsed;
        } catch (IOException ex) {
            BlockShuffle.LOGGER.warn("Could not load Block Shuffle configuration; using defaults", ex);
            current = new GameConfig();
        }
    }

    private void parse(List<String> lines) {
        String section = "";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = line.indexOf(trimmed);
            if (trimmed.startsWith("- ")) {
                lists.computeIfAbsent(section, ignored -> new ArrayList<>()).add(unquote(trimmed.substring(2)));
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator < 0) continue;
            String name = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (indent == 0) {
                section = name;
                if (!value.isEmpty() && !value.startsWith("#")) values.put(name, unquote(value));
            } else if (!value.isEmpty() && !value.startsWith("#")) {
                values.put(section + "." + name, unquote(value));
            }
        }
    }

    private static String unquote(String value) {
        int comment = value.indexOf(" #");
        if (comment >= 0) value = value.substring(0, comment);
        value = value.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }

    public String string(String key, String fallback) { return values.getOrDefault(key, fallback); }

    public int integer(String key, int fallback) {
        try { return Integer.parseInt(string(key, "")); }
        catch (NumberFormatException ex) { return fallback; }
    }

    public double decimal(String key, double fallback) {
        try { return Double.parseDouble(string(key, "")); }
        catch (NumberFormatException ex) { return fallback; }
    }

    public boolean bool(String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public List<String> list(String key) { return lists.getOrDefault(key, List.of()); }

    public int blockWeight(String id, int fallback) {
        return integer("weights." + id.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""), fallback);
    }
}
