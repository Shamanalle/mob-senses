package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoiceTrackerTest {

    private static short[] sineWave(double amplitude01, int samples) {
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            double s = Math.sin(2.0 * Math.PI * i / 48.0) * amplitude01;
            out[i] = (short) Math.round(s * 32767.0);
        }
        return out;
    }

    @Test
    void adaptiveMedianNormalizesQuietAndLoudMicrophones() {
        VoiceTracker tracker = new VoiceTracker();
        UUID quietMicPlayer = UUID.randomUUID();
        UUID loudMicPlayer = UUID.randomUUID();

        short[] quietNormal = sineWave(0.03, 960); // ~ -33.5 dBFS
        short[] loudNormal = sineWave(0.25, 960);  // ~ -15.1 dBFS

        double quietRange = 0.0;
        double loudRange = 0.0;
        for (int i = 0; i < 12; i++) {
            quietRange = tracker.onVoiceFrame(quietMicPlayer, quietNormal, false, 1000L + i * 20L);
            loudRange = tracker.onVoiceFrame(loudMicPlayer, loudNormal, false, 1000L + i * 20L);
        }

        assertTrue(tracker.medianDb(quietMicPlayer) < tracker.medianDb(loudMicPlayer) - 10.0,
                "Quiet and loud microphones must calibrate to their own medians");
        assertEquals(VoiceTracker.BASE_VOICE_RANGE, quietRange, 0.25,
                "Normal speech on a quiet mic should normalize to BASE_VOICE_RANGE");
        assertEquals(VoiceTracker.BASE_VOICE_RANGE, loudRange, 0.25,
                "Normal speech on a loud mic should normalize to BASE_VOICE_RANGE");

        // Shouting (+10 dB over that player's median) expands range, whispering shrinks it
        short[] quietShout = sineWave(0.10, 960);
        double shoutRange = tracker.onVoiceFrame(quietMicPlayer, quietShout, false, 2000L);
        double whisperRange = tracker.onVoiceFrame(quietMicPlayer, quietNormal, true, 2020L);

        assertTrue(shoutRange > quietRange * 1.6, "Shouting above median must expand audible range");
        assertTrue(whisperRange < quietRange * 0.4, "Whispering must shrink audible range");
    }

    @Test
    void silenceIsIgnoredAndPendingFramesRespectCooldown() {
        VoiceTracker tracker = new VoiceTracker();
        UUID player = UUID.randomUUID();

        short[] silence = new short[960];
        assertEquals(0.0, tracker.onVoiceFrame(player, silence, false, 1000L));
        assertEquals(0.0, tracker.pollPendingRange(player, 1000L));

        short[] speech = sineWave(0.10, 960);
        double r = tracker.onVoiceFrame(player, speech, false, 1000L);
        assertTrue(r > 0.0);

        double firstPoll = tracker.pollPendingRange(player, 1000L);
        assertEquals(r, firstPoll, 1e-6);

        // Another frame within COOLDOWN_MS stays buffered until cooldown expires
        tracker.onVoiceFrame(player, speech, false, 1100L);
        assertEquals(0.0, tracker.pollPendingRange(player, 1100L));
        assertTrue(tracker.pollPendingRange(player, 1000L + VoiceTracker.COOLDOWN_MS) > 0.0);
    }
}
