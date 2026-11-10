package com.kasper.mobsenses;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * The server's settings file, {@value #FILE_NAME}: the preset, global sense multipliers, wall
 * muffling, firearm / TaCZ gunshot distances (unsuppressed, silenced, bullet impact), individual
 * sound category ranges, Simple Voice Chat tuning, sight & darkness stealth toggles, blood scent &
 * bleeding trail settings, mob investigation speeds, custom listener/ignored mob lists, and
 * per-world presets.
 * <p>
 * Written with comments in English and Russian on first start, re-read automatically when it
 * changes on disk (an admin edited it), and saved when a command changes a value.
 * <p>
 * A value that cannot be read keeps its default and is reported in the log; a broken file never
 * stops the server.
 */
public final class ServerSettings {

    public static final String FILE_NAME = "mobsenses-server.properties";

    public static final double MIN_MULTIPLIER = 0.0;
    public static final double MAX_MULTIPLIER = 3.0;
    public static final double MIN_RANGE = 0.0;
    public static final double MAX_RANGE = 256.0;

    private final Path file;
    private long modified = Long.MIN_VALUE;

    // 1. Core & Global Multipliers
    private volatile Preset preset = Preset.DEFAULT;
    private volatile double hearing = 1.0;
    private volatile double sight = 1.0;
    private volatile double wallMuffling = 1.0;
    private volatile String language = ServerText.DEFAULT_LANGUAGE;

    // 2. Firearms & Gun Mods (TaCZ, PointBlank, CGM, Scorched Guns)
    private volatile double gunshotRange = 44.0;
    private volatile double gunshotSilencedRange = 16.0;
    private volatile double bulletImpactRange = 20.0;

    // 3. Individual Sound Categories & Footstep Posture
    private volatile double stepRange = 8.0;
    private volatile double sneakStepMultiplier = 0.25;
    private volatile double sprintStepMultiplier = 1.75;
    private volatile double blockBreakRange = 18.0;
    private volatile double blockPlaceRange = 12.0;
    private volatile double doorRange = 14.0;
    private volatile double containerRange = 12.0;
    private volatile double combatRange = 16.0;
    private volatile double projectileRange = 15.0;
    private volatile double explosionRange = 48.0;
    private volatile double bellRange = 40.0;
    private volatile double noteBlockRange = 28.0;
    private volatile double eatRange = 8.0;

    // 4. Simple Voice Chat
    private volatile boolean voice = true;
    private volatile double voiceRange = 16.0;
    private volatile double voiceWhisperMultiplier = 0.30;

    // 5. Sight, Torchlight & Darkness Stealth
    private volatile double sightRange = 16.0;
    private volatile boolean heldLightEnabled = true;
    private volatile boolean darknessStealthEnabled = true;

    // 6. Blood Scent & Wounded Trail
    private volatile boolean scentEnabled = true;
    private volatile double scentHealthThreshold = 50.0;
    private volatile double scentRange = 14.0;
    private volatile boolean bloodParticles = true;

    // 7. Mob Investigation Speeds & Custom Mob Lists
    private volatile double investigateSpeed = 1.0;
    private volatile double urgentInvestigateSpeed = 1.15;
    private volatile Set<String> extraListeners = Collections.emptySet();
    private volatile Set<String> ignoredMobs = Collections.emptySet();

    // 8. Per-world presets
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

    public double wallMuffling() {
        return wallMuffling;
    }

    public double gunshotRange() {
        return gunshotRange;
    }

    public double gunshotSilencedRange() {
        return gunshotSilencedRange;
    }

    public double bulletImpactRange() {
        return bulletImpactRange;
    }

    public double stepRange() {
        return stepRange;
    }

    public double sneakStepMultiplier() {
        return sneakStepMultiplier;
    }

    public double sprintStepMultiplier() {
        return sprintStepMultiplier;
    }

    public double blockBreakRange() {
        return blockBreakRange;
    }

    public double blockPlaceRange() {
        return blockPlaceRange;
    }

    public double doorRange() {
        return doorRange;
    }

    public double containerRange() {
        return containerRange;
    }

    public double combatRange() {
        return combatRange;
    }

    public double projectileRange() {
        return projectileRange;
    }

    public double explosionRange() {
        return explosionRange;
    }

    public double bellRange() {
        return bellRange;
    }

    public double noteBlockRange() {
        return noteBlockRange;
    }

    public double eatRange() {
        return eatRange;
    }

    public boolean voice() {
        return voice;
    }

    public double voiceRange() {
        return voiceRange;
    }

    public double voiceWhisperMultiplier() {
        return voiceWhisperMultiplier;
    }

