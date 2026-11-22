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

    @Test
    void directionalMobFovConeAndCloseBackSenseBehaveAccurately() {
        // When FOV is disabled, mobs see 360 degrees even directly behind their back at distance
        assertTrue(SightModel.isWithinFov(0, 65, 0, 0.0f, 0, 65, -8, false, 150.0, 2.0));

        // Mob facing South (yaw=0 -> +Z) with 150 deg cone (half-angle = 75 deg) and 2m back sixth-sense
        assertTrue(SightModel.isWithinFov(0, 65, 0, 0.0f, 0, 65, 10, true, 150.0, 2.0), "Directly ahead (+Z)");
        assertTrue(SightModel.isWithinFov(0, 65, 0, 0.0f, -8, 65, 3, true, 150.0, 2.0), "Inside left front (~69 deg)");
        assertFalse(SightModel.isWithinFov(0, 65, 0, 0.0f, 8, 65, 1, true, 150.0, 2.0), "Outside right (~83 deg)");
        assertFalse(SightModel.isWithinFov(0, 65, 0, 0.0f, 0, 65, -6, true, 150.0, 2.0), "Behind at 6m");

        // All other cardinal head rotations: West (90 -> -X), East (-90 -> +X), North (180/-180 -> -Z)
        assertTrue(SightModel.isWithinFov(0, 65, 0, 90.0f, -6, 65, 0, true, 120.0, 2.0), "Facing West sees -X");
        assertFalse(SightModel.isWithinFov(0, 65, 0, 90.0f, 6, 65, 0, true, 120.0, 2.0), "Facing West blind to +X");
        assertTrue(SightModel.isWithinFov(0, 65, 0, -90.0f, 6, 65, 0, true, 120.0, 2.0), "Facing East sees +X");
        assertTrue(SightModel.isWithinFov(0, 65, 0, 180.0f, 0, 65, -6, true, 120.0, 2.0), "Facing North sees -Z");
        assertTrue(SightModel.isWithinFov(0, 65, 0, -540.0f, 0, 65, -6, true, 120.0, 2.0), "Wrapped yaw normalized");

        // Close rear sixth-sense perimeter vs pure blind spot (0m)
        assertTrue(SightModel.isWithinFov(0, 65, 0, 0.0f, 0, 65, -1.5, true, 150.0, 2.0),
                "Touching within 2m back sense alerts mob");
        assertFalse(SightModel.isWithinFov(0, 65, 0, 0.0f, 0, 65, -1.5, true, 150.0, 0.0),
                "With 0m back sense, directly behind remains blind");

        // 360 deg slider / NaN yaw safety
        assertTrue(SightModel.isWithinFov(0, 65, 0, 0.0f, 0, 65, -10, true, 360.0, 0.0));
        assertTrue(SightModel.isWithinFov(0, 65, 0, Float.NaN, 0, 65, -10, true, 120.0, 0.0));
    }
}
