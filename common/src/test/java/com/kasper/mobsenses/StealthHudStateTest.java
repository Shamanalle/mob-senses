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

    @Test
    void waterSuppressesScentOnHudAndMobAlertStateSyncsAndDecays() {
        StealthHudState hud = new StealthHudState();

        // Swimming washes blood scent indicator off HUD immediately
        hud.update(0, 0, 0, true, false, false, 1.0, 4.0, 20.0, 0.0, 1000L, false, false, 0.0, true, false);
        assertFalse(hud.scentActive());

        // Dry vs rain scent
        hud.update(0, 0, 0, true, false, false, 1.0, 4.0, 20.0, 0.0, 1500L, false, false, 0.0, false, true);
        int rainScent = hud.scentPercent();
        hud.update(0, 0, 0, true, false, false, 1.0, 4.0, 20.0, 0.0, 2000L, false, false, 0.0, false, false);
        int dryScent = hud.scentPercent();
        assertTrue(dryScent > rainScent && rainScent > 0);

        // Remote mob alert transitions 0 -> 1 -> 2 -> 3 and expires cleanly
        hud.syncRemoteMobAlert(StealthHudState.ALERT_SUSPICIOUS, 3000L);
        assertEquals(StealthHudState.ALERT_SUSPICIOUS, hud.mobAlertLevel());
        assertEquals("gui.mobsenses.hud.alert.suspicious", hud.alertKey());

        hud.syncRemoteMobAlert(StealthHudState.ALERT_HUNTED, 3200L);
        assertEquals(StealthHudState.ALERT_HUNTED, hud.mobAlertLevel());
        assertEquals("gui.mobsenses.hud.alert.hunted", hud.alertKey());

        hud.update(0, 0, 0, true, false, false, 1.0, 20.0, 20.0, 0.0, 10_000L, false, false, 0.0, false, false);
        assertEquals(StealthHudState.ALERT_CALM, hud.mobAlertLevel());
        assertEquals("gui.mobsenses.hud.alert.calm", hud.alertKey());
    }

    @Test
    void remoteServerLinkDrivesHudLightAndScent() {
        StealthHudState hud = new StealthHudState();
        ServerLink link = MobSenses.serverLink();
        try {
            java.util.Properties synced = new java.util.Properties();
            synced.setProperty("v", "1");
            synced.setProperty("preset", "default");
            synced.setProperty("darkness_stealth_enabled", "true");
            synced.setProperty("scent_enabled", "true");
            synced.setProperty("scent_health_threshold", "50");
            assertTrue(link.onState(synced));

            hud.update(0, 0, 0, false, false, false, 1.0, 6.0, 20.0, 0.0, 1_000L, false, false, 0.0, false, false);
            int darkLight = hud.lightPercent();
            int woundedScent = hud.scentPercent();
            assertTrue(darkLight <= 30, "Darkness stealth active should keep dark exposure low, was " + darkLight);
            assertTrue(woundedScent > 0, "Wounded player below remote threshold should emit scent, was " + woundedScent);

            java.util.Properties disabled = new java.util.Properties();
            disabled.setProperty("v", "1");
            disabled.setProperty("preset", "default");
            disabled.setProperty("darkness_stealth_enabled", "false");
            disabled.setProperty("scent_enabled", "false");
            assertTrue(link.onState(disabled));

            hud.update(0, 0, 0, false, false, false, 1.0, 6.0, 20.0, 0.0, 6_000L, false, false, 0.0, false, false);
            assertTrue(hud.lightPercent() >= 55 && hud.lightPercent() > darkLight,
                    "Remote darkness_stealth_enabled=false must raise dark light exposure to baseline (~62%), was "
                            + hud.lightPercent());
            assertEquals(0, hud.scentPercent(), "Remote scent_enabled=false must zero HUD scent");

            java.util.Properties presetOff = new java.util.Properties();
            presetOff.setProperty("v", "1");
            presetOff.setProperty("preset", "off");
            presetOff.setProperty("scent_enabled", "true");
            presetOff.setProperty("scent_health_threshold", "50");
            assertTrue(link.onState(presetOff));

            hud.update(0, 0, 0, false, false, false, 1.0, 6.0, 20.0, 0.0, 11_000L, false, false, 0.0, false, false);
            assertEquals(0, hud.scentPercent(), "Remote preset=off must zero HUD scent even if scent_enabled=true");
        } finally {
            link.resetClientState();
        }
    }

    @Test
    void envGearMufflingSelectiveBarsAndBackwardsClockJumpWorkCorrectly() {
        ServerLink link = MobSenses.serverLink();
        link.resetClientState();
        String prevSurfaceScaling = MobSenses.settings().getOption("surface_sound_scaling");
        try {
            StealthHudState hud = new StealthHudState();

            // 100% env/gear muffling (envGearMult = 0.0) silences walking steps
            hud.update(0, 15, 0, false, false, true, 1.0, 20.0, 20.0, 0.0, 1_000L, false, false, 0.0, false, false, 0.0);
            assertEquals(0, hud.noisePercent(), "Zero envGearMult must silence continuous step noise");

            // With surface_sound_scaling=false, surfaceFactor variation is ignored while envGearMult still applies
            assertTrue(MobSenses.settings().setOption("surface_sound_scaling", "false"));
            StealthHudState carpetHud = new StealthHudState();
            StealthHudState glassHud = new StealthHudState();
            carpetHud.update(0, 15, 0, false, false, true, 0.25, 20.0, 20.0, 0.0, 1_000L, false, false, 0.0, false, false, 0.5);
            glassHud.update(0, 15, 0, false, false, true, 1.50, 20.0, 20.0, 0.0, 1_000L, false, false, 0.0, false, false, 0.5);
            assertEquals(carpetHud.noisePercent(), glassHud.noisePercent(),
                    "surface_sound_scaling=false must ignore block surfaceFactor");
            assertTrue(carpetHud.noisePercent() > 0,
                    "surface_sound_scaling=false with positive envGearMult must keep attenuated step noise");

            // Selective bar exposure/status: exclude Light bar while holding a bright light in daylight
            hud.update(0, 15, 14, false, false, false, 1.0, 20.0, 20.0, 0.0, 2_000L, false, false, 0.0, false, false, 1.0);
            assertTrue(hud.lightPercent() >= 65);
            assertTrue(hud.overallExposure(true, true, true) >= 65);
            assertEquals("gui.mobsenses.hud.exposed", hud.statusKey(true, true, true));
            assertEquals(0, hud.overallExposure(true, false, true));
            assertEquals("gui.mobsenses.hud.hidden", hud.statusKey(true, false, true));

            // Backwards clock jump re-anchors timestamps instead of stalling noise decay
            StealthHudState clockHud = new StealthHudState();
            clockHud.update(0, 0, 0, false, false, false, 1.0, 20.0, 20.0, 0.0, 10_000L, false, false, 0.0, false, false, 1.0);
            clockHud.onLocalNoise(24.0, 10_000L);
            clockHud.update(0, 0, 0, false, false, false, 1.0, 20.0, 20.0, 0.0, 10_000L, false, false, 0.0, false, false, 1.0);
            int peakBeforeJump = clockHud.noisePercent();
            assertTrue(peakBeforeJump > 50);

            clockHud.update(0, 0, 0, false, false, false, 1.0, 20.0, 20.0, 0.0, 5_000L, false, false, 0.0, false, false, 1.0);
            clockHud.update(0, 0, 0, false, false, false, 1.0, 20.0, 20.0, 0.0, 6_500L, false, false, 0.0, false, false, 1.0);
            assertTrue(clockHud.noisePercent() < peakBeforeJump,
                    "Noise bar must resume decaying smoothly after a backwards system clock jump");
        } finally {
            if (prevSurfaceScaling != null) {
                MobSenses.settings().setOption("surface_sound_scaling", prevSurfaceScaling);
            }
            link.resetClientState();
        }
    }
}
