package com.kasper.mobsenses;

/**
 * Client-side state for the Mob Senses Stealth & Noise HUD.
 * <p>
 * Combines the player's recent noise peaks (footsteps, actions, gunshots, and Simple Voice Chat speech),
 * ambient and held-item light exposure from {@link SightModel}, and wounded blood-scent intensity
 * from {@link ScentModel} into responsive {@code 0..100%} bars, transient peak-hold markers, audible
 * range readouts, smooth auto-hide fading, and a unified stealth status indicator.
 */
public final class StealthHudState {

    /** Audible range (in blocks) that fills the HUD noise bar to 100%. */
    public static final double FULL_SCALE_NOISE_RANGE = 28.0;

    /** Decay rate of the HUD noise bar in percentage points per second. */
    private static final double NOISE_DECAY_PER_SEC = 55.0;

    /** How long transient peak-hold ticks linger at the high-water mark before decaying. */
    private static final long PEAK_HOLD_MS = 650L;

    /** Decay rate of the transient peak-hold marker once its hold window expires. */
    private static final double PEAK_HOLD_DECAY_PER_SEC = 65.0;

    /** Smooth auto-hide envelope timings (milliseconds). */
    private static final long AUTO_HIDE_FADEIN_MS = 140L;
    private static final long AUTO_HIDE_HOLD_MS = 1150L;
    private static final long AUTO_HIDE_FADEOUT_MS = 700L;
    private static final long PREVIEW_DURATION_MS = 5000L;

    private volatile boolean enabled = true;
    private double peakNoisePercent;
    private double peakHoldPercent;
    private long peakHoldUntilMs;
    private double peakRangeBlocks;
    private double displayLight;
    private double displayScent;
    private boolean initialized;
    private long lastUpdateMs;
    private long activeSinceMs;
    private long lastActiveMs;
    private long previewUntilMs;
    private int noisePercent;
    private int lightPercent;
    private int scentPercent;
    private int audibleRangeBlocks;
    private boolean voiceActive;
    private boolean whispering;

    /** Whether a connected multiplayer server has locked/disabled the client Stealth HUD (`allow_client_hud=false`). */
    public boolean lockedByServer() {
        return !MobSenses.serverLink().allowClientHud();
    }

    /** Whether the player has enabled the HUD locally (ignoring any remote server fair-play lock). */
    public boolean userEnabled() {
        return enabled;
    }

