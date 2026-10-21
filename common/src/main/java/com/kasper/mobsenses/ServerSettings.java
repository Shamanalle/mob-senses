package com.kasper.mobsenses;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The server's settings file, {@value #FILE_NAME}: the preset, the multipliers of the senses, the voice
 * switch and per-world presets. Written with comments in English and Russian on first start, re-read
 * when it changes on disk (an admin edited it) and saved when a command changes a value.
 * <p>
 * A value that cannot be read keeps its default and is reported in the log; a broken file never
 * stops the server.
 */
public final class ServerSettings {

    public static final String FILE_NAME = "mobsenses-server.properties";

    public static final double MIN_MULTIPLIER = 0.0;
    public static final double MAX_MULTIPLIER = 3.0;

    private final Path file;
    private long modified = Long.MIN_VALUE;

    private volatile Preset preset = Preset.DEFAULT;
    private volatile double hearing = 1.0;
    private volatile double sight = 1.0;
    private volatile boolean voice = true;
    private volatile String language = ServerText.DEFAULT_LANGUAGE;
    /** World id ("minecraft:the_nether") to its own preset. */
    private volatile Map<String, Preset> worlds = Collections.emptyMap();

    /** @param file the settings file; null keeps everything in memory (tests) */
    public ServerSettings(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public Preset preset() {
        return preset;
    }

    /** The preset of a world: its own, or the server's. */
    public Preset presetOf(String world) {
        Preset own = world == null ? null : worlds.get(world);
        return own != null ? own : preset;
    }

    public double hearing() {
        return hearing;
    }

    public double sight() {
        return sight;
    }

    public boolean voice() {
        return voice;
    }

    /** The language of replies to the console and to players whose language is unknown. */
    public String language() {
        return language;
    }

    /** Worlds with a preset of their own, sorted by id. */
    public Map<String, Preset> worlds() {
        return worlds;
    }

    public void setPreset(Preset p) {
        preset = p;
    }

    /** @param p null to let the world use the server's preset again */
    public void setWorldPreset(String world, Preset p) {
        Map<String, Preset> copy = new TreeMap<>(worlds);
        if (p == null) {
            copy.remove(world);
        } else {
            copy.put(world, p);
        }
        worlds = Collections.unmodifiableMap(copy);
    }

    public void setHearing(double value) {
        hearing = clampMultiplier(value);
    }

    public void setSight(double value) {
        sight = clampMultiplier(value);
    }

    public void setVoice(boolean on) {
        voice = on;
    }

    public static boolean validMultiplier(double value) {
        return value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER;
    }

    private static double clampMultiplier(double value) {
        return Double.isNaN(value) ? 1.0 : Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
    }

    /** Reads the file, or writes it with the defaults when there is none yet. */
    public synchronized void load() {
        if (file == null) {
            return;
        }
        try {
            if (!Files.exists(file)) {
                save();
                return;
            }
            modified = Files.getLastModifiedTime(file).toMillis();
            read(Jv.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Reading " + file, e);
        }
    }

    /** Reads the file again when it changed on disk since the last read or write. @return whether it did */
    public synchronized boolean reloadIfChanged() {
        if (file == null) {
            return false;
        }
        try {
            if (!Files.exists(file) || Files.getLastModifiedTime(file).toMillis() == modified) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        load();
        return true;
    }

    /** Parses the text of a settings file into this object. Unknown or broken lines keep the defaults. */
    synchronized void read(String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        Preset parsed = Preset.byId(p.getProperty("preset"));
        preset = parsed != null ? parsed : Preset.DEFAULT;
        hearing = number(p, "hearing", 1.0);
        sight = number(p, "sight", 1.0);
        voice = !"false".equalsIgnoreCase(p.getProperty("voice", "true").trim());
        String lang = p.getProperty("language", ServerText.DEFAULT_LANGUAGE).trim();
        language = Jv.isBlank(lang) ? ServerText.DEFAULT_LANGUAGE : lang.toLowerCase(Locale.ROOT);
        Map<String, Preset> w = new TreeMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("world.") && key.endsWith(".preset")) {
                String world = key.substring("world.".length(), key.length() - ".preset".length());
                Preset wp = Preset.byId(p.getProperty(key));
                if (!world.isEmpty() && wp != null) {
                    w.put(world, wp);
                } else {
                    ModLog.warn(FILE_NAME + ": ignored " + key + "=" + p.getProperty(key));
                }
            }
        }
        worlds = Collections.unmodifiableMap(w);
    }

    private static double number(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(v.trim());
            if (validMultiplier(d)) {
                return d;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        ModLog.warn(FILE_NAME + ": " + key + "=" + v + " is not a number from " + format(MIN_MULTIPLIER) + " to "
                + format(MAX_MULTIPLIER) + "; using " + format(fallback));
        return fallback;
    }

    /** Writes the current values with their comments. */
    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Jv.writeString(file, write(), StandardCharsets.UTF_8);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Writing " + file, e);
        }
    }

    /** The text of the settings file for the current values. */
    synchronized String write() {
        List<String> lines = new ArrayList<>();
        lines.add("# Mob Senses: server settings. Changes made here are read without a restart.");
        lines.add("# Mob Senses: настройки сервера. Изменения в этом файле применяются без перезапуска.");
        lines.add("");
        lines.add("# How keen the mobs' senses are: off, subtle, default or hardcore.");
        lines.add("# Насколько острые чувства у мобов: off, subtle, default или hardcore.");
        lines.add("preset=" + preset.id());
        lines.add("");
        lines.add("# How far mobs hear noise, times the preset's distance (0 - 3, 1 = as the preset says).");
        lines.add("# Насколько далеко мобы слышат шум, множитель к пресету (0 - 3, 1 = как в пресете).");
        lines.add("hearing=" + format(hearing));
        lines.add("");
        lines.add("# How far mobs see the player, times the preset's distance (0 - 3).");
        lines.add("# Насколько далеко мобы видят игрока, множитель к пресету (0 - 3).");
        lines.add("sight=" + format(sight));
        lines.add("");
        lines.add("# Whether mobs hear players talk in Simple Voice Chat (when it is installed): true or false.");
        lines.add("# Слышат ли мобы речь игроков в Simple Voice Chat (если он установлен): true или false.");
        lines.add("voice=" + voice);
        lines.add("");
        lines.add("# Language of replies to the console and to players whose language is unknown (en_us, ru_ru, ...).");
        lines.add("# Язык ответов консоли и игрокам, чей язык неизвестен (en_us, ru_ru, ...).");
        lines.add("language=" + language);
        lines.add("");
        lines.add("# A world's own preset: world.<world id>.preset=<preset>, e.g. world.minecraft:the_nether.preset=hardcore");
        lines.add("# Свой пресет мира: world.<id мира>.preset=<пресет>, например world.minecraft:the_nether.preset=hardcore");
        for (Map.Entry<String, Preset> e : worlds.entrySet()) {
            lines.add("world." + e.getKey().replace(":", "\\:") + ".preset=" + e.getValue().id());
        }
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    /** A multiplier as the file and the replies show it: "1", "0.75". */
    public static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        String s = String.format(Locale.ROOT, "%.2f", value);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }
}
