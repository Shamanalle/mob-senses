package com.kasper.mobsenses;

/**
 * Shared tactical helpers with identical behavior on the Fabric/Forge/NeoForge mod and the
 * Bukkit/Paper/Folia plugin: hazard-safe destination snapping, golden-angle horde fan-out,
 * stealth ambush reactions, and two-stage suspicion thresholds.
 */
public final class TacticalTargets {

    /** Ratio of mob health at or below which an unseen ambush triggers flight instead of a sweep. */
    public static final double AMBUSH_FLEE_HEALTH_RATIO = 0.35;

    /** Outer fringe fraction of audible range beyond which a single non-urgent sound triggers Stage-1 suspicion. */
    public static final double SUSPICION_FRINGE_RATIO = 0.62;

    /** Default inner and outer radius (blocks) for horde rally golden-angle ring fan-out. */
    public static final double DEFAULT_HORDE_RING_MIN = 1.65;
    public static final double DEFAULT_HORDE_RING_MAX = 2.75;

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final double GOLDEN_FRAC = (3.0 - Math.sqrt(5.0)) * 0.5;
    private static final int[] SAFE_DY_ORDER = {0, -1, 1, -2, 2};

    private TacticalTargets() {
    }

    /** 3D coordinate pair usable across Java 8+ platforms via Jabel. */
    @com.github.bsideup.jabel.Desugar
    public record Point3(double x, double y, double z) {
    }

    /** Planned reaction for a mob damaged by an unseen or unspotted attacker. */
    @com.github.bsideup.jabel.Desugar
    public record AmbushPlan(boolean flee, double targetX, double targetY, double targetZ, double speed) {
    }

    /** Platform-agnostic block safety query used to keep investigation goals out of hazards. */
    public interface HazardWorld {
        /** Whether block {@code (x, y, z)} is hazardous to enter or stand on (lava, fire, magma, trapdoor drop, etc.). */
        boolean isHazardous(int x, int y, int z);

        /** Whether block {@code (x, y, z)} provides safe, dry, non-hazardous footing. */
        boolean isStandable(int x, int y, int z);
    }

