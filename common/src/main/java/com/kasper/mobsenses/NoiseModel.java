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
            case "decoy_land":
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
     * Loudness multiplier for an item thrown as a decoy and hitting a surface: bells ring farthest,
     * metal gear clatters louder than stone/glass, and feathers, wool, paper or snowballs land softly.
     */
    public static double decoyItemMultiplier(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 1.0;
        }
        String name = normalizeEvent(itemId);
        if (name.contains("bell")) {
            return 1.50;
        }
        if (name.contains("wool") || name.contains("carpet") || name.contains("feather")
                || name.contains("moss") || name.contains("paper") || name.contains("sponge")
                || name.contains("leaf") || name.contains("leaves") || name.contains("string")
                || name.contains("kelp") || name.contains("hay") || name.contains("banner")) {
            return 0.55;
        }
        if (name.contains("snowball") || name.contains("slime") || name.contains("clay")
                || name.contains("mud") || name.contains("dirt") || name.contains("sand")
                || name.contains("seed") || name.contains("sapling") || hasWordToken(name, "stick")
                || name.contains("plank") || name.contains("flower") || name.contains("wheat")
                || name.contains("bread") || name.contains("apple") || name.contains("meat")
                || name.contains("flesh") || name.contains("leather")) {
            return 0.85;
        }
        if (name.contains("iron") || name.contains("gold") || name.contains("copper")
                || name.contains("netherite") || name.contains("diamond") || name.contains("metal")
                || name.contains("steel") || name.contains("brass") || name.contains("chain")
                || name.contains("anvil") || name.contains("bucket")
                || hasWordToken(name, "rail") || name.endsWith("_rail")
                || name.contains("minecart") || name.contains("cauldron") || name.contains("hopper")
                || name.contains("compass") || name.contains("clock") || name.contains("trident")
                || name.contains("sword") || name.contains("pickaxe")
                || hasWordToken(name, "axe") || name.endsWith("_axe")
                || name.contains("shovel") || name.contains("hoe") || name.contains("helmet")
                || name.contains("chestplate") || name.contains("leggings") || name.contains("boots")
                || name.contains("shield") || name.contains("shears")
                || name.contains("flint_and_steel")) {
            return 1.30;
        }
        if (name.contains("stone") || name.contains("cobble") || name.contains("brick")
                || name.contains("glass") || name.contains("bottle") || name.contains("amethyst")
                || name.contains("flint") || name.contains("quartz") || name.contains("prismarine")
                || name.contains("obsidian") || name.contains("deepslate") || name.contains("basalt")
                || name.contains("blackstone") || name.contains("ender_pearl")
                || name.contains("fire_charge") || hasWordToken(name, "pot") || name.endsWith("_pot")) {
            return 1.15;
        }
        return 1.0;
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
        AcousticMaterial effectiveMat = material;
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
                double surfaceMult = (settings == null || settings.surfaceSoundScaling())
                        ? surfaceStepFactor(effectiveMat, blockId) : 1.0;
                return base * posFactor * surfaceMult;
            }
            case BLOCK_BREAK: {
                double base = settings != null ? settings.blockBreakRange() : Kind.BLOCK_BREAK.defaultRange();
                double factor = (settings == null || settings.surfaceSoundScaling()) && effectiveMat != null
                        ? effectiveMat.blockFactor() : 1.0;
                return base * factor;
            }
            case BLOCK_PLACE: {
                double placeCfg = settings != null ? settings.blockPlaceRange() : Kind.BLOCK_PLACE.defaultRange();
                double base = ("block_activate".equals(id) || "block_deactivate".equals(id) || "block_change".equals(id))
                        ? placeCfg * (10.0 / 12.0) : placeCfg;
                double factor = (settings == null || settings.surfaceSoundScaling()) && effectiveMat != null
                        ? effectiveMat.blockFactor() : 1.0;
                return base * factor;
            }
            case DOOR: {
                double base = settings != null ? settings.doorRange() : Kind.DOOR.defaultRange();
                double factor = (settings == null || settings.surfaceSoundScaling()) && effectiveMat == AcousticMaterial.METAL
                        ? 1.25 : 1.0;
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
                if ("decoy_land".equals(id)) {
                    boolean scaleSurface = settings == null || settings.surfaceSoundScaling();
                    double matFactor = scaleSurface && effectiveMat != null && effectiveMat != AcousticMaterial.AIR
                            ? effectiveMat.blockFactor() : 1.0;
                    double itemFactor = scaleSurface ? decoyItemMultiplier(blockId) : 1.0;
                    return base * (18.0 / 15.0) * matFactor * itemFactor;
                }
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
        if (s != null && !s.throughWallHearing() && wallThickness >= 0.85) {
            return 0.0;
        }
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
     * {@code "cgm:rifle"}, {@code "scguns:iron_musket"}, {@code "superbwarfare:m4a1"},
     * {@code "jeg:assault_rifle"}) is a firearm from a gun mod.
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
                || name.contains("water_gun") || name.contains("potato_cannon")
                || name.contains("armor") || name.contains("helmet") || name.contains("chestplate")
                || name.contains("leggings") || name.contains("boots") || name.contains("parachute")
                || name.contains("wrench") || name.contains("medkit") || name.contains("bandage")
                || name.contains("drone") || name.contains("vehicle") || name.contains("crate")
                || name.contains("bayonet") || name.contains("speedloader")
                || name.contains("respirator") || name.contains("gas_mask")
                || name.contains("brass_mask") || name.contains("goggles")
                || name.contains("spawn_egg")
                || name.contains("pickaxe") || name.contains("shovel") || name.contains("paxel")
                || name.contains("crowbar") || name.contains("machete") || name.contains("katana")
                || name.contains("baton") || name.contains("chainsaw") || name.contains("cleaver")
                || name.contains("turret") || name.contains("printer") || name.contains("recycler")
                || name.contains("macerator") || name.contains("polarizer") || name.contains("compressor")
                || name.contains("cryoniter") || name.contains("thermolith") || name.contains("statue")
                || name.contains("minecart") || name.contains("binoculars") || name.contains("designator")
                || name.contains("artillery_indicator") || name.contains("dog_tag")
                || name.contains("medical_kit") || name.contains("syringe") || name.contains("salve")
                || name.contains("air_tank") || name.contains("battery") || name.contains("charging_station")
                || name.contains("container") || name.contains("holosight") || name.contains("red_dot")
                || name.contains("compensator") || name.contains("flash_hider")
                || name.contains("schematic") || name.contains("mold") || name.contains("exo_suit")
                || name.contains("processor") || name.contains("internals")
                || hasWordToken(name, "axe") || hasWordToken(name, "hoe")
                || hasWordToken(name, "sword") || hasWordToken(name, "knife")
                || hasWordToken(name, "dagger") || hasWordToken(name, "club")
                || hasWordToken(name, "perk") || hasWordToken(name, "target")
                || hasWordToken(name, "pill")
                || (!name.contains("launcher") && hasWordToken(name, "mortar"))
                || (!name.contains("gun") && !name.contains("launcher") && hasWordToken(name, "flare"))
                || (!name.contains("launcher") && !name.contains("gun") && !name.contains("rifle") && !name.contains("lance")
                        && (name.contains("grenade") || name.contains("molotov") || name.contains("claymore")
                        || name.contains("landmine") || name.contains("choke_bomb") || name.contains("swarm_bomb")
                        || name.contains("smoke_bomb") || name.contains("flashbang") || name.contains("flash_bang")
                        || hasWordToken(name, "c4") || name.equals("c4_bomb")
                        || name.equals("tm_62") || name.equals("m67") || name.equals("ptkm_1r")))
                || hasWordToken(name, "silencer") || hasWordToken(name, "suppressor")
                || hasWordToken(name, "cartridge") || hasWordToken(name, "canister")
                || hasWordToken(name, "shell") || hasWordToken(name, "shells")
                || hasWordToken(name, "round") || hasWordToken(name, "rounds")
                || hasWordToken(name, "vest") || hasWordToken(name, "hammer")
                || hasWordToken(name, "part") || hasWordToken(name, "parts")
                || hasWordToken(name, "scrap")) {
            return false;
        }
        if ((full.startsWith("tacz:") && name.contains("gun"))
                || full.startsWith("pointblank:")
                || full.startsWith("cgm:") || full.startsWith("scguns:")
                || full.startsWith("superbwarfare:") || full.startsWith("sbw:")
                || full.startsWith("jeg:")
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

    private static boolean hasWordToken(String snakeName, String token) {
        if (snakeName.equals(token)) {
            return true;
        }
        if (snakeName.startsWith(token + "_") || snakeName.endsWith("_" + token)) {
            return true;
        }
        return snakeName.contains("_" + token + "_");
    }

    /**
     * Whether a firearm item or its NBT/DataComponent summary indicates a suppressed, air-powered,
     * pneumatic, or subsonic weapon (such as TaCZ's {@code tacz:muzzle_silence_*} attachments,
     * integrally suppressed guns like VSS/AS Val/MP5SD, Superb Warfare {@code bocek}/{@code taser}
     * and barrel suppressors, or Scorched Guns 2 air guns and integrally silenced firearms).
     */
    public static boolean isSilencedFirearm(String itemId, String tagSummary) {
        String full = itemId != null ? itemId.trim().toLowerCase(Locale.ROOT) : "";
        if (!full.isEmpty()) {
            String name = normalizeEvent(full);
            if (full.contains("silence") || full.contains("suppress") || full.contains("subsonic")
                    || full.contains("airgun") || full.contains("air_gun")
                    || full.contains("air_pistol") || full.contains("air_rifle")
                    || full.contains("pneumatic") || full.contains("blowgun")
                    || full.contains("dart_gun")
                    || hasIntegrallySuppressedToken(full)
                    || (full.startsWith("scguns:") && isKnownQuietScorchedGun(name))
                    || ((full.startsWith("superbwarfare:") || full.startsWith("sbw:"))
                            && (name.equals("bocek") || name.equals("taser")))) {
                return true;
            }
        }
        if (tagSummary != null && !tagSummary.isEmpty()) {
            String lowTag = tagSummary.toLowerCase(Locale.ROOT)
                    .replace("issilenced:0b", "")
                    .replace("issilenced:0", "")
                    .replace("issilenced:false", "")
                    .replace("\"issilenced\":false", "")
                    .replace("\"issilenced\":0", "")
                    .replace("silenced:0b", "")
                    .replace("silenced:0", "")
                    .replace("silenced:false", "")
                    .replace("\"silenced\":false", "")
                    .replace("\"silenced\":0", "");
            if (lowTag.contains("silence")
                    || lowTag.contains("silencer")
                    || lowTag.contains("suppress")
                    || lowTag.contains("suppressor")
                    || lowTag.contains("subsonic")
                    || lowTag.contains("airgun")
                    || lowTag.contains("air_gun")
                    || lowTag.contains("pneumatic")
                    || lowTag.contains("mobsenses:silenced")
                    || hasIntegrallySuppressedToken(lowTag)
                    || lowTag.contains("pbs_1")
                    || lowTag.contains("pbs1")
                    || lowTag.contains("pbs_4")
                    || lowTag.contains("pbs4")
                    || lowTag.contains("dtk4")
                    || lowTag.contains("tgpa")) {
                return true;
            }
            if ((full.startsWith("superbwarfare:") || full.startsWith("sbw:") || lowTag.contains("superbwarfare"))
                    && hasLegacySbwBarrelSuppressor(lowTag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIntegrallySuppressedToken(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.contains("vintorez")
                || s.contains("as_val")
                || s.contains("asval")
                || s.contains("mp5sd")
                || s.contains("mp5_sd")
                || s.contains("honey_badger")
                || s.contains("honeybadger")
                || s.contains("welrod")
                || s.contains("de_lisle")
                || s.contains("delisle")
                || s.contains("vssk")
                || s.contains("vykhlop")
                || s.contains("maxim_9")
                || s.contains("maxim9")) {
            return true;
        }
        if (containsTokenBounded(s, "vss")) {
            return true;
        }
        return s.endsWith("_sd")
                || s.contains("_sd\"")
                || s.contains("_sd,")
                || s.contains("_sd}")
                || s.contains("_sd_")
                || s.contains("_sd ");
    }

    private static boolean containsTokenBounded(String s, String token) {
        int from = 0;
        while (true) {
            int idx = s.indexOf(token, from);
            if (idx < 0) {
                return false;
            }
            boolean leftOk = idx == 0 || !isAlphaNum(s.charAt(idx - 1));
            int after = idx + token.length();
            boolean rightOk = after >= s.length() || !isAlphaNum(s.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
            from = idx + 1;
        }
    }

    private static boolean isAlphaNum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean hasLegacySbwBarrelSuppressor(String lowTag) {
        int from = 0;
        while (true) {
            int idx = lowTag.indexOf("barrel", from);
            if (idx < 0) {
                return false;
            }
            boolean leftOk = idx == 0 || !isAlphaNum(lowTag.charAt(idx - 1));
            int i = idx + "barrel".length();
            if (leftOk) {
                while (i < lowTag.length()) {
                    char c = lowTag.charAt(i);
                    if (c == '"' || c == ':' || c == '=' || c == ' ') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i < lowTag.length() && lowTag.charAt(i) == '2') {
                    int next = i + 1;
                    if (next >= lowTag.length()
                            || (!Character.isDigit(lowTag.charAt(next)) && lowTag.charAt(next) != '.')) {
                        return true;
                    }
                }
            }
            from = idx + 1;
        }
    }

    private static boolean isKnownQuietScorchedGun(String name) {
        switch (name) {
            case "scratches":
            case "gale":
            case "umax_pistol":
            case "venturi":
            case "niami":
            case "crusader":
            case "valora":
            case "mokova":
            case "whispers":
            case "sculk_resonator":
            case "echoes_2":
            case "forlorn_hope":
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether {@code entityTypeId} is definitely a vanilla or non-gun throwable/arrow/utility projectile
     * so holding a firearm in the offhand never upgrades an ordinary throw or bow shot to a gunshot.
     */
    public static boolean isNonFirearmProjectile(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return false;
        }
        String name = normalizeEvent(entityTypeId);
        switch (name) {
            case "arrow":
            case "spectral_arrow":
            case "snowball":
            case "egg":
            case "ender_pearl":
            case "potion":
            case "splash_potion":
            case "lingering_potion":
            case "experience_bottle":
            case "trident":
            case "fishing_bobber":
            case "firework_rocket":
            case "wind_charge":
            case "breeze_wind_charge":
            case "llama_spit":
            case "shulker_bullet":
            case "eye_of_ender":
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether {@code entityTypeId} (e.g. {@code "tacz:bullet"}, {@code "cgm:projectile"},
     * {@code "scguns:bullet"}, {@code "superbwarfare:projectile"}, {@code "jeg:projectile"}) is a firearm
     * projectile or explosive ordnance from a gun mod.
     */
    public static boolean isBulletEntity(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return false;
        }
        String full = entityTypeId.trim().toLowerCase(Locale.ROOT);
        String name = normalizeEvent(full);
        if (full.startsWith("tacz:") || full.startsWith("pointblank:")
                || full.startsWith("cgm:") || full.startsWith("scguns:")
                || full.startsWith("superbwarfare:") || full.startsWith("sbw:")
                || full.startsWith("jeg:") || full.startsWith("musketmod:")
                || full.startsWith("ava:") || full.startsWith("flansmod:")
                || full.startsWith("techguns:")) {
            return name.contains("bullet") || name.contains("projectile")
                    || name.contains("pellet") || name.contains("shell")
                    || name.contains("rocket") || name.contains("missile")
                    || name.contains("grenade") || name.contains("ammo")
                    || name.contains("slug") || name.contains("mortar")
                    || name.contains("rpg") || name.contains("flechette")
                    || name.contains("dart") || name.contains("electrode")
                    || name.contains("laser") || name.contains("beam")
                    || name.contains("plasma") || name.contains("bolt");
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
