package com.kasper.mobsenses;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Models the scent trail of a badly wounded player: when a player drops to or below
 * {@link #WOUNDED_HEALTH_RATIO} ({@code 50%} health), they leave a lingering blood/scent trail that
 * zombies and spiders can follow from nearby.
 */
public final class ScentModel {

    /** Players at or below 50% of their maximum health leave a scent trail. */
    public static final double WOUNDED_HEALTH_RATIO = 0.50;

    /** Base tracking radius (in blocks) for a wounded player at 50% health. */
    public static final double BASE_SCENT_RANGE = 14.0;

    /** Maximum tracking radius (in blocks) for a critically wounded player near 0% health in Default preset. */
    public static final double MAX_BASE_SCENT_RANGE = 24.0;

    /** How long a scent crumb lingers in the world (milliseconds). */
    public static final long CRUMB_LIFETIME_MS = 15_000L;

    /** Minimum squared distance (blocks²) a player moves before dropping another scent crumb. */
    private static final double MIN_STEP_DIST_SQ = 4.0;

    /** Refresh interval (ms) if a wounded player stands still in one spot. */
    private static final long STATIONARY_REFRESH_MS = 3_000L;

    private static final int MAX_CRUMBS_PER_WORLD = 64;

    /** A point along a wounded player's scent trail. */
    @com.github.bsideup.jabel.Desugar
    public record Crumb(UUID playerId, double x, double y, double z, double range, long timestampMs) {
    }

    private final Map<String, List<Crumb>> worldTrails = new ConcurrentHashMap<>();

    /** Whether a player with {@code health} out of {@code maxHealth} is wounded enough to leave a scent trail. */
    public static boolean isWounded(double health, double maxHealth) {
        if (!(health > 0.0) || !(maxHealth > 0.0)) {
            return false;
        }
        ServerSettings s = MobSenses.settings();
        if (s != null && !s.scentEnabled()) {
            return false;
        }
        double thresholdRatio = s != null ? (s.scentHealthThreshold() / 100.0) : WOUNDED_HEALTH_RATIO;
        return (health / maxHealth) <= thresholdRatio;
    }

    /**
     * Normalized scent intensity in {@code [0.0, 1.0]}: {@code 0.0} above the health threshold, rising
     * from {@code 0.40} at the threshold to {@code 1.00} as health approaches {@code 0}.
     */
    public static double scentStrength(double health, double maxHealth) {
        if (!isWounded(health, maxHealth)) {
            return 0.0;
        }
        ServerSettings s = MobSenses.settings();
        double thresholdRatio = Math.max(0.05, s != null ? (s.scentHealthThreshold() / 100.0) : WOUNDED_HEALTH_RATIO);
        double ratio = Math.max(0.0, Math.min(thresholdRatio, health / maxHealth));
        double severity = 1.0 - (ratio / thresholdRatio);
        return 0.40 + 0.60 * severity;
    }

    /**
     * Tracking radius (in blocks) within which zombies and spiders smell a wounded player.
     */
    public static double scentRange(double health, double maxHealth, Preset preset) {
        if (!isWounded(health, maxHealth) || preset == null || preset == Preset.OFF) {
            return 0.0;
        }
        ServerSettings s = MobSenses.settings();
        double baseCfg = s != null ? s.scentRange() : BASE_SCENT_RANGE;
        if (baseCfg <= 0.0) {
            return 0.0;
        }
        double thresholdRatio = Math.max(0.05, s != null ? (s.scentHealthThreshold() / 100.0) : WOUNDED_HEALTH_RATIO);
        double ratio = Math.max(0.0, Math.min(thresholdRatio, health / maxHealth));
        double severity = 1.0 - (ratio / thresholdRatio);
        double maxBase = s != null ? Math.max(baseCfg, s.scentMaxRange()) : baseCfg * (MAX_BASE_SCENT_RANGE / BASE_SCENT_RANGE);
        double base = baseCfg + (maxBase - baseCfg) * severity;
        return base * preset.hearing();
    }

    /**
     * Whether a mob type tracks wounded players by scent (zombies, spiders, and configured {@code scent_mobs}).
     */
    public static boolean isScentTracker(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return false;
        }
        ServerSettings s = MobSenses.settings();
        if (s != null && s.isScentMob(entityTypeId)) {
            return true;
        }
        String id = NoiseModel.normalizeEvent(entityTypeId).toLowerCase(Locale.ROOT);
        return id.contains("zombie")
                || id.equals("husk")
                || id.equals("drowned")
                || id.contains("zombified_piglin")
                || id.equals("pig_zombie")
                || id.equals("spider")
                || id.equals("cave_spider");
    }

    private static long crumbLifetimeMs() {
        ServerSettings s = MobSenses.settings();
        return s != null ? Math.round(s.scentDurationSeconds() * 1000.0) : CRUMB_LIFETIME_MS;
    }

    /**
     * Records a wounded player's current position along their world's scent trail.
     *
     * @return the active scent range in blocks, or {@code 0.0} if the player is not wounded
     */
    public double recordPlayer(String world, UUID playerId, double x, double y, double z,
                               double health, double maxHealth, Preset preset, long nowMs) {
        if (world == null || playerId == null) {
            return 0.0;
        }
        double range = scentRange(health, maxHealth, preset);
        if (range <= 0.0) {
            return 0.0;
        }
        List<Crumb> crumbs = worldTrails.computeIfAbsent(world, k -> new ArrayList<>());
        synchronized (crumbs) {
            pruneExpired(crumbs, nowMs);
            for (int i = crumbs.size() - 1; i >= 0; i--) {
                Crumb c = crumbs.get(i);
                if (playerId.equals(c.playerId())) {
                    double dx = x - c.x();
                    double dy = y - c.y();
                    double dz = z - c.z();
                    if (dx * dx + dy * dy + dz * dz < MIN_STEP_DIST_SQ
                            && nowMs - c.timestampMs() < STATIONARY_REFRESH_MS) {
                        return range;
                    }
                    break;
                }
            }
            if (crumbs.size() >= MAX_CRUMBS_PER_WORLD) {
                crumbs.remove(0);
            }
            crumbs.add(new Crumb(playerId, x, y, z, range, nowMs));
        }
        return range;
    }

    /**
     * Finds the freshest reachable scent crumb within range of {@code (mobX, mobY, mobZ)} in {@code world},
     * or {@code null} if none is in range.
     */
    public Crumb findNearestScent(String world, double mobX, double mobY, double mobZ, long nowMs) {
        if (world == null) {
            return null;
        }
        List<Crumb> crumbs = worldTrails.get(world);
        if (crumbs == null) {
            return null;
        }
        Crumb best = null;
        double bestScore = -1.0;
        synchronized (crumbs) {
            pruneExpired(crumbs, nowMs);
            for (Crumb c : crumbs) {
                double dx = c.x() - mobX;
                double dy = c.y() - mobY;
                double dz = c.z() - mobZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                double r = c.range();
                if (distSq > r * r) {
                    continue;
                }
                double freshness = 1.0 - Math.min(1.0, (double) (nowMs - c.timestampMs()) / (double) crumbLifetimeMs());
                double closeness = 1.0 - Math.sqrt(distSq) / Math.max(1.0, r);
                double score = freshness * 0.6 + closeness * 0.4;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        return best;
    }

    /**
     * Returns a snapshot of all non-expired blood-scent crumbs currently active in {@code world}.
     */
    public List<Crumb> activeCrumbs(String world, long nowMs) {
        if (world == null) {
            return java.util.Collections.emptyList();
        }
        List<Crumb> crumbs = worldTrails.get(world);
        if (crumbs == null) {
            return java.util.Collections.emptyList();
        }
        synchronized (crumbs) {
            pruneExpired(crumbs, nowMs);
            return new ArrayList<>(crumbs);
        }
    }

    private static void pruneExpired(List<Crumb> crumbs, long nowMs) {
        long lifetime = crumbLifetimeMs();
        Iterator<Crumb> it = crumbs.iterator();
        while (it.hasNext()) {
            Crumb c = it.next();
            if (nowMs - c.timestampMs() > lifetime) {
                it.remove();
            }
        }
    }

    public void clear() {
        worldTrails.clear();
    }
}
