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
}
