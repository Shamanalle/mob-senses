package com.kasper.mobsenses;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players' microphone audio from Simple Voice Chat and normalizes each player's voice level
 * against the running median of their own microphone.
 * <p>
 * Different microphones vary wildly in gain: a quiet headset at normal speaking volume might sit
 * around {@code -34 dBFS}, while a hot USB condenser sits around {@code -14 dBFS}. By keeping a
 * sliding window of recent speech frames per player and measuring against that player's median,
 * ordinary conversation alerts mobs at roughly {@link #BASE_VOICE_RANGE} blocks for everyone,
 * whispering shrinks the radius, and shouting expands it.
 */
public final class VoiceTracker {

    /** Audible radius (in blocks) of normal speech at a player's median microphone level. */
    public static final double BASE_VOICE_RANGE = 16.0;

    /** Multiplier applied when the player is holding the Simple Voice Chat whisper key. */
    public static final double WHISPER_FACTOR = 0.30;

    /** Frames quieter than this (in dBFS) are treated as background noise / silence. */
    public static final double MIN_SPEECH_DB = -52.0;

    /** Default reference level (in dBFS) before a player's microphone has enough speech frames. */
    public static final double DEFAULT_MEDIAN_DB = -24.0;

    /** Number of speech frames kept in each player's sliding median window (~1.3 s at 20 ms/frame). */
    public static final int WINDOW_FRAMES = 64;

    /** Minimum speech frames before switching from {@link #DEFAULT_MEDIAN_DB} to the player's median. */
    public static final int MIN_CALIBRATED_FRAMES = 6;

    /** Minimum interval between voice noise events emitted into the world for a single player. */
    public static final long COOLDOWN_MS = 250L;

    /** How long the client HUD keeps showing local voice activity after the last packet. */
    private static final long CLIENT_VOICE_HOLD_MS = 350L;

    private static final class PlayerMicState {
        private final double[] window = new double[WINDOW_FRAMES];
        private int count;
        private int writeIndex;
        private double cachedMedian = DEFAULT_MEDIAN_DB;
        private double pendingRange;
        private long lastDispatchMs = Long.MIN_VALUE / 2;

        synchronized double recordFrame(double db, boolean whispering, ServerSettings s) {
            if (!whispering) {
                window[writeIndex] = db;
                writeIndex = (writeIndex + 1) % WINDOW_FRAMES;
                if (count < WINDOW_FRAMES) {
                    count++;
                }
                if (count >= MIN_CALIBRATED_FRAMES) {
                    double[] copy = Arrays.copyOf(window, count);
                    Arrays.sort(copy);
                    double raw = (count & 1) == 1
                            ? copy[count >>> 1]
                            : 0.5 * (copy[(count >>> 1) - 1] + copy[count >>> 1]);
                    cachedMedian = Math.max(-44.0, Math.min(-8.0, raw));
                }
            }
            double baseVoice = s != null ? s.voiceRange() : BASE_VOICE_RANGE;
            double whisperMult = s != null ? s.voiceWhisperMultiplier() : WHISPER_FACTOR;
            double scale = volumeScale(db, cachedMedian, whispering, whisperMult);
            double range = baseVoice * scale;
            if (range > pendingRange) {
                pendingRange = range;
            }
            return range;
        }

        synchronized double pollPending(long nowMs) {
            if (pendingRange <= 0.0 || nowMs - lastDispatchMs < COOLDOWN_MS) {
                return 0.0;
            }
            double out = pendingRange;
            pendingRange = 0.0;
            lastDispatchMs = nowMs;
            return out;
        }

        synchronized double medianDb() {
            return cachedMedian;
        }
    }

    private final Map<UUID, PlayerMicState> players = new ConcurrentHashMap<>();
    private final PlayerMicState clientSelf = new PlayerMicState();
    private volatile double clientLevel01;
    private volatile double clientRangeBlocks;
    private volatile boolean clientWhispering;
    private volatile long clientLastMs;

    /**
     * Root-mean-square level of a 16-bit signed PCM audio frame in dBFS ({@code [-96.0, 0.0]}).
     */
    public static double rmsDb(short[] samples) {
        if (samples == null || samples.length == 0) {
            return -96.0;
        }
        double sumSq = 0.0;
        for (short s : samples) {
            double norm = s / 32768.0;
            sumSq += norm * norm;
        }
        double rms = Math.sqrt(sumSq / samples.length);
        if (rms <= 1.0e-5) {
            return -96.0;
        }
        return Math.max(-96.0, Math.min(0.0, 20.0 * Math.log10(rms)));
    }

    /**
     * Relative loudness scale compared to the player's calibrated microphone median.
     * <p>
     * Speaking at the player's median yields {@code 1.0}; speaking {@code +10 dB} louder doubles the
     * range (up to {@code 2.25×}); speaking softly shrinks it down to {@code 0.35×}; whispering
     * multiplies the result by {@link #WHISPER_FACTOR}.
     */
    public static double volumeScale(double db, double medianDb, boolean whispering) {
        ServerSettings s = MobSenses.settings();
        double whisperMult = s != null ? s.voiceWhisperMultiplier() : WHISPER_FACTOR;
        return volumeScale(db, medianDb, whispering, whisperMult);
    }

    public static double volumeScale(double db, double medianDb, boolean whispering, double whisperMultiplier) {
        if (db < MIN_SPEECH_DB) {
            return 0.0;
        }
        double delta = db - medianDb;
        double scale = Math.pow(2.0, delta / 10.0);
        double clamped = Math.max(0.35, Math.min(2.25, scale));
        return whispering ? clamped * whisperMultiplier : clamped;
    }

    /**
     * Processes a decoded 16-bit PCM voice frame from a player on the server.
     *
     * @return the computed base audible range in blocks, or {@code 0.0} if the frame is silence
     */
    public double onVoiceFrame(UUID playerId, short[] samples, boolean whispering, long nowMs) {
        if (playerId == null) {
            return 0.0;
        }
        double db = rmsDb(samples);
        return onVoiceDb(playerId, db, whispering, nowMs);
    }

    /**
     * Processes a voice frame whose level in dBFS is already computed.
     */
    public double onVoiceDb(UUID playerId, double db, boolean whispering, long nowMs) {
        if (playerId == null || db < MIN_SPEECH_DB) {
            return 0.0;
        }
        PlayerMicState state = players.computeIfAbsent(playerId, k -> new PlayerMicState());
        return state.recordFrame(db, whispering, MobSenses.settings());
    }

    /**
     * Polls the loudest pending voice range for {@code playerId} if the per-player cooldown has elapsed.
     */
    public double pollPendingRange(UUID playerId, long nowMs) {
        if (playerId == null) {
            return 0.0;
        }
        PlayerMicState state = players.get(playerId);
        return state == null ? 0.0 : state.pollPending(nowMs);
    }

    /** Current calibrated median dBFS for {@code playerId}, or {@link #DEFAULT_MEDIAN_DB} if uncalibrated. */
    public double medianDb(UUID playerId) {
        PlayerMicState state = playerId == null ? null : players.get(playerId);
        return state == null ? DEFAULT_MEDIAN_DB : state.medianDb();
    }

    /** Records local client microphone audio for the client-side Stealth HUD. */
    public void onClientSound(short[] samples, boolean whispering, long nowMs) {
        double db = rmsDb(samples);
        if (db < MIN_SPEECH_DB) {
            return;
        }
        ServerLink link = MobSenses.serverLink();
        ServerSettings srv = link.effectiveSettings();
        Preset preset = link.activePreset();
        double rawRange = clientSelf.recordFrame(db, whispering, srv);
        double effRange = (!srv.voice() || preset == Preset.OFF)
                ? 0.0
                : NoiseModel.effectiveRange(rawRange, preset, srv.hearing());
        if (effRange < NoiseModel.MIN_AUDIBLE_RANGE) {
            clientLevel01 = 0.0;
            clientRangeBlocks = 0.0;
            clientWhispering = false;
            return;
        }
        long elapsed = nowMs - clientLastMs;
        if (elapsed < 0L || elapsed >= CLIENT_VOICE_HOLD_MS) {
            clientLevel01 = 0.0;
            clientRangeBlocks = 0.0;
        }
        double level = Math.min(1.0, effRange / (BASE_VOICE_RANGE * 1.6));
        clientLevel01 = Math.max(clientLevel01, level);
        clientRangeBlocks = Math.max(clientRangeBlocks, effRange);
        clientWhispering = whispering;
        clientLastMs = nowMs;
    }

    /** Normalized local voice activity in {@code [0, 1]} for the client HUD. */
    public double clientVoiceLevel(long nowMs) {
        long elapsed = nowMs - clientLastMs;
        if (elapsed < 0 || elapsed >= CLIENT_VOICE_HOLD_MS) {
            clientLevel01 = 0.0;
            clientRangeBlocks = 0.0;
            clientWhispering = false;
            return 0.0;
        }
        double decay = 1.0 - (double) elapsed / (double) CLIENT_VOICE_HOLD_MS;
        return Math.max(0.0, Math.min(1.0, clientLevel01 * decay));
    }

    /** Estimated audible voice range in blocks on the local client for the HUD readout. */
    public double clientVoiceRangeBlocks(long nowMs) {
        long elapsed = nowMs - clientLastMs;
        if (elapsed < 0 || elapsed >= CLIENT_VOICE_HOLD_MS) {
            return 0.0;
        }
        return clientRangeBlocks;
    }

    /** Whether the active local voice packet is using Simple Voice Chat's whisper key. */
    public boolean clientWhispering(long nowMs) {
        long elapsed = nowMs - clientLastMs;
        return elapsed >= 0 && elapsed < CLIENT_VOICE_HOLD_MS && clientWhispering;
    }

    public void removePlayer(UUID playerId) {
        if (playerId != null) {
            players.remove(playerId);
        }
    }

    public void clear() {
        players.clear();
        clientLevel01 = 0.0;
        clientRangeBlocks = 0.0;
        clientWhispering = false;
        clientLastMs = 0L;
    }
}