    public double sightRange() {
        return sightRange;
    }

    public boolean heldLightEnabled() {
        return heldLightEnabled;
    }

    public boolean darknessStealthEnabled() {
        return darknessStealthEnabled;
    }

    public boolean scentEnabled() {
        return scentEnabled;
    }

    public double scentHealthThreshold() {
        return scentHealthThreshold;
    }

    public double scentRange() {
        return scentRange;
    }

    public boolean bloodParticles() {
        return bloodParticles;
    }

    public double investigateSpeed() {
        return investigateSpeed;
    }

    public double urgentInvestigateSpeed() {
        return urgentInvestigateSpeed;
    }

    public Set<String> extraListeners() {
        return extraListeners;
    }

    public Set<String> ignoredMobs() {
        return ignoredMobs;
    }

    /** Whether {@code entityTypeId} matches an entry in {@code extra_listeners}. */
    public boolean isExtraListener(String entityTypeId) {
        return matchesEntityList(entityTypeId, extraListeners);
    }

    /** Whether {@code entityTypeId} matches an entry in {@code ignored_mobs}. */
    public boolean isIgnoredMob(String entityTypeId) {
        return matchesEntityList(entityTypeId, ignoredMobs);
    }

    private static boolean matchesEntityList(String entityTypeId, Set<String> list) {
        if (entityTypeId == null || entityTypeId.isEmpty() || list == null || list.isEmpty()) {
            return false;
        }
        String full = entityTypeId.trim().toLowerCase(Locale.ROOT);
        if (list.contains(full)) {
            return true;
        }
        String shortName = NoiseModel.normalizeEvent(full);
        if (list.contains(shortName) || list.contains("minecraft:" + shortName)) {
            return true;
        }
        int colon = full.indexOf(':');
        if (colon > 0) {
            String ns = full.substring(0, colon);
            if (list.contains(ns + ":*") || list.contains(ns)) {
                return true;
            }
        }
        return false;
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
        hearing = clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER, 1.0);
    }

    public void setSight(double value) {
        sight = clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER, 1.0);
    }

    public void setWallMuffling(double value) {
        wallMuffling = clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER, 1.0);
    }

    public void setGunshotRange(double value) {
        gunshotRange = clamp(value, MIN_RANGE, MAX_RANGE, 44.0);
    }

    public void setGunshotSilencedRange(double value) {
        gunshotSilencedRange = clamp(value, MIN_RANGE, MAX_RANGE, 16.0);
    }

    public void setBulletImpactRange(double value) {
        bulletImpactRange = clamp(value, MIN_RANGE, MAX_RANGE, 20.0);
    }

    public void setStepRange(double value) {
        stepRange = clamp(value, MIN_RANGE, MAX_RANGE, 8.0);
    }

    public void setSneakStepMultiplier(double value) {
        sneakStepMultiplier = clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER, 0.25);
    }

    public void setSprintStepMultiplier(double value) {
        sprintStepMultiplier = clamp(value, MIN_MULTIPLIER, MAX_MULTIPLIER, 1.75);
    }

    public void setBlockBreakRange(double value) {
        blockBreakRange = clamp(value, MIN_RANGE, MAX_RANGE, 18.0);
    }

    public void setBlockPlaceRange(double value) {
        blockPlaceRange = clamp(value, MIN_RANGE, MAX_RANGE, 12.0);
    }

    public void setDoorRange(double value) {
        doorRange = clamp(value, MIN_RANGE, MAX_RANGE, 14.0);
    }

    public void setContainerRange(double value) {
        containerRange = clamp(value, MIN_RANGE, MAX_RANGE, 12.0);
    }

    public void setCombatRange(double value) {
        combatRange = clamp(value, MIN_RANGE, MAX_RANGE, 16.0);
    }

    public void setProjectileRange(double value) {
        projectileRange = clamp(value, MIN_RANGE, MAX_RANGE, 15.0);
    }

    public void setExplosionRange(double value) {
        explosionRange = clamp(value, MIN_RANGE, MAX_RANGE, 48.0);
    }

    public void setBellRange(double value) {
        bellRange = clamp(value, MIN_RANGE, MAX_RANGE, 40.0);
    }

    public void setNoteBlockRange(double value) {
        noteBlockRange = clamp(value, MIN_RANGE, MAX_RANGE, 28.0);
    }

    public void setEatRange(double value) {
        eatRange = clamp(value, MIN_RANGE, MAX_RANGE, 8.0);
    }

    public void setVoice(boolean on) {
        voice = on;
    }

    public void setVoiceRange(double value) {
        voiceRange = clamp(value, MIN_RANGE, MAX_RANGE, 16.0);
    }

    public void setVoiceWhisperMultiplier(double value) {
        voiceWhisperMultiplier = clamp(value, 0.0, 1.0, 0.30);
    }

    public void setSightRange(double value) {
        sightRange = clamp(value, MIN_RANGE, MAX_RANGE, 16.0);
    }

    public void setHeldLightEnabled(boolean on) {
        heldLightEnabled = on;
    }

    public void setDarknessStealthEnabled(boolean on) {
        darknessStealthEnabled = on;
    }

    public void setScentEnabled(boolean on) {
        scentEnabled = on;
    }

    public void setScentHealthThreshold(double value) {
        scentHealthThreshold = clamp(value, 5.0, 100.0, 50.0);
    }

    public void setScentRange(double value) {
        scentRange = clamp(value, MIN_RANGE, MAX_RANGE, 14.0);
    }

    public void setBloodParticles(boolean on) {
        bloodParticles = on;
    }

    public void setInvestigateSpeed(double value) {
        investigateSpeed = clamp(value, 0.5, 2.5, 1.0);
    }

    public void setUrgentInvestigateSpeed(double value) {
        urgentInvestigateSpeed = clamp(value, 0.5, 2.5, 1.15);
    }

    public void setExtraListeners(Set<String> ids) {
        extraListeners = normalizeSet(ids);
    }

    public void setIgnoredMobs(Set<String> ids) {
        ignoredMobs = normalizeSet(ids);
    }

    public static boolean validMultiplier(double value) {
        return value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
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
        hearing = boundedNumber(p, "hearing", MIN_MULTIPLIER, MAX_MULTIPLIER, 1.0);
        sight = boundedNumber(p, "sight", MIN_MULTIPLIER, MAX_MULTIPLIER, 1.0);
        wallMuffling = boundedNumber(p, "wall_muffling", MIN_MULTIPLIER, MAX_MULTIPLIER, 1.0);

        gunshotRange = boundedNumber(p, "gunshot_range", MIN_RANGE, MAX_RANGE, 44.0);
        gunshotSilencedRange = boundedNumber(p, "gunshot_silenced_range", MIN_RANGE, MAX_RANGE, 16.0);
        bulletImpactRange = boundedNumber(p, "bullet_impact_range", MIN_RANGE, MAX_RANGE, 20.0);

        stepRange = boundedNumber(p, "step_range", MIN_RANGE, MAX_RANGE, 8.0);
        sneakStepMultiplier = boundedNumber(p, "sneak_step_multiplier", MIN_MULTIPLIER, MAX_MULTIPLIER, 0.25);
        sprintStepMultiplier = boundedNumber(p, "sprint_step_multiplier", MIN_MULTIPLIER, MAX_MULTIPLIER, 1.75);
        blockBreakRange = boundedNumber(p, "block_break_range", MIN_RANGE, MAX_RANGE, 18.0);
        blockPlaceRange = boundedNumber(p, "block_place_range", MIN_RANGE, MAX_RANGE, 12.0);
        doorRange = boundedNumber(p, "door_range", MIN_RANGE, MAX_RANGE, 14.0);
        containerRange = boundedNumber(p, "container_range", MIN_RANGE, MAX_RANGE, 12.0);
        combatRange = boundedNumber(p, "combat_range", MIN_RANGE, MAX_RANGE, 16.0);
        projectileRange = boundedNumber(p, "projectile_range", MIN_RANGE, MAX_RANGE, 15.0);
        explosionRange = boundedNumber(p, "explosion_range", MIN_RANGE, MAX_RANGE, 48.0);
        bellRange = boundedNumber(p, "bell_range", MIN_RANGE, MAX_RANGE, 40.0);
        noteBlockRange = boundedNumber(p, "note_block_range", MIN_RANGE, MAX_RANGE, 28.0);
        eatRange = boundedNumber(p, "eat_range", MIN_RANGE, MAX_RANGE, 8.0);

        voice = bool(p, "voice", true);
        voiceRange = boundedNumber(p, "voice_range", MIN_RANGE, MAX_RANGE, 16.0);
        voiceWhisperMultiplier = boundedNumber(p, "voice_whisper_multiplier", 0.0, 1.0, 0.30);

        sightRange = boundedNumber(p, "sight_range", MIN_RANGE, MAX_RANGE, 16.0);
        heldLightEnabled = bool(p, "held_light_enabled", true);
        darknessStealthEnabled = bool(p, "darkness_stealth_enabled", true);

        scentEnabled = bool(p, "scent_enabled", true);
        scentHealthThreshold = boundedNumber(p, "scent_health_threshold", 5.0, 100.0, 50.0);
        scentRange = boundedNumber(p, "scent_range", MIN_RANGE, MAX_RANGE, 14.0);
        bloodParticles = bool(p, "blood_particles", true);

        investigateSpeed = boundedNumber(p, "investigate_speed", 0.5, 2.5, 1.0);
        urgentInvestigateSpeed = boundedNumber(p, "urgent_investigate_speed", 0.5, 2.5, 1.15);
        extraListeners = parseCsvSet(p.getProperty("extra_listeners"));
        ignoredMobs = parseCsvSet(p.getProperty("ignored_mobs"));

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

    private static double boundedNumber(Properties p, String key, double min, double max, double fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(v.trim().replace(',', '.'));
            if (d >= min && d <= max) {
                return d;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        ModLog.warn(FILE_NAME + ": " + key + "=" + v + " is not a number from " + format(min) + " to "
                + format(max) + "; using " + format(fallback));
        return fallback;
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null || Jv.isBlank(v)) {
            return fallback;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "on".equals(s) || "yes".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "off".equals(s) || "no".equals(s) || "0".equals(s)) {
            return false;
        }
        return fallback;
    }

    private static Set<String> parseCsvSet(String raw) {
        if (raw == null || Jv.isBlank(raw)) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static Set<String> normalizeSet(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !Jv.isBlank(id)) {
                copy.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(copy);
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
        lines.add("# ============================================================================");
        lines.add("# 1. CORE PRESET & GLOBAL MULTIPLIERS / ОСНОВНОЙ ПРЕСЕТ И МНОЖИТЕЛИ");
        lines.add("# ============================================================================");
        lines.add("# How keen the mobs' senses are: off, subtle, default or hardcore.");
        lines.add("# Насколько острые чувства у мобов: off, subtle, default или hardcore.");
        lines.add("preset=" + preset.id());
        lines.add("");
        lines.add("# Global multiplier on all hearing distances (0 - 3, 1 = as configured below).");
        lines.add("# Общий множитель дальности слуха мобов (0 - 3, 1 = как задано ниже).");
        lines.add("hearing=" + format(hearing));
        lines.add("");
        lines.add("# Global multiplier on how far mobs see the player (0 - 3).");
        lines.add("# Общий множитель дальности зрения мобов (0 - 3).");
        lines.add("sight=" + format(sight));
        lines.add("");
        lines.add("# How strongly walls muffle sound (0 - 3: 0 = sound ignores walls, 1 = realistic, 2 = double muffling).");
        lines.add("# Насколько сильно стены глушат звук (0 - 3: 0 = стены не глушат, 1 = реалистично, 2 = вдвое сильнее).");
        lines.add("wall_muffling=" + format(wallMuffling));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 2. FIREARMS & GUN MODS (TaCZ, PointBlank, CGM, Scorched Guns) / ОГНЕСТРЕЛЬНОЕ ОРУЖИЕ");
        lines.add("# ============================================================================");
        lines.add("# Base audible distance in blocks for an unsuppressed gunshot (0 - 256, 0 = disabled).");
        lines.add("# Базовая дальность слышимости выстрела без глушителя в блоках (0 - 256, 0 = выкл).");
        lines.add("gunshot_range=" + format(gunshotRange));
        lines.add("");
        lines.add("# Base audible distance in blocks for a gunshot with a suppressor/silencer (0 - 256, 0 = completely silent).");
        lines.add("# Базовая дальность слышимости выстрела с глушителем в блоках (0 - 256, 0 = полностью бесшумно).");
        lines.add("gunshot_silenced_range=" + format(gunshotSilencedRange));
        lines.add("");
        lines.add("# Base audible distance in blocks where a bullet hits or ricochets (0 - 256, 0 = disabled).");
        lines.add("# Базовая дальность звука в точке попадания или рикошета пули в блоках (0 - 256, 0 = выкл).");
        lines.add("bullet_impact_range=" + format(bulletImpactRange));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 3. SOUND CATEGORIES (in blocks, 0 - 256, 0 = disabled) / КАТЕГОРИИ ЗВУКОВ (в блоках)");
        lines.add("# ============================================================================");
        lines.add("# Walking footsteps base distance in blocks, plus sneaking and sprinting multipliers.");
        lines.add("# Базовая дальность шагов в блоках, а также множители шагов в приседе и на бегу.");
        lines.add("step_range=" + format(stepRange));
        lines.add("sneak_step_multiplier=" + format(sneakStepMultiplier));
        lines.add("sprint_step_multiplier=" + format(sprintStepMultiplier));
        lines.add("");
        lines.add("# Breaking and placing blocks, opening doors/chests, melee combat, bows, explosions, bells, music, eating.");
        lines.add("# Ломание и установка блоков, двери, сундуки, ближний бой, луки/снаряды, взрывы, колокола, нотные блоки, еда.");
        lines.add("block_break_range=" + format(blockBreakRange));
        lines.add("block_place_range=" + format(blockPlaceRange));
        lines.add("door_range=" + format(doorRange));
        lines.add("container_range=" + format(containerRange));
        lines.add("combat_range=" + format(combatRange));
        lines.add("projectile_range=" + format(projectileRange));
        lines.add("explosion_range=" + format(explosionRange));
        lines.add("bell_range=" + format(bellRange));
        lines.add("note_block_range=" + format(noteBlockRange));
        lines.add("eat_range=" + format(eatRange));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 4. SIMPLE VOICE CHAT / ГОЛОСОВОЙ ЧАТ");
        lines.add("# ============================================================================");
        lines.add("# Whether mobs hear players talk in Simple Voice Chat: true or false.");
        lines.add("# Слышат ли мобы речь игроков в Simple Voice Chat: true или false.");
        lines.add("voice=" + voice);
        lines.add("");
        lines.add("# Base audible distance in blocks for normal speaking volume (0 - 256) and whisper multiplier (0 - 1).");
        lines.add("# Базовая дальность обычной речи в блоках (0 - 256) и множитель шёпота (0 - 1).");
        lines.add("voice_range=" + format(voiceRange));
        lines.add("voice_whisper_multiplier=" + format(voiceWhisperMultiplier));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 5. SIGHT, TORCHLIGHT & DARKNESS STEALTH / ЗРЕНИЕ, ФАКЕЛЫ И СКРЫТНОСТЬ В ТЕМНОТЕ");
        lines.add("# ============================================================================");
        lines.add("# Baseline daylight sight distance in blocks (0 - 256).");
        lines.add("# Базовая дальность зрения мобов днём в блоках (0 - 256).");
        lines.add("sight_range=" + format(sightRange));
        lines.add("");
        lines.add("# Whether holding a torch/lantern makes you visible from farther away, and whether darkness + sneaking hides you.");
        lines.add("# Делает ли факел/фонарь в руке игрока заметнее издалека и скрывает ли темнота в приседе.");
        lines.add("held_light_enabled=" + heldLightEnabled);
        lines.add("darkness_stealth_enabled=" + darknessStealthEnabled);
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 6. BLOOD SCENT & WOUNDED TRAIL / ЗАПАХ КРОВИ И СЛЕД РАНЕНОГО ИГРОКА");
        lines.add("# ============================================================================");
        lines.add("# Whether wounded players at or below scent_health_threshold (%) leave a blood trail for zombies and spiders.");
        lines.add("# Оставляют ли раненые игроки (при здоровье <= scent_health_threshold %) след крови для зомби и пауков.");
        lines.add("scent_enabled=" + scentEnabled);
        lines.add("scent_health_threshold=" + format(scentHealthThreshold));
        lines.add("scent_range=" + format(scentRange));
        lines.add("blood_particles=" + bloodParticles);
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 7. MOB INVESTIGATION & CUSTOM MOB LISTS / ПОВЕДЕНИЕ И СПИСКИ МОБОВ");
        lines.add("# ============================================================================");
        lines.add("# Mob walking speed multiplier for normal sounds and urgent sounds (gunshots, explosions, combat, bells, voice).");
        lines.add("# Множитель скорости ходьбы мобов на обычные звуки и на громкие тревожные звуки (выстрелы, взрывы, бой, голос).");
        lines.add("investigate_speed=" + format(investigateSpeed));
        lines.add("urgent_investigate_speed=" + format(urgentInvestigateSpeed));
        lines.add("");
        lines.add("# Comma-separated mob IDs or mod namespaces to add as listeners or to ignore completely.");
        lines.add("# Список ID мобов (или пространств имён модов) через запятую: дополнительные слушатели и игнорируемые мобы.");
        lines.add("# Example / Пример: extra_listeners=alexsmobs:murmur,mutantmonsters:*");
        lines.add("extra_listeners=" + String.join(",", extraListeners));
        lines.add("ignored_mobs=" + String.join(",", ignoredMobs));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 8. LANGUAGE & PER-WORLD PRESETS / ЯЗЫК И ПРЕСЕТЫ МИРОВ");
        lines.add("# ============================================================================");
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

    /** A multiplier or distance as the file and the replies show it: "1", "0.75", "44". */
    public static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        String s = String.format(Locale.ROOT, "%.2f", value);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }
}
