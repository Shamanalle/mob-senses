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
 * muffling, firearm / TaCZ gunshot distances (unsuppressed, silenced, bullet impact, and per-weapon
 * {@code gun.<id>.range} overrides), individual sound category ranges, weather & stealth gear
 * modifiers, Simple Voice Chat tuning, sight & darkness stealth toggles, blood scent & bleeding
 * trail settings, mob investigation & horde parameters, custom listener/ignored mob lists, and
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

    private static final List<String> OPTION_KEYS = Jv.listOf(
            "preset", "hearing", "sight", "wall_muffling",
            "gunshot_range", "gunshot_silenced_range", "bullet_impact_range",
            "step_range", "sneak_step_multiplier", "sprint_step_multiplier",
            "block_break_range", "block_place_range", "door_range", "container_range",
            "combat_range", "projectile_range", "explosion_range", "bell_range",
            "note_block_range", "eat_range",
            "rain_muffling", "thunder_muffling", "feather_falling_muffling",
            "voice", "voice_range", "voice_whisper_multiplier",
            "sight_range", "held_light_enabled", "darkness_stealth_enabled",
            "scent_enabled", "scent_health_threshold", "scent_range", "blood_particles",
            "investigate_speed", "urgent_investigate_speed", "investigation_seconds",
            "max_alerted_mobs", "horde_alert_range", "alert_cooldown_ms",
            "extra_listeners", "ignored_mobs", "language");

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
    private volatile Map<String, Double> gunRanges = Collections.emptyMap();
    private volatile Map<String, Double> gunSilencedRanges = Collections.emptyMap();

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

    // 4. Weather & Stealth Gear Modifiers
    private volatile double rainMuffling = 0.25;
    private volatile double thunderMuffling = 0.45;
    private volatile double featherFallingMuffling = 0.20;

    // 5. Simple Voice Chat
    private volatile boolean voice = true;
    private volatile double voiceRange = 16.0;
    private volatile double voiceWhisperMultiplier = 0.30;

    // 6. Sight, Torchlight & Darkness Stealth
    private volatile double sightRange = 16.0;
    private volatile boolean heldLightEnabled = true;
    private volatile boolean darknessStealthEnabled = true;

    // 7. Blood Scent & Wounded Trail
    private volatile boolean scentEnabled = true;
    private volatile double scentHealthThreshold = 50.0;
    private volatile double scentRange = 14.0;
    private volatile boolean bloodParticles = true;

    // 8. Mob Investigation, Horde & Custom Mob Lists
    private volatile double investigateSpeed = 1.0;
    private volatile double urgentInvestigateSpeed = 1.15;
    private volatile double investigationSeconds = 8.0;
    private volatile int maxAlertedMobs = 8;
    private volatile double hordeAlertRange = 0.0;
    private volatile long alertCooldownMs = 900L;
    private volatile Set<String> extraListeners = Collections.emptySet();
    private volatile Set<String> ignoredMobs = Collections.emptySet();

    // 9. Per-world presets
    /** World id ("minecraft:the_nether") to its own preset. */
    private volatile Map<String, Preset> worlds = Collections.emptyMap();

    /** @param file the settings file; null keeps everything in memory (tests) */
    public ServerSettings(Path file) {
        this.file = file;
    }

    public static List<String> optionKeys() {
        return OPTION_KEYS;
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

    public Map<String, Double> gunRanges() {
        return gunRanges;
    }

    public Map<String, Double> gunSilencedRanges() {
        return gunSilencedRanges;
    }

    /**
     * Resolves the base gunshot range for a specific weapon ID or sound ID (e.g. {@code tacz:ai_awp}),
     * checking {@code gun.<pattern>.range} / {@code gun.<pattern>.silenced_range} overrides first and
     * falling back to {@link #gunshotRange()} or {@link #gunshotSilencedRange()}.
     */
    public double gunRangeFor(String weaponOrSoundId, boolean silenced) {
        Map<String, Double> overrides = silenced ? gunSilencedRanges : gunRanges;
        double fallback = silenced ? gunshotSilencedRange : gunshotRange;
        if (overrides.isEmpty() || weaponOrSoundId == null || Jv.isBlank(weaponOrSoundId)) {
            return fallback;
        }
        String norm = weaponOrSoundId.trim().toLowerCase(Locale.ROOT);
        Double exact = overrides.get(norm);
        if (exact != null) {
            return exact;
        }
        String bestKey = null;
        Double bestVal = null;
        for (Map.Entry<String, Double> e : overrides.entrySet()) {
            String k = e.getKey();
            if (!k.isEmpty() && norm.contains(k)) {
                if (bestKey == null || k.length() > bestKey.length()) {
                    bestKey = k;
                    bestVal = e.getValue();
                }
            }
        }
        return bestVal != null ? bestVal : fallback;
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

    public double rainMuffling() {
        return rainMuffling;
    }

    public double thunderMuffling() {
        return thunderMuffling;
    }

    public double featherFallingMuffling() {
        return featherFallingMuffling;
    }

    /** Hearing range multiplier for current weather (1.0 in clear weather). */
    public double weatherMultiplier(boolean raining, boolean thundering) {
        if (thundering) {
            return Math.max(0.0, 1.0 - thunderMuffling);
        }
        if (raining) {
            return Math.max(0.0, 1.0 - rainMuffling);
        }
        return 1.0;
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

    public double investigationSeconds() {
        return investigationSeconds;
    }

    public int maxAlertedMobs() {
        return maxAlertedMobs;
    }

    public double hordeAlertRange() {
        return hordeAlertRange;
    }

    public long alertCooldownMs() {
        return alertCooldownMs;
    }

    public Set<String> extraListeners() {
        return extraListeners;
    }

    public Set<String> ignoredMobs() {
        return ignoredMobs;
    }

    public String language() {
        return language;
    }

    public Map<String, Preset> worlds() {
        return worlds;
    }

    /**
     * Checks whether the given entity ID (e.g. {@code "minecraft:zombie"} or {@code "zombie"}) is in
     * {@link #ignoredMobs()}. Supports both full IDs ({@code namespace:path}), short IDs ({@code path}),
     * and wildcard namespaces ({@code namespace:*}).
     */
    public boolean isIgnoredMob(String entityId) {
        return matchesMobSet(ignoredMobs, entityId);
    }

    /**
     * Checks whether the given entity ID is in {@link #extraListeners()}.
     */
    public boolean isExtraListener(String entityId) {
        return matchesMobSet(extraListeners, entityId);
    }

    private static boolean matchesMobSet(Set<String> set, String entityId) {
        if (set.isEmpty() || entityId == null || entityId.isEmpty()) {
            return false;
        }
        String norm = entityId.trim().toLowerCase(Locale.ROOT);
        if (set.contains(norm)) {
            return true;
        }
        int colon = norm.indexOf(':');
        if (colon >= 0) {
            String namespace = norm.substring(0, colon);
            String shortName = norm.substring(colon + 1);
            if (set.contains(shortName) || set.contains(namespace + ":*")) {
                return true;
            }
        } else {
            if (set.contains("minecraft:" + norm)) {
                return true;
            }
        }
        return false;
    }

    /** How far mobs hear in {@code world}: the world's preset times {@link #hearing()}. */
    public double effectiveHearing(String world) {
        return presetOf(world).hearing() * hearing;
    }

    /** How far mobs see in {@code world}: the world's preset times {@link #sight()}. */
    public double effectiveSight(String world) {
        return presetOf(world).sight() * sight;
    }

    public void setPreset(Preset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("preset is null");
        }
        this.preset = preset;
    }

    public void setHearing(double hearing) {
        this.hearing = clamp(hearing, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public void setSight(double sight) {
        this.sight = clamp(sight, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public void setWallMuffling(double wallMuffling) {
        this.wallMuffling = clamp(wallMuffling, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    public void setGunshotRange(double gunshotRange) {
        this.gunshotRange = checkRange("gunshot_range", gunshotRange);
    }

    public void setGunshotSilencedRange(double gunshotSilencedRange) {
        this.gunshotSilencedRange = checkRange("gunshot_silenced_range", gunshotSilencedRange);
    }

    public void setBulletImpactRange(double bulletImpactRange) {
        this.bulletImpactRange = checkRange("bullet_impact_range", bulletImpactRange);
    }

    public synchronized void setGunRange(String pattern, Double range) {
        if (pattern == null || Jv.isBlank(pattern)) {
            return;
        }
        Map<String, Double> copy = new TreeMap<>(gunRanges);
        String key = pattern.trim().toLowerCase(Locale.ROOT);
        if (range == null) {
            copy.remove(key);
        } else {
            copy.put(key, checkRange("gun." + key + ".range", range));
        }
        gunRanges = Collections.unmodifiableMap(copy);
    }

    public synchronized void setGunSilencedRange(String pattern, Double range) {
        if (pattern == null || Jv.isBlank(pattern)) {
            return;
        }
        Map<String, Double> copy = new TreeMap<>(gunSilencedRanges);
        String key = pattern.trim().toLowerCase(Locale.ROOT);
        if (range == null) {
            copy.remove(key);
        } else {
            copy.put(key, checkRange("gun." + key + ".silenced_range", range));
        }
        gunSilencedRanges = Collections.unmodifiableMap(copy);
    }

    public void setStepRange(double stepRange) {
        this.stepRange = checkRange("step_range", stepRange);
    }

    public void setSneakStepMultiplier(double sneakStepMultiplier) {
        this.sneakStepMultiplier = checkMultiplier("sneak_step_multiplier", sneakStepMultiplier);
    }

    public void setSprintStepMultiplier(double sprintStepMultiplier) {
        this.sprintStepMultiplier = checkMultiplier("sprint_step_multiplier", sprintStepMultiplier);
    }

    public void setBlockBreakRange(double blockBreakRange) {
        this.blockBreakRange = checkRange("block_break_range", blockBreakRange);
    }

    public void setBlockPlaceRange(double blockPlaceRange) {
        this.blockPlaceRange = checkRange("block_place_range", blockPlaceRange);
    }

    public void setDoorRange(double doorRange) {
        this.doorRange = checkRange("door_range", doorRange);
    }

    public void setContainerRange(double containerRange) {
        this.containerRange = checkRange("container_range", containerRange);
    }

    public void setCombatRange(double combatRange) {
        this.combatRange = checkRange("combat_range", combatRange);
    }

    public void setProjectileRange(double projectileRange) {
        this.projectileRange = checkRange("projectile_range", projectileRange);
    }

    public void setExplosionRange(double explosionRange) {
        this.explosionRange = checkRange("explosion_range", explosionRange);
    }

    public void setBellRange(double bellRange) {
        this.bellRange = checkRange("bell_range", bellRange);
    }

    public void setNoteBlockRange(double noteBlockRange) {
        this.noteBlockRange = checkRange("note_block_range", noteBlockRange);
    }

    public void setEatRange(double eatRange) {
        this.eatRange = checkRange("eat_range", eatRange);
    }

    public void setRainMuffling(double rainMuffling) {
        this.rainMuffling = checkBounded("rain_muffling", rainMuffling, 0.0, 1.0);
    }

    public void setThunderMuffling(double thunderMuffling) {
        this.thunderMuffling = checkBounded("thunder_muffling", thunderMuffling, 0.0, 1.0);
    }

    public void setFeatherFallingMuffling(double featherFallingMuffling) {
        this.featherFallingMuffling = checkBounded("feather_falling_muffling", featherFallingMuffling, 0.0, 1.0);
    }

    public void setVoice(boolean voice) {
        this.voice = voice;
    }

    public void setVoiceRange(double voiceRange) {
        this.voiceRange = checkRange("voice_range", voiceRange);
    }

    public void setVoiceWhisperMultiplier(double voiceWhisperMultiplier) {
        this.voiceWhisperMultiplier = checkBounded("voice_whisper_multiplier", voiceWhisperMultiplier, 0.0, 1.0);
    }

    public void setSightRange(double sightRange) {
        this.sightRange = checkRange("sight_range", sightRange);
    }

    public void setHeldLightEnabled(boolean heldLightEnabled) {
        this.heldLightEnabled = heldLightEnabled;
    }

    public void setDarknessStealthEnabled(boolean darknessStealthEnabled) {
        this.darknessStealthEnabled = darknessStealthEnabled;
    }

    public void setScentEnabled(boolean scentEnabled) {
        this.scentEnabled = scentEnabled;
    }

    public void setScentHealthThreshold(double scentHealthThreshold) {
        this.scentHealthThreshold = checkBounded("scent_health_threshold", scentHealthThreshold, 5.0, 100.0);
    }

    public void setScentRange(double scentRange) {
        this.scentRange = checkRange("scent_range", scentRange);
    }

    public void setBloodParticles(boolean bloodParticles) {
        this.bloodParticles = bloodParticles;
    }

    public void setInvestigateSpeed(double investigateSpeed) {
        this.investigateSpeed = checkBounded("investigate_speed", investigateSpeed, 0.5, 2.5);
    }

    public void setUrgentInvestigateSpeed(double urgentInvestigateSpeed) {
        this.urgentInvestigateSpeed = checkBounded("urgent_investigate_speed", urgentInvestigateSpeed, 0.5, 2.5);
    }

    public void setInvestigationSeconds(double investigationSeconds) {
        this.investigationSeconds = checkBounded("investigation_seconds", investigationSeconds, 2.0, 60.0);
    }

    public void setMaxAlertedMobs(int maxAlertedMobs) {
        if (maxAlertedMobs < 1 || maxAlertedMobs > 64) {
            throw new IllegalArgumentException("max_alerted_mobs=" + maxAlertedMobs);
        }
        this.maxAlertedMobs = maxAlertedMobs;
    }

    public void setHordeAlertRange(double hordeAlertRange) {
        this.hordeAlertRange = checkBounded("horde_alert_range", hordeAlertRange, 0.0, 64.0);
    }

    public void setAlertCooldownMs(long alertCooldownMs) {
        if (alertCooldownMs < 0L || alertCooldownMs > 10000L) {
            throw new IllegalArgumentException("alert_cooldown_ms=" + alertCooldownMs);
        }
        this.alertCooldownMs = alertCooldownMs;
    }

    public void setExtraListeners(Set<String> ids) {
        this.extraListeners = normalizeSet(ids);
    }

    public void setIgnoredMobs(Set<String> ids) {
        this.ignoredMobs = normalizeSet(ids);
    }

    public void setLanguage(String language) {
        this.language = Jv.isBlank(language) ? ServerText.DEFAULT_LANGUAGE : language.trim().toLowerCase(Locale.ROOT);
    }

    /** Sets a world's preset, or removes it when {@code preset} is null (the world inherits {@link #preset()}). */
    public synchronized void setWorldPreset(String world, Preset preset) {
        if (world == null || Jv.isBlank(world)) {
            throw new IllegalArgumentException("world is blank");
        }
        Map<String, Preset> copy = new TreeMap<>(worlds);
        if (preset == null) {
            copy.remove(world);
        } else {
            copy.put(world, preset);
        }
        worlds = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the current formatted string value of any config key (or {@code null} if unknown).
     */
    public String getOption(String rawKey) {
        if (rawKey == null || Jv.isBlank(rawKey)) {
            return null;
        }
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("gun.") && key.endsWith(".range")) {
            String gun = key.substring("gun.".length(), key.length() - ".range".length());
            Double v = gunRanges.get(gun);
            return v != null ? format(v) : format(gunshotRange);
        }
        if (key.startsWith("gun.") && key.endsWith(".silenced_range")) {
            String gun = key.substring("gun.".length(), key.length() - ".silenced_range".length());
            Double v = gunSilencedRanges.get(gun);
            return v != null ? format(v) : format(gunshotSilencedRange);
        }
        switch (key) {
            case "preset":
                return preset.id();
            case "hearing":
                return format(hearing);
            case "sight":
                return format(sight);
            case "wall_muffling":
                return format(wallMuffling);
            case "gunshot_range":
                return format(gunshotRange);
            case "gunshot_silenced_range":
                return format(gunshotSilencedRange);
            case "bullet_impact_range":
                return format(bulletImpactRange);
            case "step_range":
                return format(stepRange);
            case "sneak_step_multiplier":
                return format(sneakStepMultiplier);
            case "sprint_step_multiplier":
                return format(sprintStepMultiplier);
            case "block_break_range":
                return format(blockBreakRange);
            case "block_place_range":
                return format(blockPlaceRange);
            case "door_range":
                return format(doorRange);
            case "container_range":
                return format(containerRange);
            case "combat_range":
                return format(combatRange);
            case "projectile_range":
                return format(projectileRange);
            case "explosion_range":
                return format(explosionRange);
            case "bell_range":
                return format(bellRange);
            case "note_block_range":
                return format(noteBlockRange);
            case "eat_range":
                return format(eatRange);
            case "rain_muffling":
                return format(rainMuffling);
            case "thunder_muffling":
                return format(thunderMuffling);
            case "feather_falling_muffling":
                return format(featherFallingMuffling);
            case "voice":
                return String.valueOf(voice);
            case "voice_range":
                return format(voiceRange);
            case "voice_whisper_multiplier":
                return format(voiceWhisperMultiplier);
            case "sight_range":
                return format(sightRange);
            case "held_light_enabled":
                return String.valueOf(heldLightEnabled);
            case "darkness_stealth_enabled":
                return String.valueOf(darknessStealthEnabled);
            case "scent_enabled":
                return String.valueOf(scentEnabled);
            case "scent_health_threshold":
                return format(scentHealthThreshold);
            case "scent_range":
                return format(scentRange);
            case "blood_particles":
                return String.valueOf(bloodParticles);
            case "investigate_speed":
                return format(investigateSpeed);
            case "urgent_investigate_speed":
                return format(urgentInvestigateSpeed);
            case "investigation_seconds":
                return format(investigationSeconds);
            case "max_alerted_mobs":
                return String.valueOf(maxAlertedMobs);
            case "horde_alert_range":
                return format(hordeAlertRange);
            case "alert_cooldown_ms":
                return String.valueOf(alertCooldownMs);
            case "extra_listeners":
                return String.join(",", extraListeners);
            case "ignored_mobs":
                return String.join(",", ignoredMobs);
            case "language":
                return language;
            default:
                return null;
        }
    }

    /**
     * Updates a setting by key and string value. Returns {@code true} if valid and applied, or
     * {@code false} if the key or value is invalid.
     */
    public synchronized boolean setOption(String rawKey, String rawVal) {
        if (rawKey == null || rawVal == null || Jv.isBlank(rawKey) || Jv.isBlank(rawVal)) {
            return false;
        }
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        String val = rawVal.trim();
        try {
            if (key.startsWith("gun.") && key.endsWith(".range")) {
                String gun = key.substring("gun.".length(), key.length() - ".range".length());
                if (gun.isEmpty()) {
                    return false;
                }
                if ("default".equalsIgnoreCase(val) || "reset".equalsIgnoreCase(val)) {
                    setGunRange(gun, null);
                    return true;
                }
                setGunRange(gun, parseDouble(val));
                return true;
            }
            if (key.startsWith("gun.") && key.endsWith(".silenced_range")) {
                String gun = key.substring("gun.".length(), key.length() - ".silenced_range".length());
                if (gun.isEmpty()) {
                    return false;
                }
                if ("default".equalsIgnoreCase(val) || "reset".equalsIgnoreCase(val)) {
                    setGunSilencedRange(gun, null);
                    return true;
                }
                setGunSilencedRange(gun, parseDouble(val));
                return true;
            }
            switch (key) {
                case "preset": {
                    Preset p = Preset.byId(val);
                    if (p == null) {
                        return false;
                    }
                    setPreset(p);
                    return true;
                }
                case "hearing": {
                    double d = parseDouble(val);
                    if (!validMultiplier(d)) {
                        return false;
                    }
                    setHearing(d);
                    return true;
                }
                case "sight": {
                    double d = parseDouble(val);
                    if (!validMultiplier(d)) {
                        return false;
                    }
                    setSight(d);
                    return true;
                }
                case "wall_muffling": {
                    double d = parseDouble(val);
                    if (!validMultiplier(d)) {
                        return false;
                    }
                    setWallMuffling(d);
                    return true;
                }
                case "gunshot_range":
                    setGunshotRange(parseDouble(val));
                    return true;
                case "gunshot_silenced_range":
                    setGunshotSilencedRange(parseDouble(val));
                    return true;
                case "bullet_impact_range":
                    setBulletImpactRange(parseDouble(val));
                    return true;
                case "step_range":
                    setStepRange(parseDouble(val));
                    return true;
                case "sneak_step_multiplier":
                    setSneakStepMultiplier(parseDouble(val));
                    return true;
                case "sprint_step_multiplier":
                    setSprintStepMultiplier(parseDouble(val));
                    return true;
                case "block_break_range":
                    setBlockBreakRange(parseDouble(val));
                    return true;
                case "block_place_range":
                    setBlockPlaceRange(parseDouble(val));
                    return true;
                case "door_range":
                    setDoorRange(parseDouble(val));
                    return true;
                case "container_range":
                    setContainerRange(parseDouble(val));
                    return true;
                case "combat_range":
                    setCombatRange(parseDouble(val));
                    return true;
                case "projectile_range":
                    setProjectileRange(parseDouble(val));
                    return true;
                case "explosion_range":
                    setExplosionRange(parseDouble(val));
                    return true;
                case "bell_range":
                    setBellRange(parseDouble(val));
                    return true;
                case "note_block_range":
                    setNoteBlockRange(parseDouble(val));
                    return true;
                case "eat_range":
                    setEatRange(parseDouble(val));
                    return true;
                case "rain_muffling":
                    setRainMuffling(parseDouble(val));
                    return true;
                case "thunder_muffling":
                    setThunderMuffling(parseDouble(val));
                    return true;
                case "feather_falling_muffling":
                    setFeatherFallingMuffling(parseDouble(val));
                    return true;
                case "voice":
                    setVoice(parseStrictBool(val));
                    return true;
                case "voice_range":
                    setVoiceRange(parseDouble(val));
                    return true;
                case "voice_whisper_multiplier":
                    setVoiceWhisperMultiplier(parseDouble(val));
                    return true;
                case "sight_range":
                    setSightRange(parseDouble(val));
                    return true;
                case "held_light_enabled":
                    setHeldLightEnabled(parseStrictBool(val));
                    return true;
                case "darkness_stealth_enabled":
                    setDarknessStealthEnabled(parseStrictBool(val));
                    return true;
                case "scent_enabled":
                    setScentEnabled(parseStrictBool(val));
                    return true;
                case "scent_health_threshold":
                    setScentHealthThreshold(parseDouble(val));
                    return true;
                case "scent_range":
                    setScentRange(parseDouble(val));
                    return true;
                case "blood_particles":
                    setBloodParticles(parseStrictBool(val));
                    return true;
                case "investigate_speed":
                    setInvestigateSpeed(parseDouble(val));
                    return true;
                case "urgent_investigate_speed":
                    setUrgentInvestigateSpeed(parseDouble(val));
                    return true;
                case "investigation_seconds":
                    setInvestigationSeconds(parseDouble(val));
                    return true;
                case "max_alerted_mobs":
                    setMaxAlertedMobs((int) Math.round(parseDouble(val)));
                    return true;
                case "horde_alert_range":
                    setHordeAlertRange(parseDouble(val));
                    return true;
                case "alert_cooldown_ms":
                    setAlertCooldownMs(Math.round(parseDouble(val)));
                    return true;
                case "extra_listeners":
                    setExtraListeners("none".equalsIgnoreCase(val) || "clear".equalsIgnoreCase(val)
                            ? Collections.<String>emptySet()
                            : parseCsvSet(val));
                    return true;
                case "ignored_mobs":
                    setIgnoredMobs("none".equalsIgnoreCase(val) || "clear".equalsIgnoreCase(val)
                            ? Collections.<String>emptySet()
                            : parseCsvSet(val));
                    return true;
                case "language":
                    setLanguage(val);
                    return true;
                default:
                    return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static double parseDouble(String val) {
        return Double.parseDouble(val.trim().replace(',', '.'));
    }

    private static boolean parseStrictBool(String val) {
        String s = val.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "on".equals(s) || "yes".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "off".equals(s) || "no".equals(s) || "0".equals(s)) {
            return false;
        }
        throw new IllegalArgumentException("Not a boolean: " + val);
    }

    public static boolean validMultiplier(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER;
    }

    public static boolean validRange(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= MIN_RANGE && value <= MAX_RANGE;
    }

    private static double checkMultiplier(String name, double value) {
        if (!validMultiplier(value)) {
            throw new IllegalArgumentException(name + "=" + value);
        }
        return value;
    }

    private static double checkRange(String name, double value) {
        if (!validRange(value)) {
            throw new IllegalArgumentException(name + "=" + value);
        }
        return value;
    }

    private static double checkBounded(String name, double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + "=" + value);
        }
        return value;
    }

    /** Reads the file if it exists, or writes the defaults if it does not. */
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

        rainMuffling = boundedNumber(p, "rain_muffling", 0.0, 1.0, 0.25);
        thunderMuffling = boundedNumber(p, "thunder_muffling", 0.0, 1.0, 0.45);
        featherFallingMuffling = boundedNumber(p, "feather_falling_muffling", 0.0, 1.0, 0.20);

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
        investigationSeconds = boundedNumber(p, "investigation_seconds", 2.0, 60.0, 8.0);
        maxAlertedMobs = (int) Math.round(boundedNumber(p, "max_alerted_mobs", 1.0, 64.0, 8.0));
        hordeAlertRange = boundedNumber(p, "horde_alert_range", 0.0, 64.0, 0.0);
        alertCooldownMs = Math.round(boundedNumber(p, "alert_cooldown_ms", 0.0, 10000.0, 900.0));
        extraListeners = parseCsvSet(p.getProperty("extra_listeners"));
        ignoredMobs = parseCsvSet(p.getProperty("ignored_mobs"));

        String lang = p.getProperty("language", ServerText.DEFAULT_LANGUAGE).trim();
        language = Jv.isBlank(lang) ? ServerText.DEFAULT_LANGUAGE : lang.toLowerCase(Locale.ROOT);
        Map<String, Preset> w = new TreeMap<>();
        Map<String, Double> gr = new TreeMap<>();
        Map<String, Double> gsr = new TreeMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("world.") && key.endsWith(".preset")) {
                String world = key.substring("world.".length(), key.length() - ".preset".length());
                Preset wp = Preset.byId(p.getProperty(key));
                if (!world.isEmpty() && wp != null) {
                    w.put(world, wp);
                } else {
                    ModLog.warn(FILE_NAME + ": ignored " + key + "=" + p.getProperty(key));
                }
            } else if (key.startsWith("gun.") && key.endsWith(".silenced_range")) {
                String gun = key.substring("gun.".length(), key.length() - ".silenced_range".length()).trim().toLowerCase(Locale.ROOT);
                if (!gun.isEmpty()) {
                    double val = boundedNumber(p, key, MIN_RANGE, MAX_RANGE, -1.0);
                    if (val >= 0.0) {
                        gsr.put(gun, val);
                    }
                }
            } else if (key.startsWith("gun.") && key.endsWith(".range")) {
                String gun = key.substring("gun.".length(), key.length() - ".range".length()).trim().toLowerCase(Locale.ROOT);
                if (!gun.isEmpty()) {
                    double val = boundedNumber(p, key, MIN_RANGE, MAX_RANGE, -1.0);
                    if (val >= 0.0) {
                        gr.put(gun, val);
                    }
                }
            }
        }
        worlds = Collections.unmodifiableMap(w);
        gunRanges = Collections.unmodifiableMap(gr);
        gunSilencedRanges = Collections.unmodifiableMap(gsr);
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
        lines.add("# Optional per-weapon overrides by gun ID or keyword (e.g. gun.tacz\\:ai_awp.range=85, gun.glock.silenced_range=8).");
        lines.add("# Индивидуальная дальность для конкретного оружия (например: gun.tacz\\:ai_awp.range=85, gun.glock.silenced_range=8).");
        for (Map.Entry<String, Double> e : gunRanges.entrySet()) {
            lines.add("gun." + e.getKey().replace(":", "\\:") + ".range=" + format(e.getValue()));
        }
        for (Map.Entry<String, Double> e : gunSilencedRanges.entrySet()) {
            lines.add("gun." + e.getKey().replace(":", "\\:") + ".silenced_range=" + format(e.getValue()));
        }
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
        lines.add("# 4. WEATHER & STEALTH GEAR MODIFIERS / ПОГОДА И ЭКИПИРОВКА СКРЫТНОСТИ");
        lines.add("# ============================================================================");
        lines.add("# Fraction of sound muffled outdoors by rain (0 - 1, 0.25 = 25% quieter) and thunderstorms (0.45 = 45% quieter).");
        lines.add("# Насколько дождь (0.25 = на 25% тише) и гроза (0.45 = на 45% тише) маскируют звуки (0 = выкл).");
        lines.add("rain_muffling=" + format(rainMuffling));
        lines.add("thunder_muffling=" + format(thunderMuffling));
        lines.add("");
        lines.add("# Footstep noise reduction when wearing boots/leggings with Feather Falling or Swift Sneak (0 - 1, 0.2 = 20% quieter).");
        lines.add("# Снижение шума шагов от зачарований «Невесомость» (Feather Falling) и «Проворство» (Swift Sneak) (0 - 1).");
        lines.add("feather_falling_muffling=" + format(featherFallingMuffling));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 5. SIMPLE VOICE CHAT / ГОЛОСОВОЙ ЧАТ");
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
        lines.add("# 6. SIGHT, TORCHLIGHT & DARKNESS STEALTH / ЗРЕНИЕ, ФАКЕЛЫ И СКРЫТНОСТЬ В ТЕМНОТЕ");
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
        lines.add("# 7. BLOOD SCENT & WOUNDED TRAIL / ЗАПАХ КРОВИ И СЛЕД РАНЕНОГО ИГРОКА");
        lines.add("# ============================================================================");
        lines.add("# Whether wounded players at or below scent_health_threshold (%) leave a blood trail for zombies and spiders.");
        lines.add("# Оставляют ли раненые игроки (при здоровье <= scent_health_threshold %) след крови для зомби и пауков.");
        lines.add("scent_enabled=" + scentEnabled);
        lines.add("scent_health_threshold=" + format(scentHealthThreshold));
        lines.add("scent_range=" + format(scentRange));
        lines.add("blood_particles=" + bloodParticles);
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 8. MOB INVESTIGATION, HORDE & CUSTOM MOB LISTS / ПОВЕДЕНИЕ, ОРДА И СПИСКИ МОБОВ");
        lines.add("# ============================================================================");
        lines.add("# Mob walking speed multiplier for normal sounds and urgent sounds (gunshots, explosions, combat, bells, voice).");
        lines.add("# Множитель скорости ходьбы мобов на обычные звуки и на громкие тревожные звуки (выстрелы, взрывы, бой, голос).");
        lines.add("investigate_speed=" + format(investigateSpeed));
        lines.add("urgent_investigate_speed=" + format(urgentInvestigateSpeed));
        lines.add("investigation_seconds=" + format(investigationSeconds));
        lines.add("");
        lines.add("# Maximum mobs alerted by a single sound (1 - 64), horde chain-alert radius in blocks (0 - 64, 0 = off), and per-mob cooldown in ms.");
        lines.add("# Максимум мобов на один звук (1 - 64), радиус подтягивания соседних мобов ордой в блоках (0 - 64, 0 = выкл) и кулдаун в мс.");
        lines.add("max_alerted_mobs=" + maxAlertedMobs);
        lines.add("horde_alert_range=" + format(hordeAlertRange));
        lines.add("alert_cooldown_ms=" + alertCooldownMs);
        lines.add("");
        lines.add("# Comma-separated mob IDs or mod namespaces to add as listeners or to ignore completely.");
        lines.add("# Список ID мобов (или пространств имён модов) через запятую: дополнительные слушатели и игнорируемые мобы.");
        lines.add("# Example / Пример: extra_listeners=alexsmobs:murmur,mutantmonsters:*");
        lines.add("extra_listeners=" + String.join(",", extraListeners));
        lines.add("ignored_mobs=" + String.join(",", ignoredMobs));
        lines.add("");
        lines.add("# ============================================================================");
        lines.add("# 9. LANGUAGE & PER-WORLD PRESETS / ЯЗЫК И ПРЕСЕТЫ МИРОВ");
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
