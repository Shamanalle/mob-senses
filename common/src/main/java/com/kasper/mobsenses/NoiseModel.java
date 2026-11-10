package com.kasper.mobsenses;

import java.util.Locale;

/**
 * The hearing model of Mob Senses: maps game events (steps with posture and surface material, breaking
 * and placing blocks, doors, containers, fights, projectiles, explosions, bells, note blocks, eating,
 * and player voice) to their base audible radius in blocks, scales them by the world's {@link Preset}
 * and the server's hearing multiplier, and attenuates them through walls.
 */
public final class NoiseModel {

    /** Categories of game noises that mobs can hear. */
    public enum Kind {
        STEP(8.0, true),
        BLOCK_BREAK(18.0, false),
        BLOCK_PLACE(12.0, false),
        DOOR(14.0, false),
        CONTAINER(12.0, false),
        COMBAT(16.0, false),
        PROJECTILE(15.0, false),
        GUNSHOT(44.0, false),
        EXPLOSION(48.0, false),
        BELL(40.0, false),
        NOTE_BLOCK(28.0, false),
        EAT(8.0, true),
        VOICE(16.0, true);

        private final double defaultRange;
        private final boolean requiresPlayerSource;

        Kind(double defaultRange, boolean requiresPlayerSource) {
            this.defaultRange = defaultRange;
            this.requiresPlayerSource = requiresPlayerSource;
        }

        /** Base audible radius in blocks in open air (Default preset, ×1 hearing). */
        public double defaultRange() {
            return defaultRange;
        }

        /**
         * Whether this noise category is personal to an entity (steps, eating, voice) and should only alert
         * mobs when made by a player, so mobs do not endlessly chase other mobs' idle steps.
         */
        public boolean requiresPlayerSource() {
            return requiresPlayerSource;
        }
    }

    /** How the player is moving when making a step or landing noise. */
    public enum Posture {
        SNEAKING(0.25),
        WALKING(1.00),
        SPRINTING(1.75);

        private final double factor;

        Posture(double factor) {
            this.factor = factor;
        }

        public double factor() {
            return factor;
        }
    }

    /** Exponential decay rate per unit of stone-equivalent wall thickness. */
    public static final double WALL_DECAY_RATE = 0.45;

    /** Minimum audible range (in blocks) worth queuing for mob investigation. */
    public static final double MIN_AUDIBLE_RANGE = 1.0;

    private NoiseModel() {
    }

