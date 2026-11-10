package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerSettingsTest {

    @TempDir
    Path dir;

    @Test
    void writesDefaultsOnFirstStart() throws Exception {
        ServerSettings s = new ServerSettings(dir.resolve(ServerSettings.FILE_NAME));
        s.load();
        String text = Files.readString(s.file(), StandardCharsets.UTF_8);
        assertTrue(text.contains("preset=default"), text);
        assertTrue(text.contains("hearing=1"), text);
        assertTrue(text.contains("voice=true"), text);
        assertTrue(text.contains("gunshot_range=44"), text);
        assertTrue(text.contains("gunshot_silenced_range=16"), text);
        assertTrue(text.contains("bullet_impact_range=20"), text);
        assertTrue(text.contains("настройки сервера"), "Russian comments: " + text);
    }

    @Test
    void savedValuesComeBack() {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        ServerSettings s = new ServerSettings(file);
        s.load();
        s.setPreset(Preset.HARDCORE);
        s.setHearing(1.25);
        s.setSight(0.5);
        s.setVoice(false);
        s.setWorldPreset("minecraft:the_nether", Preset.OFF);
        s.save();

        ServerSettings again = new ServerSettings(file);
        again.load();
        assertEquals(Preset.HARDCORE, again.preset());
        assertEquals(1.25, again.hearing(), 1e-9);
        assertEquals(0.5, again.sight(), 1e-9);
        assertFalse(again.voice());
        assertEquals(Preset.OFF, again.presetOf("minecraft:the_nether"));
        assertEquals(Preset.HARDCORE, again.presetOf("minecraft:overworld"));
        assertEquals(Preset.HARDCORE, again.presetOf(null));
    }

    @Test
    void brokenValuesKeepTheirDefaults() throws Exception {
        ServerSettings s = new ServerSettings(null);
        s.read("preset=loud\nhearing=banana\nsight=7\nvoice=maybe\nworld.minecraft\\:end.preset=nope\n");
        assertEquals(Preset.DEFAULT, s.preset());
        assertEquals(1.0, s.hearing());
        assertEquals(1.0, s.sight());
        assertTrue(s.voice());
        assertTrue(s.worlds().isEmpty());
    }

    @Test
    void editedFileIsReadAgain() throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        ServerSettings s = new ServerSettings(file);
        s.load();
        assertFalse(s.reloadIfChanged());
        Files.writeString(file, "preset=subtle\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertTrue(s.reloadIfChanged());
        assertEquals(Preset.SUBTLE, s.preset());
    }

    @Test
    void multipliersAreClampedAndFormatted() {
        ServerSettings s = new ServerSettings(null);
        s.setHearing(9);
        assertEquals(ServerSettings.MAX_MULTIPLIER, s.hearing());
        s.setSight(-1);
        assertEquals(0.0, s.sight());
        assertEquals("1", ServerSettings.format(1.0));
        assertEquals("0.75", ServerSettings.format(0.75));
        assertEquals("1.5", ServerSettings.format(1.5));
    }

    @Test
    void firearmAndCategoryRangesAndMobListsAreReadAndSaved() throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        ServerSettings s = new ServerSettings(file);
        s.read("gunshot_range=96\n"
                + "gunshot_silenced_range=8\n"
                + "bullet_impact_range=30\n"
                + "wall_muffling=0.5\n"
                + "step_range=10\n"
                + "sneak_step_multiplier=0\n"
                + "sprint_step_multiplier=2\n"
                + "scent_enabled=false\n"
                + "scent_health_threshold=35\n"
                + "blood_particles=false\n"
                + "held_light_enabled=false\n"
                + "darkness_stealth_enabled=false\n"
                + "extra_listeners=alexsmobs:murmur, mutantmonsters:*\n"
                + "ignored_mobs=minecraft:slime, enderman\n");
        s.save();

        ServerSettings loaded = new ServerSettings(file);
        loaded.load();
        assertEquals(96.0, loaded.gunshotRange(), 1e-6);
        assertEquals(8.0, loaded.gunshotSilencedRange(), 1e-6);
        assertEquals(30.0, loaded.bulletImpactRange(), 1e-6);
        assertEquals(0.5, loaded.wallMuffling(), 1e-6);
        assertEquals(10.0, loaded.stepRange(), 1e-6);
        assertEquals(0.0, loaded.sneakStepMultiplier(), 1e-6);
        assertEquals(2.0, loaded.sprintStepMultiplier(), 1e-6);
        assertFalse(loaded.scentEnabled());
        assertEquals(35.0, loaded.scentHealthThreshold(), 1e-6);
        assertFalse(loaded.bloodParticles());
        assertFalse(loaded.heldLightEnabled());
        assertFalse(loaded.darknessStealthEnabled());

        assertTrue(loaded.isExtraListener("alexsmobs:murmur"));
        assertTrue(loaded.isExtraListener("mutantmonsters:mutant_zombie"));
        assertFalse(loaded.isExtraListener("minecraft:cow"));

        assertTrue(loaded.isIgnoredMob("minecraft:slime"));
        assertTrue(loaded.isIgnoredMob("minecraft:enderman"));
        assertFalse(loaded.isIgnoredMob("minecraft:zombie"));

        assertEquals(96.0, NoiseModel.baseRange("mobsenses:gunshot", NoiseModel.Posture.WALKING, null, null, loaded), 1e-6);
        assertEquals(8.0, NoiseModel.baseRange("mobsenses:gunshot_silenced", NoiseModel.Posture.WALKING, null, null, loaded), 1e-6);
        assertEquals(30.0, NoiseModel.baseRange("mobsenses:bullet_impact", NoiseModel.Posture.WALKING, null, null, loaded), 1e-6);
        assertEquals(0.0, NoiseModel.baseRange("minecraft:step", NoiseModel.Posture.SNEAKING, AcousticMaterial.STONE, "minecraft:stone", loaded), 1e-6);
    }
}
