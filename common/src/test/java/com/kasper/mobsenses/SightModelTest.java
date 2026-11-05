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
}
