package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NoiseCoalescerTest {

    @Test
    void coalescesSameChunkKeepingLoudestAndSeparatesDifferentChunks() {
        NoiseCoalescer coalescer = new NoiseCoalescer();
        coalescer.record("minecraft:overworld", NoiseModel.Kind.STEP, "minecraft:step", 2.5, 64.0, 2.5, 7.0);
        coalescer.record("minecraft:overworld", NoiseModel.Kind.BLOCK_BREAK, "minecraft:block_destroy", 10.5, 64.0, 12.5, 16.0);
        coalescer.record("minecraft:overworld", NoiseModel.Kind.EAT, "minecraft:eat", 5.5, 64.0, 5.5, 5.0);
        // Second chunk (chunkX = 2, chunkZ = 0)
        coalescer.record("minecraft:overworld", NoiseModel.Kind.BELL, "minecraft:bell_ring", 34.5, 64.0, 2.5, 32.0);

        List<NoiseCoalescer.CoalescedNoise> drained = coalescer.drainWorld("minecraft:overworld");
        assertEquals(2, drained.size());
        // Sorted loudest first
        assertEquals(NoiseModel.Kind.BELL, drained.get(0).kind());
        assertEquals(1, drained.get(0).mergedCount());
        assertEquals(NoiseModel.Kind.BLOCK_BREAK, drained.get(1).kind());
        assertEquals(16.0, drained.get(1).range(), 1e-9);
        assertEquals(3, drained.get(1).mergedCount());
        assertTrue(coalescer.drainWorld("minecraft:overworld").isEmpty());
    }

    @Test
    void enforcesPerEventAndPerTickBudgets() {
        NoiseCoalescer coalescer = new NoiseCoalescer(2, 3, 5);
        for (int c = 0; c < 4; c++) {
            coalescer.record("minecraft:overworld", NoiseModel.Kind.EXPLOSION, "minecraft:explode",
                    c * 16 + 8.0, 64.0, 8.0, 40.0);
        }

        List<NoiseCoalescer.Candidate<String>> mobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            mobs.add(new NoiseCoalescer.Candidate<>("mob-" + i, 10.0, 64.0, 8.0, 65.5));
        }

        AtomicInteger traces = new AtomicInteger();
        List<NoiseCoalescer.Alerted<String>> alerted = coalescer.dispatchWorld(
                "minecraft:overworld",
                (ex, ey, ez, radius) -> mobs,
                (sx, sy, sz, tx, ty, tz) -> {
                    traces.incrementAndGet();
                    return 0.0;
                });

        // 2 events max per tick; first event traces 3 mobs (per-event cap), second event traces 2 mobs (hitting 5 per-tick cap)
        assertEquals(5, traces.get());
        assertEquals(5, alerted.size());
    }

    @Test
    void benchmarkCoalescingAndRayBundleStayWellUnderTickBudget() {
        NoiseCoalescer coalescer = new NoiseCoalescer();
        List<NoiseCoalescer.Candidate<String>> listeners = Arrays.asList(
                new NoiseCoalescer.Candidate<>("z1", 6.5, 64.0, 2.5, 65.6),
                new NoiseCoalescer.Candidate<>("z2", 12.5, 64.0, 8.5, 65.6),
                new NoiseCoalescer.Candidate<>("z3", -8.5, 64.0, 4.5, 65.6),
                new NoiseCoalescer.Candidate<>("z4", 18.5, 64.0, -6.5, 65.6)
        );

        RayBundle.OpenGrid grid = (x, y, z) -> x != 5;
        RayBundle.RayCaster single = (sx, sy, sz, ex, ey, ez) -> {
            double[] w = {0.0};
            VoxelRay.walk(sx, sy, sz, ex, ey, ez, 48, (bx, by, bz) -> {
                if (bx == 5) {
                    double c = VoxelRay.chord(sx, sy, sz, ex, ey, ez, bx, by, bz, bx + 1, by + 1, bz + 1);
                    w[0] += RayBundle.chordWeight(c) * AcousticMaterial.STONE.wallWeight();
                }
                return false;
            });
            return w[0];
        };

        long startNs = System.nanoTime();
        int ticks = 200;
        int totalHeard = 0;
        for (int t = 0; t < ticks; t++) {
            for (int i = 0; i < 20; i++) {
                coalescer.record("minecraft:overworld", NoiseModel.Kind.STEP, "minecraft:step",
                        (i % 4) * 16 + 1.5, 64.5, 1.5, 18.0);
            }
            totalHeard += coalescer.dispatchWorld(
                    "minecraft:overworld",
                    (ex, ey, ez, r) -> listeners,
                    (sx, sy, sz, tx, ty, tz) -> RayBundle.trace(single, grid, sx, sy, sz, tx, ty, tz)).size();
        }
        double msPerTick = (System.nanoTime() - startNs) / 1_000_000.0 / ticks;
        assertTrue(totalHeard > 0);
        assertTrue(msPerTick < 5.0, "Expected < 5 ms per server tick, got " + msPerTick + " ms");
    }

    @Test
    void loudSoundCoverMasksNearbyQuietStepsUntilExpired() {
        NoiseCoalescer coalescer = new NoiseCoalescer();
        long t0 = 10_000L;
        coalescer.registerSoundCover("minecraft:overworld", NoiseModel.Kind.EXPLOSION, 0.0, 64.0, 0.0, 32.0, t0);

        double masked = coalescer.applySoundCover(
                "minecraft:overworld", NoiseModel.Kind.STEP, 4.0, 64.0, 4.0, 8.0, t0 + 400L);
        assertTrue(masked < 8.0, "Nearby explosion should mask footsteps: " + masked);

        double unmaskedFar = coalescer.applySoundCover(
                "minecraft:overworld", NoiseModel.Kind.STEP, 60.0, 64.0, 60.0, 8.0, t0 + 400L);
        assertEquals(8.0, unmaskedFar, 1e-6);

        double afterExpiry = coalescer.applySoundCover(
                "minecraft:overworld", NoiseModel.Kind.STEP, 4.0, 64.0, 4.0, 8.0, t0 + 5000L);
        assertEquals(8.0, afterExpiry, 1e-6);
    }

    @Test
    void rapidFireGunshotsMergeCoverInsteadOfEvictingDistantExplosionCover() {
        NoiseCoalescer coalescer = new NoiseCoalescer();
        long t0 = 20_000L;
        coalescer.registerSoundCover("minecraft:overworld", NoiseModel.Kind.EXPLOSION, 100.0, 64.0, 100.0, 40.0, t0);
        for (int i = 0; i < 45; i++) {
            coalescer.registerSoundCover("minecraft:overworld", NoiseModel.Kind.GUNSHOT, 0.5, 64.0, 0.5, 36.0, t0 + i * 50L);
        }
        double maskedNearExplosion = coalescer.applySoundCover(
                "minecraft:overworld", NoiseModel.Kind.STEP, 104.0, 64.0, 100.0, 8.0, t0 + 2300L);
        assertTrue(maskedNearExplosion < 8.0, "Rapid gunfire must not evict distant explosion cover: " + maskedNearExplosion);
    }
}