    /**
     * Whether {@code kind} represents an urgent sound (gunshots, explosions, combat, bells, voice)
     * that skips Stage-1 suspicion and moves mobs immediately.
     */
    public static boolean isUrgentKind(NoiseModel.Kind kind) {
        if (kind == null) {
            return false;
        }
        switch (kind) {
            case GUNSHOT:
            case EXPLOSION:
            case BELL:
            case COMBAT:
            case VOICE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether a sound at {@code distSq} inside {@code audibleRange} falls in the outer fringe
     * where an unaware mob pauses and looks toward the source first before moving on a second cue.
     */
    public static boolean shouldPauseSuspiciously(NoiseModel.Kind kind, double distSq,
                                                  double audibleRange, boolean enabled) {
        return shouldPauseSuspiciously(kind, null, distSq, audibleRange, enabled);
    }

    /**
     * Whether a sound event at {@code distSq} inside {@code audibleRange} falls in the outer fringe
     * where an unaware mob pauses and looks toward the source first. Intentionally thrown decoys
     * ({@code mobsenses:decoy_land}) skip Stage-1 stationary pause so they reliably lure guards away.
     */
    public static boolean shouldPauseSuspiciously(NoiseModel.Kind kind, String eventId, double distSq,
                                                  double audibleRange, boolean enabled) {
        if (!enabled || kind == null || isUrgentKind(kind) || !(audibleRange > 0.0) || !(distSq >= 0.0)) {
            return false;
        }
        if ("decoy_land".equals(NoiseModel.normalizeEvent(eventId))) {
            return false;
        }
        double threshold = audibleRange * SUSPICION_FRINGE_RATIO;
        return distSq > threshold * threshold;
    }

    /**
     * Returns an offset on a golden-angle ring around a target using the default {@code 1.65 - 2.75m} radii.
     */
    public static Point3 fanOutOffset(int index) {
        return fanOutOffset(index, DEFAULT_HORDE_RING_MIN, DEFAULT_HORDE_RING_MAX);
    }

    /**
     * Computes a non-stacking ring offset around a shared investigation or combat point.
     */
    public static Point3 fanOutOffset(int index, double ringMinRadius, double ringMaxRadius) {
        if (index < 0) {
            return new Point3(0.0, 0.0, 0.0);
        }
        double minR = Math.max(0.0, Math.min(ringMinRadius, ringMaxRadius));
        double maxR = Math.max(minR, Math.max(ringMinRadius, ringMaxRadius));
        double angle = index * GOLDEN_ANGLE;
        double radialPhase = (index * GOLDEN_FRAC) % 1.0;
        double radius = minR + (maxR - minR) * radialPhase;
        return new Point3(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
    }

    /**
     * Checks whether {@code (goalX, goalY, goalZ)} lands on or directly above/inside a hazardous block
     * and snaps it to the nearest safe adjacent standable block when {@code safeFilterEnabled} is true.
     *
     * @return the original point if safe (or filtering disabled), a safe adjacent block center, or
     * {@code null} if the destination is hazardous with no safe footing nearby
     */
    public static Point3 resolveSafeGoal(HazardWorld world, boolean safeFilterEnabled,
                                         double goalX, double goalY, double goalZ,
                                         double mobX, double mobY, double mobZ) {
        if (!Double.isFinite(goalX) || !Double.isFinite(goalY) || !Double.isFinite(goalZ)) {
            return null;
        }
        if (!safeFilterEnabled || world == null) {
            return new Point3(goalX, goalY, goalZ);
        }
        int bx = (int) Math.floor(goalX);
        int by = (int) Math.floor(goalY);
        int bz = (int) Math.floor(goalZ);
        boolean hazardousGoal = isCellOrAdjacentHazard(world, bx, by, bz);
        if (!hazardousGoal) {
            return new Point3(goalX, goalY, goalZ);
        }
        double bestScore = Double.POSITIVE_INFINITY;
        Point3 best = null;
        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int cx = bx + dx;
                    int cz = bz + dz;
                    for (int dy : SAFE_DY_ORDER) {
                        int cy = by + dy;
                        if (isCellOrAdjacentHazard(world, cx, cy, cz)) {
                            continue;
                        }
                        if (!world.isStandable(cx, cy, cz)) {
                            continue;
                        }
                        double px = cx + 0.5;
                        double py = cy;
                        double pz = cz + 0.5;
                        double dGoalSq = distSq(px, py, pz, goalX, goalY, goalZ);
                        double dMobSq = distSq(px, py, pz, mobX, mobY, mobZ);
                        double score = dGoalSq + 0.05 * Math.sqrt(Math.max(0.0, dMobSq));
                        if (score < bestScore) {
                            bestScore = score;
                            best = new Point3(px, py, pz);
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static boolean isCellOrAdjacentHazard(HazardWorld world, int x, int y, int z) {
        if (world.isHazardous(x, y, z)
                || world.isHazardous(x, y - 1, z)
                || world.isHazardous(x, y + 1, z)) {
            return true;
        }
        // If y is not a safe standing cell (e.g. eye-height gunshot/voice air at feetY + 1 above a solid-or-hazard
        // floor below), inspect down to the floor surface below without penalizing safe 1-block bridges over lava.
        if (!world.isStandable(x, y, z)) {
            if (world.isHazardous(x, y - 2, z)) {
                return true;
            }
            if (!world.isStandable(x, y - 1, z) && world.isHazardous(x, y - 3, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes a reaction target when a mob is damaged by an unseen or unspotted attacker:
     * low-HP mobs ({@code <= 35%}) flee away from the shot; healthier mobs sweep the incoming sector
     * with angular scatter instead of getting unfair X-ray lock onto the sniper.
     */
    public static AmbushPlan planAmbushReaction(double mobX, double mobY, double mobZ,
                                                double attackerX, double attackerY, double attackerZ,
                                                double health, double maxHealth,
                                                double normalSpeed, double urgentSpeed,
                                                double seedUnit) {
        double unit = Double.isFinite(seedUnit) ? Math.max(0.0, Math.min(1.0, seedUnit)) : 0.5;
        double dx = attackerX - mobX;
        double dz = attackerZ - mobZ;
        double horiz = Math.hypot(dx, dz);
        double baseAngle = horiz >= 1e-4 ? Math.atan2(dz, dx) : (unit * Math.PI * 2.0);
        double speed = Math.max(urgentSpeed, normalSpeed);
        boolean flee = maxHealth > 0.0 && health <= maxHealth * AMBUSH_FLEE_HEALTH_RATIO;
        if (flee) {
            double jitter = (unit - 0.5) * 2.0 * Math.toRadians(22.0);
            double fleeAngle = baseAngle + Math.PI + jitter;
            double dist = 10.0 + 4.0 * unit;
            return new AmbushPlan(true,
                    mobX + Math.cos(fleeAngle) * dist,
                    mobY,
                    mobZ + Math.sin(fleeAngle) * dist,
                    speed);
        }
        double scatter = (unit - 0.5) * 2.0 * Math.toRadians(28.0);
        double searchAngle = baseAngle + scatter;
        double searchDist = Math.min(18.0, Math.max(8.0, horiz * 0.72));
        double frac = horiz >= 1e-4 ? Math.min(1.0, searchDist / horiz) : 0.0;
        double deltaY = Math.max(-4.0, Math.min(4.0, (attackerY - mobY) * frac));
        return new AmbushPlan(false,
                mobX + Math.cos(searchAngle) * searchDist,
                mobY + deltaY,
                mobZ + Math.sin(searchAngle) * searchDist,
                speed);
    }

    private static double distSq(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }
}
