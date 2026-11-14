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

    @Test
    void perWeaponOverridesWeatherAndHordeSettingsWork() throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        ServerSettings s = new ServerSettings(file);
        s.read("gunshot_range=44\n"
                + "gunshot_silenced_range=16\n"
                + "gun.tacz\\:ai_awp.range=85\n"
                + "gun.glock.silenced_range=6\n"
                + "rain_muffling=0.3\n"
                + "thunder_muffling=0.5\n"
                + "feather_falling_muffling=0.25\n"
                + "max_alerted_mobs=24\n"
                + "horde_alert_range=12\n"
                + "alert_cooldown_ms=500\n"
                + "investigation_seconds=15\n");
        s.save();

        ServerSettings loaded = new ServerSettings(file);
        loaded.load();
        assertEquals(85.0, loaded.gunRangeFor("tacz:modern_kinetic_gun {GunId:\"tacz:ai_awp\"}", false), 1e-6);
        assertEquals(44.0, loaded.gunRangeFor("tacz:modern_kinetic_gun {GunId:\"tacz:ak47\"}", false), 1e-6);
        assertEquals(6.0, loaded.gunRangeFor("pointblank:glock_17", true), 1e-6);
        assertEquals(16.0, loaded.gunRangeFor("tacz:hk416", true), 1e-6);

        assertEquals(0.7, loaded.weatherMultiplier(true, false), 1e-6);
        assertEquals(0.5, loaded.weatherMultiplier(true, true), 1e-6);
        assertEquals(1.0, loaded.weatherMultiplier(false, false), 1e-6);
        assertEquals(0.25, loaded.featherFallingMuffling(), 1e-6);

        assertEquals(24, loaded.maxAlertedMobs());
        assertEquals(12.0, loaded.hordeAlertRange(), 1e-6);
        assertEquals(500L, loaded.alertCooldownMs());
        assertEquals(15.0, loaded.investigationSeconds(), 1e-6);
    }

    @Test
    void upgradesLegacyConfigFileKeepingExistingValues() throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        Files.writeString(file, "preset=hardcore\nhearing=1.5\nvoice=false\n", StandardCharsets.UTF_8);
        ServerSettings s = new ServerSettings(file);
        s.load();
        assertEquals(Preset.HARDCORE, s.preset());
        assertEquals(1.5, s.hearing(), 1e-6);
        assertFalse(s.voice());
        String upgraded = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(upgraded.contains("preset=hardcore"), upgraded);
        assertTrue(upgraded.contains("gunshot_range=44"), upgraded);
        assertTrue(upgraded.contains("gunshot_silenced_range=16"), upgraded);
        assertTrue(upgraded.contains("rain_muffling=0.25"), upgraded);
        assertTrue(upgraded.contains("through_wall_hearing=true"), upgraded);
        assertTrue(upgraded.contains("scent_max_range=24"), upgraded);
    }

    @Test
    void sightScentWallAndResetOptionsWork() throws Exception {
        ServerSettings s = new ServerSettings(null);
        assertTrue(s.setOption("through_wall_hearing", "false"));
        assertFalse(s.throughWallHearing());
        assertTrue(s.setOption("surface_sound_scaling", "false"));
        assertFalse(s.surfaceSoundScaling());
        assertTrue(s.setOption("held_light_bonus", "1.2"));
        assertEquals(1.2, s.heldLightBonus(), 1e-6);
        assertTrue(s.setOption("darkness_min_visibility", "0.1"));
        assertEquals(0.1, s.darknessMinVisibility(), 1e-6);
        assertTrue(s.setOption("sneak_sight_multiplier", "0.4"));
        assertEquals(0.4, s.sneakSightMultiplier(), 1e-6);
        assertTrue(s.setOption("sprint_sight_multiplier", "1.5"));
        assertEquals(1.5, s.sprintSightMultiplier(), 1e-6);
        assertTrue(s.setOption("scent_max_range", "36"));
        assertEquals(36.0, s.scentMaxRange(), 1e-6);
        assertTrue(s.setOption("scent_duration_seconds", "30"));
        assertEquals(30.0, s.scentDurationSeconds(), 1e-6);
        assertTrue(s.setOption("scent_mobs", "minecraft:wolf, alexsmobs:grizzly_bear"));
        assertTrue(s.isScentMob("minecraft:wolf"));
        assertTrue(s.setOption("horde_max_allies", "8"));
        assertEquals(8, s.hordeMaxAllies());

        assertTrue(s.setOption("horde_max_allies", "default"));
        assertEquals(4, s.hordeMaxAllies());
        s.resetAll();
        assertTrue(s.throughWallHearing());
        assertTrue(s.surfaceSoundScaling());
        assertEquals(0.5, s.heldLightBonus(), 1e-6);
        assertEquals(24.0, s.scentMaxRange(), 1e-6);
    }

    @Test
    void writeStateAndReadStateRoundTripIncludingWorldNormalization() {
        ServerSettings src = new ServerSettings(null);
        src.setPreset(Preset.SUBTLE);
        src.setWorldPreset("minecraft:the_nether", Preset.HARDCORE);
        src.setHearing(1.75);
        src.setSight(0.8);
        src.setAllowClientHud(false);
        assertTrue(src.setOption("gunshot_range", "64"));

        assertEquals(Preset.HARDCORE, src.presetOf("the_nether"));
        assertEquals(Preset.HARDCORE, src.presetOf("minecraft:the_nether"));

        java.util.Properties wire = new java.util.Properties();
        src.writeState(wire, "the_nether");
        assertEquals("hardcore", wire.getProperty("preset"));
        assertEquals("subtle", wire.getProperty("server_preset"));
        assertEquals("false", wire.getProperty("allow_client_hud"));

        ServerSettings dst = new ServerSettings(null);
        dst.readState(wire);
        assertEquals(Preset.SUBTLE, dst.preset());
        assertEquals(Preset.HARDCORE, dst.presetOf("the_nether"));
        assertEquals(1.75, dst.hearing(), 1e-6);
        assertEquals(0.8, dst.sight(), 1e-6);
        assertFalse(dst.allowClientHud());
        assertEquals(64.0, dst.gunshotRange(), 1e-6);
    }

    @Test
    void tacticalFeatureFlagsAndDefaultHordeRangePersistAndRoundTrip() throws Exception {
        Path file = dir.resolve("tactical-settings.properties");
        ServerSettings s = new ServerSettings(file);
        s.load();

        assertEquals(8.0, s.hordeAlertRange(), 1e-6);
        assertTrue(s.soundCoverEnabled());
        assertTrue(s.decoyThrowEnabled());
        assertTrue(s.safeGoalFilter());
        assertTrue(s.suspicionEnabled());

        assertTrue(s.setOption("sound_cover_enabled", "false"));
        assertTrue(s.setOption("decoy_throw_enabled", "false"));
        assertTrue(s.setOption("safe_goal_filter", "false"));
        assertTrue(s.setOption("suspicion_enabled", "false"));
        assertTrue(s.setOption("horde_alert_range", "14"));
        s.save();

        ServerSettings reloaded = new ServerSettings(file);
        reloaded.load();
        assertFalse(reloaded.soundCoverEnabled());
        assertFalse(reloaded.decoyThrowEnabled());
        assertFalse(reloaded.safeGoalFilter());
        assertFalse(reloaded.suspicionEnabled());
        assertEquals(14.0, reloaded.hordeAlertRange(), 1e-6);

        java.util.Properties wire = new java.util.Properties();
        reloaded.writeState(wire, "minecraft:overworld");
        ServerSettings clientCopy = new ServerSettings(null);
        clientCopy.readState(wire);
        assertFalse(clientCopy.soundCoverEnabled());
        assertFalse(clientCopy.decoyThrowEnabled());
        assertFalse(clientCopy.safeGoalFilter());
        assertFalse(clientCopy.suspicionEnabled());

        reloaded.resetAll();
        assertTrue(reloaded.soundCoverEnabled());
        assertTrue(reloaded.decoyThrowEnabled());
        assertTrue(reloaded.safeGoalFilter());
        assertTrue(reloaded.suspicionEnabled());
        assertEquals(8.0, reloaded.hordeAlertRange(), 1e-6);
    }

    @Test
    void nonFiniteRejectionWorldAliasDeduplicationAndCleanFormatting() {
        ServerSettings s = new ServerSettings(null);
        s.setHearing(1.25);
        s.setMaxAlertedMobs(12);

        assertFalse(s.setOption("hearing", "NaN"));
        assertFalse(s.setOption("hearing", "Infinity"));
        assertFalse(s.setOption("hearing", "-Infinity"));
        assertFalse(s.setOption("max_alerted_mobs", "NaN"));
        assertFalse(s.setOption("max_alerted_mobs", "Infinity"));
        assertFalse(s.setOption("investigate_speed", "NaN"));
        assertEquals(1.25, s.hearing(), 1e-6);
        assertEquals(12, s.maxAlertedMobs());

        assertNull(s.worldOwnPreset("minecraft:overworld"));
        s.setWorldPreset("the_nether", Preset.SUBTLE);
        assertEquals(Preset.SUBTLE, s.worldOwnPreset("minecraft:the_nether"));
        s.setWorldPreset("minecraft:the_nether", Preset.HARDCORE);
        assertEquals(1, s.worlds().size(), "World alias updates must deduplicate equivalent keys");
        assertEquals(Preset.HARDCORE, s.worldOwnPreset("the_nether"));
        s.setWorldPreset("the_nether", null);
        assertTrue(s.worlds().isEmpty());
        assertNull(s.worldOwnPreset("minecraft:the_nether"));

        assertEquals("0", ServerSettings.format(-0.0));
        assertEquals("0", ServerSettings.format(-0.001));
        assertEquals("0", ServerSettings.format(Double.NaN));
        assertEquals("0", ServerSettings.format(Double.POSITIVE_INFINITY));
        assertEquals("1", ServerSettings.format(1.0));
        assertEquals("0.75", ServerSettings.format(0.75));
        assertEquals("1.2", ServerSettings.format(1.20));
    }

    @Test
    void v161SettingsAutoUpgradeWireSyncBossIdsAndAliases() throws Exception {
        assertEquals("scent_wall_muffling", ServerSettings.canonicalOptionKey("scent_muffling"));
        assertEquals("scent_wall_muffling", ServerSettings.canonicalOptionKey("blood_wall_muffling"));
        assertEquals("ambush_reaction_enabled", ServerSettings.canonicalOptionKey("ambush_reaction"));
        assertEquals("ambush_flee_health_threshold", ServerSettings.canonicalOptionKey("ambush_flee_hp"));
        assertEquals("ignore_bosses", ServerSettings.canonicalOptionKey("boss_immunity"));

        assertTrue(ServerSettings.isKnownBossId("minecraft:wither"));
        assertTrue(ServerSettings.isKnownBossId("ENDER_DRAGON"));
        assertTrue(ServerSettings.isKnownBossId("minecraft:elder_guardian"));
        assertTrue(ServerSettings.isKnownBossId("minecraft:warden"));
        assertTrue(ServerSettings.isKnownBossId("cataclysm:ignis_boss"));
        assertTrue(ServerSettings.isKnownBossId("twilightforest:boss_hydra"));
        assertFalse(ServerSettings.isKnownBossId("minecraft:zombie"));
        assertFalse(ServerSettings.isKnownBossId(""));
        assertFalse(ServerSettings.isKnownBossId(null));

        // Auto-upgrade legacy 1.6.0 properties while keeping user values intact
        Path file = dir.resolve("upgrade-160.properties");
        Files.writeString(file, "preset=hardcore\nhearing=1.35\n", StandardCharsets.UTF_8);
        ServerSettings s = new ServerSettings(file);
        s.load();
        assertEquals(Preset.HARDCORE, s.preset());
        assertEquals(1.35, s.hearing(), 1e-6);
        assertEquals(1.0, s.scentWallMuffling(), 1e-6);
        assertTrue(s.ambushReactionEnabled());
        assertEquals(35.0, s.ambushFleeHealthThreshold(), 1e-6);
        assertTrue(s.ignoreBosses());

        String upgraded = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(upgraded.contains("scent_wall_muffling=1"), upgraded);
        assertTrue(upgraded.contains("ambush_reaction_enabled=true"), upgraded);
        assertTrue(upgraded.contains("ambush_flee_health_threshold=35"), upgraded);
        assertTrue(upgraded.contains("ignore_bosses=true"), upgraded);

        assertFalse(s.setOption("scent_wall_muffling", "5"));
        assertFalse(s.setOption("ambush_flee_health_threshold", "120"));
        assertTrue(s.setOption("scent_wall_muffling", "2.25"));
        assertTrue(s.setOption("ambush_reaction_enabled", "false"));
        assertTrue(s.setOption("ambush_flee_health_threshold", "0"));
        assertTrue(s.setOption("ignore_bosses", "false"));
        s.save();

        java.util.Properties wire = new java.util.Properties();
        s.writeState(wire, "minecraft:overworld");
        ServerSettings clientCopy = new ServerSettings(null);
        clientCopy.readState(wire);
        assertEquals(2.25, clientCopy.scentWallMuffling(), 1e-6);
        assertFalse(clientCopy.ambushReactionEnabled());
        assertEquals(0.0, clientCopy.ambushFleeHealthThreshold(), 1e-6);
        assertFalse(clientCopy.ignoreBosses());

        s.resetAll();
        assertEquals(1.0, s.scentWallMuffling(), 1e-6);
        assertTrue(s.ambushReactionEnabled());
        assertEquals(35.0, s.ambushFleeHealthThreshold(), 1e-6);
        assertTrue(s.ignoreBosses());
    }
}
