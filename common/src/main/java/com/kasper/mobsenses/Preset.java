package com.kasper.mobsenses;

import java.util.Locale;

/**
 * How keen the mobs' senses are, as one choice for server owners. Each preset scales hearing and sight;
 * the multipliers in the settings file scale them further. {@link #OFF} turns the senses off: mobs
 * behave as in vanilla.
 */
public enum Preset {

    OFF("off", 0.0, 0.0),
    SUBTLE("subtle", 0.6, 0.8),
    DEFAULT("default", 1.0, 1.0),
    HARDCORE("hardcore", 1.6, 1.3);

    private final String id;
    private final double hearing;
    private final double sight;

    Preset(String id, double hearing, double sight) {
        this.id = id;
        this.hearing = hearing;
        this.sight = sight;
    }

    public String id() {
        return id;
    }

    /** How far mobs hear, relative to {@link #DEFAULT}. */
    public double hearing() {
        return hearing;
    }

    /** How far mobs see the player, relative to {@link #DEFAULT}. */
    public double sight() {
        return sight;
    }

    /** The key of the preset's name in the language files, without the {@code command.mobsenses.} prefix. */
    public String nameKey() {
        return "preset." + id;
    }

    /** The preset with this id ("hardcore", any case), or null. */
    public static Preset byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (Preset p : values()) {
            if (p.id.equals(wanted)) {
                return p;
            }
        }
        return null;
    }
}
