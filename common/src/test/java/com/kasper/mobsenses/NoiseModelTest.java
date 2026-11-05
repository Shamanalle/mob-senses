package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoiseModelTest {

    @Test
    void classifiesEveryNoiseKindAndIgnoresUnrelatedEvents() {
        assertEquals(NoiseModel.Kind.STEP, NoiseModel.classifyEvent("minecraft:step"));
        assertEquals(NoiseModel.Kind.STEP, NoiseModel.classifyEvent("swim"));
        assertEquals(NoiseModel.Kind.BLOCK_BREAK, NoiseModel.classifyEvent("minecraft:block_destroy"));
        assertEquals(NoiseModel.Kind.BLOCK_PLACE, NoiseModel.classifyEvent("minecraft:block_place"));
        assertEquals(NoiseModel.Kind.DOOR, NoiseModel.classifyEvent("minecraft:block_open"));
        assertEquals(NoiseModel.Kind.DOOR, NoiseModel.classifyEvent("minecraft:block_close"));
        assertEquals(NoiseModel.Kind.CONTAINER, NoiseModel.classifyEvent("minecraft:container_open"));
        assertEquals(NoiseModel.Kind.COMBAT, NoiseModel.classifyEvent("minecraft:entity_damage"));
        assertEquals(NoiseModel.Kind.PROJECTILE, NoiseModel.classifyEvent("minecraft:projectile_land"));
        assertEquals(NoiseModel.Kind.EXPLOSION, NoiseModel.classifyEvent("minecraft:explode"));
        assertEquals(NoiseModel.Kind.BELL, NoiseModel.classifyEvent("minecraft:bell_ring"));
        assertEquals(NoiseModel.Kind.NOTE_BLOCK, NoiseModel.classifyEvent("minecraft:note_block_play"));
        assertEquals(NoiseModel.Kind.EAT, NoiseModel.classifyEvent("minecraft:eat"));
        assertNull(NoiseModel.classifyEvent("minecraft:sculk_sensor_tendrils_clicking"));
        assertNull(NoiseModel.classifyEvent(""));
        assertNull(NoiseModel.classifyEvent(null));
    }

    @Test
    void stepRangeRespectsPostureAndBlockMaterial() {
        double walkStone = NoiseModel.baseRange(
                "minecraft:step", NoiseModel.Posture.WALKING, AcousticMaterial.STONE, "minecraft:stone");
        double sprintStone = NoiseModel.baseRange(
                "minecraft:step", NoiseModel.Posture.SPRINTING, AcousticMaterial.STONE, "minecraft:stone");
        double sneakStone = NoiseModel.baseRange(
                "minecraft:step", NoiseModel.Posture.SNEAKING, AcousticMaterial.STONE, "minecraft:stone");
        double walkWool = NoiseModel.baseRange(
                "minecraft:step", NoiseModel.Posture.WALKING, AcousticMaterial.WOOL, "minecraft:white_wool");
        double walkGravel = NoiseModel.baseRange(
                "minecraft:step", NoiseModel.Posture.WALKING, AcousticMaterial.EARTH, "minecraft:gravel");
        double sneakWool = NoiseModel.baseRange(
                "minecraft:step", NoiseModel.Posture.SNEAKING, AcousticMaterial.WOOL, "minecraft:white_carpet");

        assertTrue(sprintStone > walkStone, "Sprinting must be louder than walking");
        assertTrue(walkStone > sneakStone, "Walking must be louder than sneaking");
        assertTrue(walkGravel > walkStone, "Gravel crunches louder than smooth stone");
        assertTrue(walkWool < walkStone * 0.3, "Wool muffles footsteps dramatically");
        assertTrue(sneakWool < NoiseModel.MIN_AUDIBLE_RANGE, "Sneaking on wool/carpet falls below audible threshold");
    }

    @Test
    void blockBreakingAndPlacingScaleWithMaterialAndPreset() {
        double breakStone = NoiseModel.baseRange(
                "minecraft:block_destroy", NoiseModel.Posture.WALKING, AcousticMaterial.STONE, "minecraft:stone");
        double breakWool = NoiseModel.baseRange(
                "minecraft:block_destroy", NoiseModel.Posture.WALKING, AcousticMaterial.WOOL, "minecraft:white_wool");
        double placeStone = NoiseModel.baseRange(
                "minecraft:block_place", NoiseModel.Posture.WALKING, AcousticMaterial.STONE, "minecraft:stone");

        assertTrue(breakStone > placeStone, "Breaking a block is louder than placing one");
        assertTrue(breakStone > breakWool, "Breaking stone is louder than breaking wool");

        double defaultBreak = NoiseModel.effectiveRange(breakStone, Preset.DEFAULT, 1.0);
        double hardcoreBreak = NoiseModel.effectiveRange(breakStone, Preset.HARDCORE, 1.0);
        assertTrue(hardcoreBreak > defaultBreak);
        assertEquals(0.0, NoiseModel.effectiveRange(breakStone, Preset.OFF, 1.0));
    }

    @Test
    void wallOcclusionAttenuatesAudibleRange() {
        double base = 16.0;
        assertEquals(base, NoiseModel.audibleRange(base, 0.0), 1e-9);
        double oneStoneWall = NoiseModel.audibleRange(base, AcousticMaterial.STONE.wallWeight());
        double twoStoneWalls = NoiseModel.audibleRange(base, 2.0 * AcousticMaterial.STONE.wallWeight());
        assertTrue(oneStoneWall < base && oneStoneWall > twoStoneWalls);
        assertTrue(twoStoneWalls > 0.0);
        assertEquals(0.0, NoiseModel.audibleRange(base, RayBundle.MAX_RAY_THICKNESS));
    }
}
