package com.kasper.mobsenses;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Acoustic material classes used by the wall occlusion tracer and the footstep / block noise model.
 * <p>
 * {@link #wallWeight()} is the acoustic thickness (in stone-block units, where one block of stone is
 * {@code 1.0}) that a single block of the material adds to a sound ray. {@link #stepFactor()} scales
 * the loudness of footsteps on top of the material, and {@link #blockFactor()} scales the noise of
 * breaking or placing a block of this material.
 */
public enum AcousticMaterial {

    STONE("stone", 1.00, 1.00, 1.15),
    METAL("metal", 1.30, 1.30, 1.30),
    EARTH("earth", 0.90, 0.95, 0.90),
    DIRT("dirt", 0.90, 0.95, 0.90),
    GRAVEL("gravel", 0.85, 1.50, 1.10),
    SAND("sand", 0.95, 1.20, 0.90),
    WOOD("wood", 0.70, 1.05, 1.00),
    WOOL("wool", 1.40, 0.20, 0.30),
    SOFT("soft", 1.20, 0.50, 0.50),
    GLASS("glass", 0.40, 1.15, 1.35),
    ICE("ice", 0.70, 1.10, 1.15),
    DOOR("door", 0.60, 1.00, 1.00),
    LEAVES("leaves", 0.15, 1.10, 0.65),
    FOLIAGE("foliage", 0.15, 1.10, 0.65),
    THIN("thin", 0.20, 1.00, 0.90),
    LIQUID("liquid", 0.35, 0.85, 0.50),
    WATER("water", 0.35, 0.85, 0.50),
    AIR("air", 0.00, 0.00, 0.00),
    OTHER("other", 1.00, 1.00, 1.00);

    /** Share of its weight an open door, trapdoor or fence gate keeps: the sound goes round the panel. */
    public static final double OPEN_PANEL_SHARE = 0.1;

    private final String id;
    private final double wallWeight;
    private final double stepFactor;
    private final double blockFactor;

    AcousticMaterial(String id, double wallWeight, double stepFactor, double blockFactor) {
        this.id = id;
        this.wallWeight = wallWeight;
        this.stepFactor = stepFactor;
        this.blockFactor = blockFactor;
    }

    public String id() {
        return id;
    }

    /** Acoustic thickness (in stone blocks) of one full block of this material. */
    public double wallWeight() {
        return wallWeight;
    }

    /** Multiplier for footsteps on this material: wool muffles, metal rings. */
    public double stepFactor() {
        return stepFactor;
    }

    /** Multiplier for breaking or placing a block of this material. */
    public double blockFactor() {
        return blockFactor;
    }

    /**
     * Share of the weight of a door, trapdoor, fence or bars the ray crosses. They are thin by nature,
     * so a closed one counts fully however the ray crosses it; an open one only a tenth.
     */
    public static double panelShare(boolean open) {
        return open ? OPEN_PANEL_SHARE : 1.0;
    }

    /** The material with this id ({@code stone}, {@code wool}...), or {@code null}. */
    public static AcousticMaterial byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (AcousticMaterial m : values()) {
            if (m.id.equals(wanted)) {
                return m;
            }
        }
        switch (wanted) {
            case "none":
            case "empty":
                return AIR;
            case "carpet":
            case "cloth":
                return WOOL;
            case "plant":
            case "plants":
                return FOLIAGE;
            case "iron":
            case "copper":
            case "steel":
                return METAL;
            case "rock":
            case "brick":
            case "concrete":
                return STONE;
            default:
                return null;
        }
    }

    /**
     * Resolves the {@link AcousticMaterial} of a block by its ID (e.g. {@code "create:industrial_iron_block"}).
     */
    public static AcousticMaterial fromBlockId(String blockId) {
        return fromBlockId(blockId, Collections.<String>emptyList());
    }

    /**
     * Resolves the {@link AcousticMaterial} of a block by its ID and optional block tags (e.g. {@code "forge:glass"}).
     */
    public static AcousticMaterial fromBlockId(String blockId, Collection<String> blockTags) {
        if (blockTags != null && !blockTags.isEmpty()) {
            for (String tag : blockTags) {
                if (tag == null) {
                    continue;
                }
                String t = tag.toLowerCase(Locale.ROOT);
                if (t.contains("wool") || t.contains("carpet") || t.contains("dampens_vibrations")) {
                    return WOOL;
                }
                if (t.contains("glass")) {
                    return GLASS;
                }
                if (t.contains("leaves")) {
                    return LEAVES;
                }
            }
        }
        if (blockId == null || Jv.isBlank(blockId)) {
            return STONE;
        }
        String lower = blockId.trim().toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;
        if ("air".equals(path) || path.endsWith("_air")) {
            return AIR;
        }
        if ("water".equals(path) || path.contains("bubble_column")) {
            return WATER;
        }
        if ("lava".equals(path)) {
            return LIQUID;
        }
        if (path.contains("door") || path.contains("trapdoor") || path.contains("fence_gate")) {
            return DOOR;
        }
        if (path.contains("leaves")) {
            return LEAVES;
        }
        if (path.contains("vine") || path.contains("bush") || path.contains("fern") || path.contains("sapling")) {
            return FOLIAGE;
        }
        if (path.contains("wool") || path.contains("carpet") || path.contains("sponge") || path.contains("sculk")) {
            return WOOL;
        }
        if (path.contains("ice")) {
            return ICE;
        }
        if (path.contains("glass")) {
            return GLASS;
        }
        if (path.contains("gravel")) {
            return GRAVEL;
        }
        if (path.contains("sand")) {
            return SAND;
        }
        if (path.contains("dirt") || path.contains("grass_block") || path.contains("podzol")
                || path.contains("mycelium") || path.contains("clay") || path.contains("mud")
                || path.contains("farmland")) {
            return DIRT;
        }
        if (path.contains("bed") || path.contains("banner") || path.contains("snow")
                || path.contains("moss") || path.contains("hay") || path.contains("honeycomb")) {
            return SOFT;
        }
        if (path.contains("planks") || path.contains("log") || path.contains("wood")
                || path.contains("stem") || path.contains("hyphae") || path.contains("bamboo")
                || path.contains("barrel") || path.contains("chest") || path.contains("bookshelf")) {
            return WOOD;
        }
        if (path.contains("rail") || path.contains("iron") || path.contains("copper")
                || path.contains("netherite") || path.contains("gold_block") || path.contains("chain")
                || path.contains("anvil") || path.contains("cauldron") || path.contains("hopper")) {
            return METAL;
        }
        return STONE;
    }
}
