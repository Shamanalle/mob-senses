package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StealthHudStateTest {

    @Test
    void updatesAndDecaysNoiseLightAndScentMeters() {
        StealthHudState hud = new StealthHudState();

        // Sneaking in pitch darkness at full health -> Hidden
        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.0, 1000L);
        assertEquals("gui.mobsenses.hud.hidden", hud.statusKey());
        assertFalse(hud.scentActive());

        // Loud noise spikes the noise bar and exposes the player, then decays over time
        hud.onLocalNoise(24.0, 1000L);
        assertTrue(hud.noisePercent() >= 80);
        assertEquals("gui.mobsenses.hud.exposed", hud.statusKey());

        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.0, 3500L);
        assertEquals(0, hud.noisePercent());

        // Wounded player shows active scent
        hud.update(0, 0, 0, true, false, false, 1.0, 4.0, 20.0, 0.0, 4000L);
        assertTrue(hud.scentActive());
        assertTrue(hud.scentPercent() >= 70);
    }
}
