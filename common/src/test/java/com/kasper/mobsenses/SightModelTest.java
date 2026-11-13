package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SightModelTest {

    @Test
    void heldLightExtendsSightAndDarknessPlusSneakingHidesPlayer() {
        int torchLight = SightModel.heldItemLight("minecraft:torch");
        int lanternLight = SightModel.heldItemLight("minecraft:lantern");
        int swordLight = SightModel.heldItemLight("minecraft:iron_sword");

        assertEquals(14, torchLight);
        assertEquals(15, lanternLight);
        assertEquals(0, swordLight);

        double darkSneak = SightModel.effectiveSightRange(
                SightModel.DEFAULT_SIGHT_RANGE, 0, 0, 0, true, false, Preset.DEFAULT);
        double darkStand = SightModel.effectiveSightRange(
                SightModel.DEFAULT_SIGHT_RANGE, 0, 0, 0, false, false, Preset.DEFAULT);
        double darkWithTorch = SightModel.effectiveSightRange(
                SightModel.DEFAULT_SIGHT_RANGE, 0, 0, torchLight, false, false, Preset.DEFAULT);
        double daylightStand = SightModel.effectiveSightRange(
                SightModel.DEFAULT_SIGHT_RANGE, 0, 15, 0, false, false, Preset.DEFAULT);

        assertTrue(darkSneak < darkStand, "Sneaking in darkness must reduce detection range further");
        assertTrue(darkStand < daylightStand, "Darkness must reduce detection range compared to daylight");
        assertTrue(darkWithTorch > daylightStand, "Holding a torch in darkness lights the player up like a beacon");

        int darkPct = SightModel.lightExposurePercent(0, 0, 0, true);
        int torchPct = SightModel.lightExposurePercent(0, 0, torchLight, false);
        assertTrue(torchPct > darkPct + 50);
    }

    @Test
    void presetSightMultiplierAndInvisibilityAndDarknessToggleBehaveAccurately(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        MobSenses.start(dir, new MobSenses.Platform("Test", "1.21.1"), false);
        int lanternLight = SightModel.heldItemLight("minecraft:lantern");

        double subtleTorch = SightModel.effectiveSightRange(16.0, 0, 15, lanternLight, false, false, Preset.SUBTLE);
        double defaultTorch = SightModel.effectiveSightRange(16.0, 0, 15, lanternLight, false, false, Preset.DEFAULT);
        double hardcoreTorch = SightModel.effectiveSightRange(16.0, 0, 15, lanternLight, false, false, Preset.HARDCORE);
        assertTrue(subtleTorch < defaultTorch, "Subtle preset must soften torch sight amplification");
        assertTrue(hardcoreTorch > defaultTorch, "Hardcore preset must sharpen torch sight amplification");

        double subtleDark = SightModel.effectiveSightRange(16.0, 0, 0, 0, false, false, Preset.SUBTLE);
        double hardcoreDark = SightModel.effectiveSightRange(16.0, 0, 0, 0, false, false, Preset.HARDCORE);
        assertTrue(subtleDark > hardcoreDark, "Preset sight multiplier scales deviation around baseline 1.0");

        int visibleDayPct = SightModel.lightExposurePercent(0, 15, 0, false, false);
        int invisibleDayPct = SightModel.lightExposurePercent(0, 15, 0, false, true);
        int invisibleWithTorchPct = SightModel.lightExposurePercent(0, 0, lanternLight, false, true);
        assertTrue(invisibleDayPct < visibleDayPct - 40, "Invisibility without held light must drop HUD exposure");
        assertTrue(invisibleWithTorchPct > 65, "Holding a lantern while invisible still exposes the player");

        MobSenses.settings().setOption("darkness_stealth_enabled", "false");
        try {
            double pitchDarkNoStealth = SightModel.effectiveSightRange(16.0, 0, 0, 0, true, false, Preset.DEFAULT);
            assertEquals(16.0, pitchDarkNoStealth, 1e-6,
                    "Disabling darkness_stealth_enabled must keep sight at full baseline in darkness");
        } finally {
            MobSenses.settings().setOption("darkness_stealth_enabled", "true");
        }
    }

    @Test
    void highHeldLightAndSprintingScaleAboveLegacyCeiling() {
        assertTrue(SightModel.MAX_VISIBILITY_FACTOR >= 3.75,
                "MAX_VISIBILITY_FACTOR must allow held light + sprint scaling up to 3.75x");
        ServerSettings s = MobSenses.settings();
        String prevHeldBonus = s.getOption("held_light_bonus");
        String prevSprintMult = s.getOption("sprint_sight_multiplier");
        try {
            assertTrue(s.setOption("held_light_bonus", "2.0"));
            assertTrue(s.setOption("sprint_sight_multiplier", "1.5"));

            int lanternLight = SightModel.heldItemLight("minecraft:lantern");
            double walkFactor = SightModel.visibilityFactor(0, 15, lanternLight, false, false, s);
            double sprintFactor = SightModel.visibilityFactor(0, 15, lanternLight, false, true, s);

            assertTrue(walkFactor > 1.85,
                    "Daylight + lantern + held_light_bonus=2.0 must exceed legacy 1.85x cap, was " + walkFactor);
            assertTrue(sprintFactor > walkFactor && sprintFactor <= SightModel.MAX_VISIBILITY_FACTOR,
                    "Sprinting must amplify visibility above walking without exceeding MAX_VISIBILITY_FACTOR, was "
                            + sprintFactor);

            double sprintRange = SightModel.effectiveSightRange(16.0, 0, 15, lanternLight, false, true, Preset.DEFAULT);
            assertTrue(sprintRange > 16.0 * 1.85,
                    "Sprinting with lantern must extend effective sight beyond 1.85x base range, was " + sprintRange);
        } finally {
            if (prevHeldBonus != null) {
                s.setOption("held_light_bonus", prevHeldBonus);
            }
            if (prevSprintMult != null) {
                s.setOption("sprint_sight_multiplier", prevSprintMult);
            }
        }
    }
}