    /** Whether the client HUD overlay is visible (both enabled by player and allowed by server). */
    public boolean enabled() {
        return enabled && !lockedByServer();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Temporarily reveals the HUD at full opacity (even when {@code auto_hide=true}) so the player can
     * inspect layout or style changes right after running {@code /mshud}.
     */
    public synchronized void triggerPreview(long nowMs) {
        if (nowMs <= 0L) {
            return;
        }
        previewUntilMs = nowMs + PREVIEW_DURATION_MS;
        activeSinceMs = Math.max(1L, nowMs - AUTO_HIDE_FADEIN_MS);
        lastActiveMs = previewUntilMs;
    }

    /**
     * Records a noise impulse made by the local player with an audible range of {@code rangeBlocks}.
     * Uses instant attack so transient spikes (footsteps, blocks, gunshots, GameTest captures) appear
     * on the very first frame and decay smoothly afterward.
     */
    public synchronized void onLocalNoise(double rangeBlocks, long nowMs) {
        if (rangeBlocks <= 0.0) {
            return;
        }
        decayNoise(nowMs);
        if (rangeBlocks > peakRangeBlocks) {
            peakRangeBlocks = rangeBlocks;
        }
        double pct = Math.min(100.0, (rangeBlocks / FULL_SCALE_NOISE_RANGE) * 100.0);
        if (pct > peakNoisePercent) {
            peakNoisePercent = pct;
        }
        if (pct >= peakHoldPercent) {
            peakHoldPercent = pct;
            peakHoldUntilMs = nowMs + PEAK_HOLD_MS;
        }
        noisePercent = (int) Math.round(peakNoisePercent);
        audibleRangeBlocks = (int) Math.round(peakRangeBlocks);
        if (noisePercent >= 12) {
            markActive(nowMs, noisePercent >= 50);
        }
    }

    /**
     * Updates the HUD meters for the current client frame/tick.
     */
    public synchronized void update(int blockLight, int skyLight, int heldLight,
                                    boolean sneaking, boolean sprinting, boolean moving,
                                    double stepSurfaceFactor,
                                    double health, double maxHealth,
                                    double voiceLevel01, long nowMs) {
        double voiceRange = voiceLevel01 > 0.0 ? voiceLevel01 * FULL_SCALE_NOISE_RANGE : 0.0;
        update(blockLight, skyLight, heldLight, sneaking, sprinting, moving,
                stepSurfaceFactor, health, maxHealth, voiceLevel01, nowMs,
                false, false, voiceRange);
    }

    /**
     * Extended frame update supporting potion invisibility, Simple Voice Chat whisper state, and exact
     * audible voice distance in blocks.
     */
    public synchronized void update(int blockLight, int skyLight, int heldLight,
                                    boolean sneaking, boolean sprinting, boolean moving,
                                    double stepSurfaceFactor,
                                    double health, double maxHealth,
                                    double voiceLevel01, long nowMs,
                                    boolean invisible, boolean whispering, double voiceRangeBlocks) {
        double dtSec = (lastUpdateMs != 0L && nowMs > lastUpdateMs) ? (nowMs - lastUpdateMs) / 1000.0 : 0.0;
        decayNoise(nowMs);

        ServerLink link = MobSenses.serverLink();
        ServerSettings srv = link.effectiveSettings();
        Preset activePreset = link.activePreset();

        double stepRange = 0.0;
        double movementNoise = 0.0;
        if (moving && activePreset != Preset.OFF) {
            double posFactor = sneaking ? srv.sneakStepMultiplier()
                    : (sprinting ? srv.sprintStepMultiplier() : NoiseModel.Posture.WALKING.factor());
            double surfaceMult = srv.surfaceSoundScaling() ? Math.max(0.15, stepSurfaceFactor) : 1.0;
            double baseStep = srv.stepRange() * posFactor * surfaceMult;
            stepRange = NoiseModel.effectiveRange(baseStep, activePreset, srv.hearing());
            movementNoise = Math.min(100.0, (stepRange / FULL_SCALE_NOISE_RANGE) * 100.0);
        }

        double clampedVoice01 = Math.max(0.0, Math.min(1.0, voiceLevel01));
        double effectiveVoiceRange = Math.max(0.0, voiceRangeBlocks);
        double voiceNoise = effectiveVoiceRange > 0.0
                ? Math.min(100.0, (effectiveVoiceRange / FULL_SCALE_NOISE_RANGE) * 100.0)
                : clampedVoice01 * 100.0;

        this.voiceActive = clampedVoice01 > 0.05 || effectiveVoiceRange >= NoiseModel.MIN_AUDIBLE_RANGE;
        this.whispering = this.voiceActive && whispering;

        double frameNoise = Math.max(movementNoise, voiceNoise);
        if (frameNoise > peakNoisePercent) {
            peakNoisePercent = frameNoise;
        }
        if (peakNoisePercent >= peakHoldPercent && peakNoisePercent > 0.0) {
            peakHoldPercent = peakNoisePercent;
            peakHoldUntilMs = nowMs + PEAK_HOLD_MS;
        }
        double frameRange = Math.max(stepRange, effectiveVoiceRange);
        if (frameRange > peakRangeBlocks) {
            peakRangeBlocks = frameRange;
        }

        noisePercent = (int) Math.round(Math.max(0.0, Math.min(100.0, peakNoisePercent)));
        audibleRangeBlocks = noisePercent <= 0 ? 0 : (int) Math.round(Math.max(0.0, peakRangeBlocks));

        int targetLight = SightModel.lightExposurePercent(blockLight, skyLight, heldLight, sneaking, invisible);
        int targetScent = (int) Math.round(ScentModel.scentStrength(health, maxHealth) * 100.0);

        if (!initialized || dtSec <= 0.0 || dtSec > 0.25) {
            displayLight = targetLight;
            displayScent = targetScent;
            initialized = true;
        } else {
            double smooth = 1.0 - Math.exp(-dtSec * 12.0);
            displayLight = Math.abs(targetLight - displayLight) >= 18.0
                    ? targetLight
                    : displayLight + (targetLight - displayLight) * smooth;
            displayScent = Math.abs(targetScent - displayScent) >= 18.0
                    ? targetScent
                    : displayScent + (targetScent - displayScent) * smooth;
        }
        lightPercent = (int) Math.round(Math.max(0.0, Math.min(100.0, displayLight)));
        scentPercent = (int) Math.round(Math.max(0.0, Math.min(100.0, displayScent)));

        boolean activeNow = noisePercent >= 12 || scentPercent > 0 || this.voiceActive || lightPercent >= 55;
        if (activeNow) {
            markActive(nowMs, noisePercent >= 50 || scentPercent >= 50);
        }
    }

    private void markActive(long nowMs, boolean instant) {
        if (nowMs <= 0L) {
            return;
        }
        if (instant) {
            activeSinceMs = Math.max(1L, nowMs - AUTO_HIDE_FADEIN_MS);
        } else if (lastActiveMs == 0L || (nowMs - lastActiveMs) > (AUTO_HIDE_HOLD_MS + AUTO_HIDE_FADEOUT_MS)) {
            activeSinceMs = nowMs;
        } else if ((nowMs - lastActiveMs) > AUTO_HIDE_HOLD_MS) {
            double currentAlpha = visibilityAlpha(true, nowMs);
            activeSinceMs = Math.max(1L, nowMs - Math.round(currentAlpha * AUTO_HIDE_FADEIN_MS));
        }
        if (nowMs > lastActiveMs) {
            lastActiveMs = nowMs;
        }
    }

    private void decayNoise(long nowMs) {
        if (lastUpdateMs != 0L && nowMs > lastUpdateMs) {
            double dtSec = (nowMs - lastUpdateMs) / 1000.0;
            double before = peakNoisePercent;
            peakNoisePercent = Math.max(0.0, peakNoisePercent - NOISE_DECAY_PER_SEC * dtSec);
            if (before > 0.0 && peakNoisePercent > 0.0) {
                peakRangeBlocks *= (peakNoisePercent / before);
            } else if (peakNoisePercent <= 0.0) {
                peakRangeBlocks = 0.0;
            }
            if (nowMs > peakHoldUntilMs) {
                peakHoldPercent = Math.max(peakNoisePercent, peakHoldPercent - PEAK_HOLD_DECAY_PER_SEC * dtSec);
            }
        }
        if (nowMs > lastUpdateMs || lastUpdateMs == 0L) {
            lastUpdateMs = nowMs;
        }
    }

    /**
     * Opacity multiplier in {@code 0.0..1.0} for {@code auto_hide} mode. Always returns {@code 1.0} when
     * {@code autoHide} is {@code false} or during an active {@link #triggerPreview(long)} window.
     */
    public synchronized double visibilityAlpha(boolean autoHide, long nowMs) {
        if (!autoHide) {
            return 1.0;
        }
        if (previewUntilMs > 0L && nowMs < previewUntilMs) {
            return 1.0;
        }
        if (lastActiveMs == 0L || nowMs <= 0L) {
            return 0.0;
        }
        double fadeIn = 1.0;
        if (activeSinceMs > 0L && nowMs >= activeSinceMs && (nowMs - activeSinceMs) < AUTO_HIDE_FADEIN_MS) {
            fadeIn = (nowMs - activeSinceMs) / (double) AUTO_HIDE_FADEIN_MS;
        }
        long idleMs = nowMs - lastActiveMs;
        if (idleMs <= AUTO_HIDE_HOLD_MS) {
            return Math.max(0.0, Math.min(1.0, fadeIn));
        }
        long fadeElapsed = idleMs - AUTO_HIDE_HOLD_MS;
        if (fadeElapsed >= AUTO_HIDE_FADEOUT_MS) {
            return 0.0;
        }
        double t = fadeElapsed / (double) AUTO_HIDE_FADEOUT_MS;
        double smoothOut = 1.0 - (t * t * (3.0 - 2.0 * t));
        return Math.max(0.0, Math.min(fadeIn, smoothOut));
    }

    public synchronized int noisePercent() {
        return noisePercent;
    }

    /** Transient peak-hold marker percentage in {@code 0..100} (`>= noisePercent()`). */
    public synchronized int peakHoldPercent() {
        return (int) Math.round(Math.max(noisePercent, Math.min(100.0, peakHoldPercent)));
    }

    /** Estimated audible radius in blocks of the player's current/recent noise, or {@code 0} when silent. */
    public synchronized int audibleRangeBlocks() {
        return audibleRangeBlocks;
    }

    public synchronized int lightPercent() {
        return lightPercent;
    }

    public synchronized int scentPercent() {
        return scentPercent;
    }

    public synchronized boolean scentActive() {
        return scentPercent > 0;
    }

    public synchronized boolean voiceActive() {
        return voiceActive;
    }

    public synchronized boolean whispering() {
        return whispering;
    }

    /** Overall exposure score in {@code 0..100} combining noise, light, and scent. */
    public synchronized int overallExposure() {
        return Math.max(noisePercent, Math.max(lightPercent, scentPercent));
    }

    /** Translation key for the player's current stealth state. */
    public synchronized String statusKey() {
        int max = overallExposure();
        if (max >= 65) {
            return "gui.mobsenses.hud.exposed";
        }
        if (max >= 30) {
            return "gui.mobsenses.hud.caution";
        }
        return "gui.mobsenses.hud.hidden";
    }

    public synchronized void reset() {
        peakNoisePercent = 0.0;
        peakHoldPercent = 0.0;
        peakHoldUntilMs = 0L;
        peakRangeBlocks = 0.0;
        displayLight = 0.0;
        displayScent = 0.0;
        initialized = false;
        lastUpdateMs = 0L;
        activeSinceMs = 0L;
        lastActiveMs = 0L;
        previewUntilMs = 0L;
        noisePercent = 0;
        lightPercent = 0;
        scentPercent = 0;
        audibleRangeBlocks = 0;
        voiceActive = false;
        whispering = false;
    }
}
