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
}
