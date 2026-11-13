package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TacticalTargetsTest {

    @Test
    void twoStageSuspicionTriggersOnlyInOuterFringeForNonUrgentSounds() {
        assertTrue(TacticalTargets.shouldPauseSuspiciously(NoiseModel.Kind.STEP, 9.0 * 9.0, 12.0, true));
        assertFalse(TacticalTargets.shouldPauseSuspiciously(NoiseModel.Kind.STEP, 5.0 * 5.0, 12.0, true));
        assertFalse(TacticalTargets.shouldPauseSuspiciously(NoiseModel.Kind.STEP, 9.0 * 9.0, 12.0, false));
        assertFalse(TacticalTargets.shouldPauseSuspiciously(NoiseModel.Kind.GUNSHOT, 35.0 * 35.0, 44.0, true));
        assertFalse(TacticalTargets.shouldPauseSuspiciously(NoiseModel.Kind.EXPLOSION, 30.0 * 30.0, 36.0, true));
        // Q-thrown decoys skip Stage-1 stationary pause so guards walk directly to the decoy
        assertTrue(TacticalTargets.shouldPauseSuspiciously(
                NoiseModel.Kind.PROJECTILE, "minecraft:projectile_land", 14.0 * 14.0, 16.0, true));
        assertFalse(TacticalTargets.shouldPauseSuspiciously(
                NoiseModel.Kind.PROJECTILE, "mobsenses:decoy_land", 14.0 * 14.0, 16.0, true));
    }

    @Test
    void goldenAngleFanOutProducesDistinctPointsInsideRing() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            TacticalTargets.Point3 p = TacticalTargets.fanOutOffset(i);
            double r = Math.hypot(p.x(), p.z());
            assertTrue(r >= TacticalTargets.DEFAULT_HORDE_RING_MIN - 1e-6
                    && r <= TacticalTargets.DEFAULT_HORDE_RING_MAX + 1e-6, "Unexpected radius " + r);
            String key = Math.round(p.x() * 100.0) + "," + Math.round(p.z() * 100.0);
            assertTrue(seen.add(key), "Duplicate ring point for index " + i);
        }
    }

    @Test
    void safeGoalFilterAvoidsHazardsAndSnapsToNearestStandableSpot() {
        TacticalTargets.HazardWorld lavaPit = new TacticalTargets.HazardWorld() {
            @Override
            public boolean isHazardous(int x, int y, int z) {
                return x == 10 && z == 10 && y >= 63 && y <= 64;
            }

            @Override
            public boolean isStandable(int x, int y, int z) {
                return !(x == 10 && z == 10) && y == 64;
            }
        };

        TacticalTargets.Point3 snapped = TacticalTargets.resolveSafeGoal(
                lavaPit, true, 10.5, 64.0, 10.5, 4.0, 64.0, 4.0);
        assertNotNull(snapped);
        assertNotEquals(10, (int) Math.floor(snapped.x()) & (int) Math.floor(snapped.z()));
        assertTrue(Math.floor(snapped.x()) != 10 || Math.floor(snapped.z()) != 10);

        TacticalTargets.Point3 disabled = TacticalTargets.resolveSafeGoal(
                lavaPit, false, 10.5, 64.0, 10.5, 4.0, 64.0, 4.0);
        assertNotNull(disabled);
        assertEquals(10.5, disabled.x(), 1e-6);

        TacticalTargets.HazardWorld nowhereSafe = new TacticalTargets.HazardWorld() {
            @Override
            public boolean isHazardous(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean isStandable(int x, int y, int z) {
                return false;
            }
        };
        assertNull(TacticalTargets.resolveSafeGoal(nowhereSafe, true, 10.5, 64.0, 10.5, 4.0, 64.0, 4.0));
    }

    @Test
    void stealthAmbushSweepsWhenHealthyAndFleesWhenCriticallyWounded() {
        TacticalTargets.AmbushPlan healthy = TacticalTargets.planAmbushReaction(
                0.0, 64.0, 0.0, 24.0, 64.0, 0.0, 18.0, 20.0, 1.05, 1.32, 0.5);
        assertFalse(healthy.flee());
        assertTrue(healthy.targetX() > 5.0, "Healthy mob sweeps toward sniper sector");
        assertEquals(1.32, healthy.speed(), 1e-6);

        TacticalTargets.AmbushPlan lowHp = TacticalTargets.planAmbushReaction(
                0.0, 64.0, 0.0, 24.0, 64.0, 0.0, 5.0, 20.0, 1.05, 1.32, 0.5);
        assertTrue(lowHp.flee());
        assertTrue(lowHp.targetX() < -5.0, "Critically hurt mob flees opposite to sniper");

        TacticalTargets.AmbushPlan elevatedSniper = TacticalTargets.planAmbushReaction(
                0.0, 64.0, 0.0, 24.0, 84.0, 0.0, 18.0, 20.0, 1.05, 1.32, 0.5);
        assertFalse(elevatedSniper.flee());
        assertTrue(elevatedSniper.targetY() <= 68.0 && elevatedSniper.targetY() >= 60.0,
                "Elevation step toward elevated sniper stays clamped to <= 4 blocks from mobY");
    }

    @Test
    void twoBlockPitHazardIsRejectedWhileSafeOneBlockBridgeRemainsAllowed() {
        TacticalTargets.HazardWorld deepPit = new TacticalTargets.HazardWorld() {
            @Override
            public boolean isHazardous(int x, int y, int z) {
                return x == 10 && z == 10 && y == 62;
            }

            @Override
            public boolean isStandable(int x, int y, int z) {
                return (x != 10 || z != 10) && y == 64;
            }
        };

        TacticalTargets.Point3 pitSnapped = TacticalTargets.resolveSafeGoal(
                deepPit, true, 10.5, 64.0, 10.5, 4.0, 64.0, 4.0);
        assertNotNull(pitSnapped);
        assertTrue(Math.floor(pitSnapped.x()) != 10 || Math.floor(pitSnapped.z()) != 10,
                "Pit 2 blocks below non-standable goal must snap to safe edge");

        TacticalTargets.Point3 eyeHeightSnapped = TacticalTargets.resolveSafeGoal(
                deepPit, true, 10.5, 65.0, 10.5, 4.0, 64.0, 4.0);
        assertNotNull(eyeHeightSnapped);
        assertTrue(Math.floor(eyeHeightSnapped.x()) != 10 || Math.floor(eyeHeightSnapped.z()) != 10,
                "Pit 3 blocks below eye-height air goal must also snap to safe edge");

        TacticalTargets.HazardWorld oneBlockBridge = new TacticalTargets.HazardWorld() {
            @Override
            public boolean isHazardous(int x, int y, int z) {
                return y == 62;
            }

            @Override
            public boolean isStandable(int x, int y, int z) {
                return y == 64;
            }
        };

        TacticalTargets.Point3 keptBridge = TacticalTargets.resolveSafeGoal(
                oneBlockBridge, true, 10.5, 64.0, 10.5, 4.0, 64.0, 4.0);
        assertNotNull(keptBridge);
        assertEquals(10.5, keptBridge.x(), 1e-6);
        assertEquals(64.0, keptBridge.y(), 1e-6);
        assertEquals(10.5, keptBridge.z(), 1e-6);
    }
}
