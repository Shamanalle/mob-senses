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

    @Test
    void tracksPeakHoldAudibleRangeWhisperStateAndSmoothAutoHideFade() {
        StealthHudState hud = new StealthHudState();

        // Completely quiet in darkness -> auto-hide alpha starts at 0
        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.0, 1000L);
        assertEquals(0.0, hud.visibilityAlpha(true, 1000L), 1e-6);
        assertEquals(1.0, hud.visibilityAlpha(false, 1000L), 1e-6);

        // Loud gunshot impulse -> instant full visibility, audible block readout, and peak-hold linger
        hud.onLocalNoise(21.0, 1000L);
        assertEquals(21, hud.audibleRangeBlocks());
        assertEquals(75, hud.noisePercent());
        assertEquals(75, hud.peakHoldPercent());
        assertEquals(1.0, hud.visibilityAlpha(true, 1000L), 1e-6);

        // 400ms later: noise decays, while peak-hold stays pinned inside its hold window
        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.0, 1400L);
        assertTrue(hud.noisePercent() < 75);
        assertEquals(75, hud.peakHoldPercent());
        assertTrue(hud.audibleRangeBlocks() > 0);

        // Whispering in Simple Voice Chat sets both voiceActive and whispering
        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.18, 1500L, false, true, 4.8);
        assertTrue(hud.voiceActive());
        assertTrue(hud.whispering());

        // Quiet again: holds at 1.0, smoothly fades between hold and hold+fadeout, then reaches 0.0
        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.0, 3500L);
        double midFade = hud.visibilityAlpha(true, 1500L + 1150L + 350L);
        assertTrue(midFade > 0.1 && midFade < 0.9, "Expected mid fade alpha in (0.1, 0.9), got " + midFade);
        assertEquals(0.0, hud.visibilityAlpha(true, 5000L), 1e-6);

        // Preview temporarily reveals the HUD at full opacity even while quiet
        hud.triggerPreview(6000L);
        assertEquals(1.0, hud.visibilityAlpha(true, 6500L), 1e-6);
    }
}
