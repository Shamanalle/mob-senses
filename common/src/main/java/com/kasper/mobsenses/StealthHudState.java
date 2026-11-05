package com.kasper.mobsenses;

/**
 * Client-side state for the Mob Senses Stealth & Noise HUD.
 * <p>
 * Combines the player's recent noise peaks (footsteps, actions, and Simple Voice Chat speech),
 * ambient and held-item light exposure from {@link SightModel}, and wounded blood-scent intensity
 * from {@link ScentModel} into smooth {@code 0..100%} bars and a single stealth status indicator.
 */
public final class StealthHudState {

    /** Audible range (in blocks) that fills the HUD noise bar to 100%. */
    public static final double FULL_SCALE_NOISE_RANGE = 28.0;

    /** Decay rate of the HUD noise peak in percentage points per second. */
    private static final double NOISE_DECAY_PER_SEC = 55.0;

    private volatile boolean enabled = true;
    private double peakNoisePercent;
    private long lastUpdateMs;
    private int noisePercent;
    private int lightPercent;
    private int scentPercent;
    private boolean voiceActive;

    /** Whether the client HUD overlay is visible. */
    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Records a noise impulse made by the local player with an audible range of {@code rangeBlocks}.
     */
    public synchronized void onLocalNoise(double rangeBlocks, long nowMs) {
        if (rangeBlocks <= 0.0) {
            return;
        }
        decayNoise(nowMs);
        double pct = Math.min(100.0, (rangeBlocks / FULL_SCALE_NOISE_RANGE) * 100.0);
        if (pct > peakNoisePercent) {
            peakNoisePercent = pct;
        }
        noisePercent = (int) Math.round(peakNoisePercent);
    }

    /**
     * Updates the HUD meters for the current client frame/tick.
     */
    public synchronized void update(int blockLight, int skyLight, int heldLight,
                                    boolean sneaking, boolean sprinting, boolean moving,
                                    double stepSurfaceFactor,
                                    double health, double maxHealth,
                                    double voiceLevel01, long nowMs) {
        decayNoise(nowMs);
        double movementNoise = 0.0;
        if (moving) {
            NoiseModel.Posture posture = sneaking ? NoiseModel.Posture.SNEAKING
                    : (sprinting ? NoiseModel.Posture.SPRINTING : NoiseModel.Posture.WALKING);
            double stepRange = NoiseModel.Kind.STEP.defaultRange() * posture.factor() * Math.max(0.2, stepSurfaceFactor);
            movementNoise = Math.min(100.0, (stepRange / FULL_SCALE_NOISE_RANGE) * 100.0);
        }
        double voiceNoise = Math.max(0.0, Math.min(1.0, voiceLevel01)) * 100.0;
        voiceActive = voiceLevel01 > 0.05;
        if (movementNoise > peakNoisePercent) {
            peakNoisePercent = movementNoise;
        }
        if (voiceNoise > peakNoisePercent) {
            peakNoisePercent = voiceNoise;
        }
        noisePercent = (int) Math.round(Math.max(0.0, Math.min(100.0, peakNoisePercent)));
        lightPercent = SightModel.lightExposurePercent(blockLight, skyLight, heldLight, sneaking);
        scentPercent = (int) Math.round(ScentModel.scentStrength(health, maxHealth) * 100.0);
    }

    private void decayNoise(long nowMs) {
        if (lastUpdateMs != 0L && nowMs > lastUpdateMs) {
            double dtSec = (nowMs - lastUpdateMs) / 1000.0;
            peakNoisePercent = Math.max(0.0, peakNoisePercent - NOISE_DECAY_PER_SEC * dtSec);
        }
        lastUpdateMs = nowMs;
    }

    public synchronized int noisePercent() {
        return noisePercent;
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
        lastUpdateMs = 0L;
        noisePercent = 0;
        lightPercent = 0;
        scentPercent = 0;
        voiceActive = false;
    }
}
