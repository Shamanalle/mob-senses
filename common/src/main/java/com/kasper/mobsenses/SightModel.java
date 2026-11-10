package com.kasper.mobsenses;

/**
 * Models how light and posture affect how far mobs can see a player:
 * <ul>
 *   <li>Holding a torch, lantern, or other light source in the main hand or off-hand lights the player
 *       up and extends the distance from which mobs notice them.</li>
 *   <li>Standing or sneaking in darkness shrinks the distance from which mobs can spot the player.</li>
 * </ul>
 */
public final class SightModel {

    /** Baseline mob sight range in blocks in full daylight. */
    public static final double DEFAULT_SIGHT_RANGE = 16.0;

    /** Minimum visibility multiplier in pitch darkness while sneaking. */
    public static final double MIN_VISIBILITY_FACTOR = 0.20;

    /** Maximum visibility multiplier when holding a bright light source while sprinting. */
    public static final double MAX_VISIBILITY_FACTOR = 1.85;

    private SightModel() {
    }

    /**
     * Estimates the light level ({@code 0..15}) emitted by a held item from its identifier
     * (e.g. {@code "minecraft:torch"}, {@code "minecraft:lantern"}, {@code "minecraft:soul_torch"}).
     */
    public static int heldItemLight(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        String name = NoiseModel.normalizeEvent(itemId);
        if (name.isEmpty() || "air".equals(name)) {
            return 0;
        }
        if (name.contains("soul_torch") || name.contains("soul_lantern") || name.contains("crying_obsidian")) {
            return 10;
        }
        if (name.contains("redstone_torch") || name.contains("glow_ink") || name.contains("magma_block")
                || name.contains("enchanting_table") || name.contains("ender_chest") || name.contains("glow_lichen")) {
            return 7;
        }
        if (name.contains("lantern") || name.contains("glowstone") || name.contains("shroomlight")
                || name.contains("froglight") || name.contains("jack_o_lantern") || name.contains("beacon")
                || name.contains("lava_bucket") || name.contains("conduit")) {
            return 15;
        }
        if (name.contains("torch") || name.contains("end_rod") || name.contains("glow_berries")
                || name.contains("campfire")) {
            return 14;
        }
        if (name.contains("candle") || name.contains("amethyst") || name.contains("sculk_catalyst")
                || name.contains("spectral_arrow") || name.contains("blaze_rod") || name.contains("blaze_powder")) {
            return 5;
        }
        return 0;
    }

    /**
     * Visibility factor from ambient light ({@code 0..15}) and held light ({@code 0..15}):
     * {@code 0.35} in pitch darkness ({@code 0}), {@code 1.0} in full light ({@code 15}), and up to
     * {@code 1.50} when holding a bright light source like a torch or lantern.
     */
    public static double lightFactor(int blockLight, int skyLight, int heldLight) {
        int b = clampLight(blockLight);
        int s = clampLight(skyLight);
        int h = clampLight(heldLight);
        int effectiveLight = Math.max(Math.max(b, s), h);
        double ambient = 0.35 + 0.65 * (effectiveLight / 15.0);
        ServerSettings cfg = MobSenses.settings();
        double bonusWeight = cfg != null ? cfg.heldLightBonus() : 0.50;
        double heldBonus = h > 0 ? (bonusWeight * (h / 15.0)) : 0.0;
        return ambient + heldBonus;
    }

    /** Posture multiplier for visual detection: sneaking lowers profile, sprinting attracts attention. */
    public static double postureFactor(boolean sneaking, boolean sprinting) {
        ServerSettings cfg = MobSenses.settings();
        if (sneaking) {
            return cfg != null ? cfg.sneakSightMultiplier() : 0.60;
        }
        if (sprinting) {
            return cfg != null ? cfg.sprintSightMultiplier() : 1.15;
        }
        return 1.00;
    }

    /** Combined visibility multiplier in {@code [MIN_VISIBILITY_FACTOR, MAX_VISIBILITY_FACTOR]}. */
    public static double visibilityFactor(int blockLight, int skyLight, int heldLight,
                                          boolean sneaking, boolean sprinting) {
        ServerSettings cfg = MobSenses.settings();
        double minVis = cfg != null ? cfg.darknessMinVisibility() : MIN_VISIBILITY_FACTOR;
        double raw = lightFactor(blockLight, skyLight, heldLight) * postureFactor(sneaking, sprinting);
        return Math.max(minVis, Math.min(MAX_VISIBILITY_FACTOR, raw));
    }

    /**
     * Effective distance (in blocks) at which a mob can see a player given the light at the player's
     * position, the light of any item in the player's hands, the player's posture, and the world's preset.
     */
    public static double effectiveSightRange(double baseRange, int blockLight, int skyLight, int heldLight,
                                             boolean sneaking, boolean sprinting, Preset preset) {
        if (baseRange <= 0.0 || preset == null || preset == Preset.OFF) {
            return Math.max(0.0, baseRange);
        }
        ServerSettings cfg = MobSenses.settings();
        double minVis = cfg != null ? cfg.darknessMinVisibility() : MIN_VISIBILITY_FACTOR;
        double factor = visibilityFactor(blockLight, skyLight, heldLight, sneaking, sprinting);
        double scaled = 1.0 + (factor - 1.0) * preset.hearing();
        return Math.max(2.0, baseRange * Math.max(minVis, scaled));
    }

    /**
     * Light exposure percentage ({@code 0..100}) for the client Stealth HUD.
     */
    public static int lightExposurePercent(int blockLight, int skyLight, int heldLight, boolean sneaking) {
        double vis = visibilityFactor(blockLight, skyLight, heldLight, sneaking, false);
        double normalized = (vis - MIN_VISIBILITY_FACTOR) / (1.50 - MIN_VISIBILITY_FACTOR);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, normalized)) * 100.0);
    }

    private static int clampLight(int light) {
        return Math.max(0, Math.min(15, light));
    }
}