    /** Strips {@code minecraft:} and lowercases the event id. */
    public static String normalizeEvent(String eventId) {
        if (eventId == null) {
            return "";
        }
        String s = eventId.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    /**
     * Classifies a game event id ({@code "minecraft:step"}, {@code "block_destroy"}, {@code "explode"}...)
     * into a {@link Kind}, or {@code null} when the event makes no noise mobs care about (such as sculk
     * sensor clicks).
     */
    public static Kind classifyEvent(String eventId) {
        String id = normalizeEvent(eventId);
        switch (id) {
            case "step":
            case "hit_ground":
            case "swim":
            case "splash":
            case "elytra_glide":
            case "teleport":
                return Kind.STEP;
            case "block_destroy":
            case "fluid_pickup":
                return Kind.BLOCK_BREAK;
            case "block_place":
            case "block_change":
            case "block_activate":
            case "block_deactivate":
            case "block_press":
            case "block_unpress":
            case "block_switch":
            case "piston_extend":
            case "piston_contract":
            case "dispense_fail":
            case "flint_and_steel_use":
            case "Block_attach":
            case "block_attach":
            case "block_detach":
            case "fluid_place":
                return Kind.BLOCK_PLACE;
            case "block_open":
            case "block_close":
                return Kind.DOOR;
            case "container_open":
            case "container_close":
            case "shulker_open":
            case "shulker_close":
                return Kind.CONTAINER;
            case "entity_damage":
            case "entity_die":
            case "entity_action":
            case "entity_interact":
            case "mob_interact":
            case "ravager_roar":
            case "shear":
                return Kind.COMBAT;
            case "projectile_shoot":
            case "projectile_land":
            case "fishing_rod_cast":
            case "fishing_rod_reel_in":
                return Kind.PROJECTILE;
            case "gunshot":
            case "gunshot_silenced":
            case "bullet_impact":
                return Kind.GUNSHOT;
            case "explode":
            case "lightning_strike":
            case "prime_fuse":
                return Kind.EXPLOSION;
            case "bell_ring":
            case "ring_bell":
                return Kind.BELL;
            case "note_block_play":
            case "instrument_play":
            case "jukebox_play":
                return Kind.NOTE_BLOCK;
            case "eat":
            case "drink":
            case "drinking_finish":
            case "equip":
            case "item_interact_finish":
                return Kind.EAT;
            case "voice":
                return Kind.VOICE;
            default:
                return null;
        }
    }

    /**
     * Multiplier for footsteps on a specific block id and acoustic material: wool and carpet muffle steps,
     * gravel and crunchy blocks give the player away.
     */
    public static double surfaceStepFactor(AcousticMaterial material, String blockId) {
        double base = material != null ? material.stepFactor() : 1.0;
        if (blockId == null || blockId.isEmpty()) {
            return base;
        }
        String name = normalizeEvent(blockId);
        if (name.contains("wool") || name.contains("carpet")) {
            return AcousticMaterial.WOOL.stepFactor();
        }
        if (name.contains("gravel") || name.contains("amethyst") || name.contains("chain")
                || name.contains("copper_grate") || name.contains("dead_bush") || name.contains("dry_")) {
            return Math.max(base, 1.50);
        }
        if (name.contains("sand") || name.contains("scaffolding") || name.contains("bone_block")) {
            return Math.max(base, 1.20);
        }
        if (name.contains("moss") || name.contains("sponge") || name.contains("snow") || name.contains("hay")) {
            return Math.min(base, AcousticMaterial.SOFT.stepFactor());
        }
        return base;
    }

    /**
     * Base audible radius (in blocks, before preset, hearing multiplier and walls) for a game event,
     * using the active {@link MobSenses#settings()}.
     */
    public static double baseRange(String eventId, Posture posture, AcousticMaterial material, String blockId) {
        return baseRange(eventId, posture, material, blockId, MobSenses.settings());
    }

    /**
     * Base audible radius (in blocks, before preset, hearing multiplier and walls) for a game event,
     * using the configured category ranges and posture multipliers in {@code settings}.
     *
     * @param eventId   the event id, e.g. {@code "minecraft:step"} or {@code "mobsenses:gunshot"}
     * @param posture   the player's posture ({@code null} defaults to {@link Posture#WALKING})
     * @param material  acoustic material of the block under feet or the block acted upon ({@code null} for default)
     * @param blockId   optional block id (e.g. {@code "minecraft:gravel"}, {@code "minecraft:white_wool"})
     * @param settings  server settings supplying custom base ranges ({@code null} uses built-in defaults)
     * @return audible radius in blocks, or {@code 0.0} when silent or disabled ({@code 0})
     */
    public static double baseRange(String eventId, Posture posture, AcousticMaterial material, String blockId,
                                   ServerSettings settings) {
        Kind kind = classifyEvent(eventId);
        if (kind == null) {
            return 0.0;
        }
        String id = normalizeEvent(eventId);
        Posture pos = posture != null ? posture : Posture.WALKING;
        switch (kind) {
            case STEP: {
                double stepCfg = settings != null ? settings.stepRange() : Kind.STEP.defaultRange();
                if (stepCfg <= 0.0) {
                    return 0.0;
                }
                double scale = stepCfg / Kind.STEP.defaultRange();
                double base;
                if ("hit_ground".equals(id)) {
                    base = 12.0 * scale;
                } else if ("splash".equals(id)) {
                    base = 12.0 * scale;
                } else if ("swim".equals(id)) {
                    base = 6.0 * scale;
                } else {
                    base = stepCfg;
                }
                double posFactor;
                if (pos == Posture.SNEAKING) {
                    posFactor = settings != null ? settings.sneakStepMultiplier() : Posture.SNEAKING.factor();
                } else if (pos == Posture.SPRINTING) {
                    posFactor = settings != null ? settings.sprintStepMultiplier() : Posture.SPRINTING.factor();
                } else {
                    posFactor = Posture.WALKING.factor();
                }
                return base * posFactor * surfaceStepFactor(material, blockId);
            }
            case BLOCK_BREAK: {
                double base = settings != null ? settings.blockBreakRange() : Kind.BLOCK_BREAK.defaultRange();
                double factor = material != null ? material.blockFactor() : 1.0;
                return base * factor;
            }
            case BLOCK_PLACE: {
                double placeCfg = settings != null ? settings.blockPlaceRange() : Kind.BLOCK_PLACE.defaultRange();
                double base = ("block_activate".equals(id) || "block_deactivate".equals(id) || "block_change".equals(id))
                        ? placeCfg * (10.0 / 12.0) : placeCfg;
                double factor = material != null ? material.blockFactor() : 1.0;
                return base * factor;
            }
            case DOOR: {
                double base = settings != null ? settings.doorRange() : Kind.DOOR.defaultRange();
                double factor = material == AcousticMaterial.METAL ? 1.25 : 1.0;
                return base * factor;
            }
            case CONTAINER:
                return settings != null ? settings.containerRange() : Kind.CONTAINER.defaultRange();
            case COMBAT: {
                double base = settings != null ? settings.combatRange() : Kind.COMBAT.defaultRange();
                return "entity_die".equals(id) ? base * (18.0 / 16.0) : base;
            }
            case PROJECTILE: {
                double base = settings != null ? settings.projectileRange() : Kind.PROJECTILE.defaultRange();
                return "projectile_land".equals(id) ? base * (16.0 / 15.0) : base * (12.0 / 15.0);
            }
            case GUNSHOT: {
                if ("gunshot_silenced".equals(id)) {
                    return settings != null ? settings.gunRangeFor(blockId, true) : 16.0;
                }
                if ("bullet_impact".equals(id)) {
                    return settings != null ? settings.bulletImpactRange() : 20.0;
                }
                return settings != null ? settings.gunRangeFor(blockId, false) : Kind.GUNSHOT.defaultRange();
            }
            case EXPLOSION: {
                double base = settings != null ? settings.explosionRange() : Kind.EXPLOSION.defaultRange();
                return "prime_fuse".equals(id) ? base * (16.0 / 48.0) : base;
            }
            case BELL:
                return settings != null ? settings.bellRange() : Kind.BELL.defaultRange();
            case NOTE_BLOCK: {
                double base = settings != null ? settings.noteBlockRange() : Kind.NOTE_BLOCK.defaultRange();
                return "jukebox_play".equals(id) ? base * (24.0 / 28.0) : base;
            }
            case EAT: {
                double base = settings != null ? settings.eatRange() : Kind.EAT.defaultRange();
                return base * (pos == Posture.SNEAKING ? 0.6 : 1.0);
            }
            case VOICE:
                return settings != null ? settings.voiceRange() : Kind.VOICE.defaultRange();
            default:
                return 0.0;
        }
    }

    /**
     * Scales a base audible radius by the world's {@link Preset} and the server's {@code hearing} multiplier.
     */
    public static double effectiveRange(double baseRange, Preset preset, double hearingMultiplier) {
        if (baseRange <= 0.0 || preset == null || preset == Preset.OFF || !(hearingMultiplier > 0.0)) {
            return 0.0;
        }
        return baseRange * preset.hearing() * hearingMultiplier;
    }

    /**
     * Fraction of a sound's range that survives passing through {@code wallThickness} stone-equivalent
     * blocks of wall, in {@code (0, 1]}.
     * <p>
     * Open air ({@code 0}) gives {@code 1.0}, an open wooden door ({@code 0.06}) gives {@code ~0.97},
     * glass ({@code 0.4}) gives {@code ~0.84}, wood ({@code 0.7}) gives {@code ~0.73}, one stone wall
     * ({@code 1.0}) gives {@code ~0.64}, wool ({@code 1.4}) gives {@code ~0.53}.
     */
    public static double occlusionFactor(double wallThickness) {
        if (!(wallThickness > 0.0)) {
            return 1.0;
        }
        return Math.exp(-WALL_DECAY_RATE * wallThickness);
    }

    /**
     * Final audible range after wall attenuation, scaled by {@link ServerSettings#wallMuffling()}.
     */
    public static double audibleRange(double effectiveRange, double wallThickness) {
        ServerSettings s = MobSenses.settings();
        double muffling = s != null ? s.wallMuffling() : 1.0;
        return audibleRange(effectiveRange, wallThickness, muffling);
    }

    /**
     * Final audible range after wall attenuation with explicit {@code wallMuffling} multiplier.
     * When {@code wallMuffling <= 0}, walls do not block sound; when {@code wallThickness * wallMuffling}
     * reaches {@link RayBundle#MAX_RAY_THICKNESS}, the sound is completely blocked ({@code 0.0}).
     */
    public static double audibleRange(double effectiveRange, double wallThickness, double wallMuffling) {
        if (effectiveRange <= 0.0) {
            return 0.0;
        }
        if (!(wallMuffling > 0.0) || !(wallThickness > 0.0)) {
            return effectiveRange;
        }
        double scaledThickness = wallThickness * wallMuffling;
        if (scaledThickness >= RayBundle.MAX_RAY_THICKNESS) {
            return 0.0;
        }
        return effectiveRange * occlusionFactor(scaledThickness);
    }

    /**
     * Whether {@code itemId} (e.g. {@code "tacz:modern_kinetic_gun"}, {@code "pointblank:glock17"},
     * {@code "cgm:rifle"}, {@code "scguns:iron_musket"}) is a firearm from a gun mod.
     */
    public static boolean isFirearmItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        String full = itemId.trim().toLowerCase(Locale.ROOT);
        String name = normalizeEvent(full);
        if (name.contains("gunpowder") || name.contains("ammo") || name.contains("bullet")
                || name.contains("magazine") || name.contains("attachment") || name.contains("scope")
                || name.contains("sight") || name.contains("grip") || name.contains("stock")
                || name.contains("muzzle") || name.contains("workbench") || name.contains("table")
                || name.contains("casing") || name.contains("blueprint") || name.contains("ingot")
                || name.contains("nugget") || name.contains("ore") || name.contains("block")
                || name.contains("water_gun") || name.contains("potato_cannon")) {
            return false;
        }
        if (full.startsWith("tacz:") || full.startsWith("pointblank:")
                || full.startsWith("cgm:") || full.startsWith("scguns:")
                || full.startsWith("musketmod:") || full.startsWith("flansmod:")
                || full.startsWith("ava:") || full.startsWith("techguns:")) {
            return true;
        }
        return name.contains("kinetic_gun")
                || name.contains("firearm")
                || name.contains("pistol")
                || name.contains("revolver")
                || name.contains("rifle")
                || name.contains("sniper")
                || name.contains("shotgun")
                || name.contains("submachine")
                || name.contains("smg")
                || name.contains("carbine")
                || name.contains("musket")
                || name.contains("blunderbuss")
                || name.contains("minigun")
                || name.contains("bazooka")
                || name.contains("rocket_launcher")
                || name.contains("grenade_launcher")
                || name.equals("gun")
                || name.endsWith("_gun")
                || name.startsWith("gun_");
    }

