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
                return Kind.CONTAINER;
            case "entity_damage":
            case "entity_die":
            case "entity_action":
            case "entity_interact":
            case "shear":
                return Kind.COMBAT;
            case "projectile_shoot":
            case "projectile_land":
                return Kind.PROJECTILE;
            case "explode":
            case "lightning_strike":
            case "prime_fuse":
                return Kind.EXPLOSION;
            case "bell_ring":
                return Kind.BELL;
            case "note_block_play":
            case "instrument_play":
            case "jukebox_play":
                return Kind.NOTE_BLOCK;
            case "eat":
            case "drink":
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
     * Base audible radius (in blocks, before preset, hearing multiplier and walls) for a game event.
     *
     * @param eventId   the event id, e.g. {@code "minecraft:step"} or {@code "minecraft:explode"}
     * @param posture   the player's posture ({@code null} defaults to {@link Posture#WALKING})
     * @param material  acoustic material of the block under feet or the block acted upon ({@code null} for default)
     * @param blockId   optional block id (e.g. {@code "minecraft:gravel"}, {@code "minecraft:white_wool"})
     * @return audible radius in blocks, or {@code 0.0} when silent
     */
    public static double baseRange(String eventId, Posture posture, AcousticMaterial material, String blockId) {
        Kind kind = classifyEvent(eventId);
        if (kind == null) {
            return 0.0;
        }
        String id = normalizeEvent(eventId);
        Posture pos = posture != null ? posture : Posture.WALKING;
        switch (kind) {
            case STEP: {
                double base;
                if ("hit_ground".equals(id)) {
                    base = 12.0;
                } else if ("splash".equals(id)) {
                    base = 12.0;
                } else if ("swim".equals(id)) {
                    base = 6.0;
                } else {
                    base = Kind.STEP.defaultRange();
                }
                return base * pos.factor() * surfaceStepFactor(material, blockId);
            }
            case BLOCK_BREAK: {
                double factor = material != null ? material.blockFactor() : 1.0;
                return Kind.BLOCK_BREAK.defaultRange() * factor;
            }
            case BLOCK_PLACE: {
                double base = ("block_activate".equals(id) || "block_deactivate".equals(id) || "block_change".equals(id))
                        ? 10.0 : Kind.BLOCK_PLACE.defaultRange();
                double factor = material != null ? material.blockFactor() : 1.0;
                return base * factor;
            }
            case DOOR: {
                double factor = material == AcousticMaterial.METAL ? 1.25 : 1.0;
                return Kind.DOOR.defaultRange() * factor;
            }
            case CONTAINER:
                return Kind.CONTAINER.defaultRange();
            case COMBAT:
                return "entity_die".equals(id) ? 18.0 : Kind.COMBAT.defaultRange();
            case PROJECTILE:
                return "projectile_land".equals(id) ? 16.0 : 12.0;
            case EXPLOSION:
                return "prime_fuse".equals(id) ? 16.0 : Kind.EXPLOSION.defaultRange();
            case BELL:
                return Kind.BELL.defaultRange();
            case NOTE_BLOCK:
                return "jukebox_play".equals(id) ? 24.0 : Kind.NOTE_BLOCK.defaultRange();
            case EAT:
                return Kind.EAT.defaultRange() * (pos == Posture.SNEAKING ? 0.6 : 1.0);
            case VOICE:
                return Kind.VOICE.defaultRange();
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
     * Final audible range after wall attenuation. When the thickness reaches {@link RayBundle#MAX_RAY_THICKNESS},
     * the sound is completely blocked ({@code 0.0}).
     */
    public static double audibleRange(double effectiveRange, double wallThickness) {
        if (effectiveRange <= 0.0 || wallThickness >= RayBundle.MAX_RAY_THICKNESS) {
            return 0.0;
        }
        return effectiveRange * occlusionFactor(wallThickness);
    }
}
