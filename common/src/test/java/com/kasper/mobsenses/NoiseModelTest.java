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

    @Test
    void firearmAndTaCZGunshotsAreLoudWhileSuppressorsMuffleThem() {
        assertTrue(NoiseModel.isFirearmItem("tacz:modern_kinetic_gun"));
        assertTrue(NoiseModel.isFirearmItem("pointblank:glock17"));
        assertTrue(NoiseModel.isFirearmItem("cgm:assault_rifle"));
        assertTrue(NoiseModel.isFirearmItem("scguns:iron_musket"));
        assertTrue(NoiseModel.isFirearmItem("superbwarfare:m4a1"));
        assertTrue(NoiseModel.isFirearmItem("sbw:rpg_7"));
        assertTrue(NoiseModel.isFirearmItem("jeg:combat_rifle"));
        assertFalse(NoiseModel.isFirearmItem("minecraft:gunpowder"));
        assertFalse(NoiseModel.isFirearmItem("tacz:ammo"));
        assertFalse(NoiseModel.isFirearmItem("tacz:gun_smith_table"));
        assertFalse(NoiseModel.isFirearmItem("superbwarfare:tactical_vest"));
        assertFalse(NoiseModel.isFirearmItem("superbwarfare:combat_helmet"));
        assertFalse(NoiseModel.isFirearmItem("superbwarfare:wrench"));
        assertFalse(NoiseModel.isFirearmItem("superbwarfare:drone"));
        assertFalse(NoiseModel.isFirearmItem("jeg:gunmetal_ingot"));
        assertFalse(NoiseModel.isFirearmItem("jeg:scrap"));
        assertFalse(NoiseModel.isFirearmItem("jeg:rifle_part"));
        assertFalse(NoiseModel.isFirearmItem("minecraft:bow"));

        assertTrue(NoiseModel.isBulletEntity("tacz:bullet"));
        assertTrue(NoiseModel.isBulletEntity("tacz:kinetic_bullet"));
        assertTrue(NoiseModel.isBulletEntity("cgm:projectile"));
        assertTrue(NoiseModel.isBulletEntity("superbwarfare:projectile"));
        assertTrue(NoiseModel.isBulletEntity("sbw:mortar_shell"));
        assertTrue(NoiseModel.isBulletEntity("jeg:projectile"));
        assertFalse(NoiseModel.isBulletEntity("superbwarfare:apc"));
        assertFalse(NoiseModel.isBulletEntity("minecraft:arrow"));
        assertFalse(NoiseModel.isBulletEntity("minecraft:zombie"));

        assertTrue(NoiseModel.isSilencedFirearm(
                "tacz:modern_kinetic_gun",
                "{AttachmentMUZZLE:{id:\"tacz:muzzle_silence_phantom_s1\"}}"));
        assertFalse(NoiseModel.isSilencedFirearm(
                "tacz:modern_kinetic_gun",
                "{AttachmentMUZZLE:{id:\"tacz:muzzle_brake_cthulhu\"}}"));

        assertEquals(NoiseModel.Kind.GUNSHOT, NoiseModel.classifyEvent("mobsenses:gunshot"));
        assertEquals(NoiseModel.Kind.GUNSHOT, NoiseModel.classifyEvent("mobsenses:gunshot_silenced"));
        assertEquals(NoiseModel.Kind.GUNSHOT, NoiseModel.classifyEvent("mobsenses:bullet_impact"));

        double gunshot = NoiseModel.baseRange("mobsenses:gunshot", NoiseModel.Posture.WALKING, null, null);
        double silenced = NoiseModel.baseRange("mobsenses:gunshot_silenced", NoiseModel.Posture.WALKING, null, null);
        double impact = NoiseModel.baseRange("mobsenses:bullet_impact", NoiseModel.Posture.WALKING, null, null);
        double bow = NoiseModel.baseRange("minecraft:projectile_shoot", NoiseModel.Posture.WALKING, null, null);

        assertEquals(44.0, gunshot, 1e-6);
        assertEquals(16.0, silenced, 1e-6);
        assertEquals(20.0, impact, 1e-6);
        assertTrue(gunshot > silenced * 2.5);
        assertTrue(gunshot > bow * 3.0);
    }
}