    /**
     * Whether a firearm item or its NBT/DataComponent summary indicates a suppressor/silencer is attached
     * (such as TaCZ's {@code tacz:muzzle_silence_*} attachments).
     */
    public static boolean isSilencedFirearm(String itemId, String tagSummary) {
        if (itemId != null) {
            String lowId = itemId.toLowerCase(Locale.ROOT);
            if (lowId.contains("silence") || lowId.contains("suppress") || lowId.contains("subsonic")) {
                return true;
            }
        }
        if (tagSummary != null && !tagSummary.isEmpty()) {
            String lowTag = tagSummary.toLowerCase(Locale.ROOT);
            return lowTag.contains("silence")
                    || lowTag.contains("silencer")
                    || lowTag.contains("suppress")
                    || lowTag.contains("suppressor")
                    || lowTag.contains("subsonic");
        }
        return false;
    }

    /**
     * Whether {@code entityTypeId} (e.g. {@code "tacz:bullet"}, {@code "cgm:projectile"},
     * {@code "scguns:bullet"}) is a firearm projectile or explosive ordnance from a gun mod.
     */
    public static boolean isBulletEntity(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return false;
        }
        String full = entityTypeId.trim().toLowerCase(Locale.ROOT);
        String name = normalizeEvent(full);
        if (full.startsWith("tacz:") || full.startsWith("pointblank:")
                || full.startsWith("cgm:") || full.startsWith("scguns:")
                || full.startsWith("musketmod:")) {
            return name.contains("bullet") || name.contains("projectile")
                    || name.contains("pellet") || name.contains("shell")
                    || name.contains("rocket") || name.contains("missile")
                    || name.contains("grenade") || name.contains("ammo");
        }
        return name.equals("bullet")
                || name.endsWith("_bullet")
                || name.startsWith("bullet_")
                || name.contains("kinetic_bullet")
                || name.equals("pellet")
                || name.endsWith("_pellet")
                || name.equals("slug")
                || name.endsWith("_slug");
    }
}
