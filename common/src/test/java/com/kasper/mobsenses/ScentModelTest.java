package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScentModelTest {

    @Test
    void woundedPlayersLeaveScentTrailTrackedByZombiesAndSpiders() {
        assertFalse(ScentModel.isWounded(20.0, 20.0));
        assertFalse(ScentModel.isWounded(12.0, 20.0));
        assertTrue(ScentModel.isWounded(10.0, 20.0));
        assertTrue(ScentModel.isWounded(3.0, 20.0));

        assertTrue(ScentModel.isScentTracker("minecraft:zombie"));
        assertTrue(ScentModel.isScentTracker("minecraft:husk"));
        assertTrue(ScentModel.isScentTracker("minecraft:spider"));
        assertTrue(ScentModel.isScentTracker("minecraft:cave_spider"));
        assertFalse(ScentModel.isScentTracker("minecraft:skeleton"));
        assertFalse(ScentModel.isScentTracker("minecraft:creeper"));

        ScentModel scent = new ScentModel();
        UUID player = UUID.randomUUID();
        String world = "minecraft:overworld";

        // Healthy player leaves no scent
        assertEquals(0.0, scent.recordPlayer(world, player, 10.0, 64.0, 10.0, 18.0, 20.0, Preset.DEFAULT, 1000L));
        assertNull(scent.findNearestScent(world, 12.0, 64.0, 10.0, 1000L));

        // Badly wounded player leaves a crumb that nearby zombies/spiders smell
        double range = scent.recordPlayer(world, player, 10.0, 64.0, 10.0, 4.0, 20.0, Preset.DEFAULT, 2000L);
        assertTrue(range >= ScentModel.BASE_SCENT_RANGE);
        ScentModel.Crumb found = scent.findNearestScent(world, 15.0, 64.0, 10.0, 2500L);
        assertNotNull(found);
        assertEquals(10.0, found.x(), 1e-6);

        // Crumb expires after CRUMB_LIFETIME_MS
        assertNull(scent.findNearestScent(world, 15.0, 64.0, 10.0, 2000L + ScentModel.CRUMB_LIFETIME_MS + 100L));
    }

    @Test
    void activePlayerCrumbsIsolatesPerPlayerTrail() {
        ScentModel scent = new ScentModel();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String world = "minecraft:overworld";

        scent.recordPlayer(world, alice, 0.0, 64.0, 0.0, 6.0, 20.0, Preset.DEFAULT, 1000L);
        scent.recordPlayer(world, bob, 30.0, 64.0, 30.0, 6.0, 20.0, Preset.DEFAULT, 1100L);
        scent.recordPlayer(world, alice, 5.0, 64.0, 0.0, 6.0, 20.0, Preset.DEFAULT, 1500L);

        assertEquals(2, scent.activePlayerCrumbs(world, alice, 2000L).size());
        assertEquals(1, scent.activePlayerCrumbs(world, bob, 2000L).size());
    }

    @Test
    void waterWashesOffBloodTrailAndRainWeakensScentRange() {
        ScentModel scent = new ScentModel();
        UUID player = UUID.randomUUID();
        String world = "minecraft:overworld";

        double dryRange = scent.recordPlayer(world, player, 0.0, 64.0, 0.0, 4.0, 20.0, Preset.DEFAULT, 1000L, false, false);
        assertTrue(dryRange > 0.0);
        assertEquals(1, scent.activePlayerCrumbs(world, player, 1100L).size());

        // Rain dampens scent range
        UUID rainPlayer = UUID.randomUUID();
        double rainRange = scent.recordPlayer(world, rainPlayer, 20.0, 64.0, 0.0, 4.0, 20.0, Preset.DEFAULT, 1000L, false, true);
        assertTrue(rainRange > 0.0 && rainRange < dryRange);

        // Swimming suppresses new crumbs and rapidly washes away existing trail
        assertEquals(0.0, scent.recordPlayer(world, player, 1.0, 64.0, 0.0, 4.0, 20.0, Preset.DEFAULT, 1500L, true, false));
        for (int i = 0; i < 12; i++) {
            scent.recordPlayer(world, player, 1.0, 64.0, 0.0, 4.0, 20.0, Preset.DEFAULT, 2000L + i * 500L, true, false);
        }
        assertTrue(scent.activePlayerCrumbs(world, player, 7500L).isEmpty(), "Swimming must wash away trail");
    }

    @Test
    void scentWallMufflingAttenuatesBlockedTrailsAndPrefersOpenPaths() {
        assertEquals(14.0, ScentModel.scentRangeThroughWalls(14.0, 0.0, 1.0), 1e-6);
        assertEquals(14.0, ScentModel.scentRangeThroughWalls(14.0, 2.0, 0.0), 1e-6);
        double oneWall = ScentModel.scentRangeThroughWalls(14.0, 1.0, 1.0);
        assertTrue(oneWall > 4.0 && oneWall < 14.0, "One stone wall should partially muffle blood scent: " + oneWall);
        assertEquals(0.0, ScentModel.scentRangeThroughWalls(14.0, 3.5, 1.0), 1e-6);

        ScentModel scent = new ScentModel();
        UUID blockedPlayer = UUID.randomUUID();
        UUID openPlayer = UUID.randomUUID();
        String world = "minecraft:overworld";
        long now = 5000L;

        try {
            MobSenses.settings().resetAll();
            scent.recordPlayer(world, blockedPlayer, 6.0, 64.0, 0.0, 4.0, 20.0, Preset.DEFAULT, now);
            scent.recordPlayer(world, openPlayer, 10.0, 64.0, 0.0, 4.0, 20.0, Preset.DEFAULT, now);

            ScentModel.WallOccluder sealedWallBetween = (fx, fy, fz, tx, ty, tz) -> fx < 8.0 ? 4.0 : 0.0;

            MobSenses.settings().setScentWallMuffling(1.0);
            ScentModel.Crumb chosen = scent.findNearestScent(world, 0.0, 64.0, 0.0, 65.5, now, sealedWallBetween);
            assertNotNull(chosen);
            assertEquals(openPlayer, chosen.playerId(),
                    "Mob must pick reachable open-air crumb over closer crumb sealed behind thick walls");

            MobSenses.settings().setScentWallMuffling(0.0);
            ScentModel.Crumb unfiltered = scent.findNearestScent(world, 0.0, 64.0, 0.0, 65.5, now, sealedWallBetween);
            assertNotNull(unfiltered);
            assertEquals(blockedPlayer, unfiltered.playerId(),
                    "When scent_wall_muffling=0, closer crumb wins regardless of intervening walls");
        } finally {
            MobSenses.settings().resetAll();
        }
    }
}
