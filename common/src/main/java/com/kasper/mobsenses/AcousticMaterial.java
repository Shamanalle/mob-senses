package com.kasper.mobsenses;

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
    WOOD("wood", 0.70, 1.05, 1.00),
    WOOL("wool", 1.40, 0.20, 0.30),
    SOFT("soft", 1.20, 0.50, 0.50),
    GLASS("glass", 0.40, 1.15, 1.35),
    ICE("ice", 0.70, 1.10, 1.15),
    DOOR("door", 0.60, 1.00, 1.00),
    LEAVES("leaves", 0.15, 1.10, 0.65),
    THIN("thin", 0.20, 1.00, 0.90),
    LIQUID("liquid", 0.35, 0.85, 0.50),
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
        return null;
    }
}
