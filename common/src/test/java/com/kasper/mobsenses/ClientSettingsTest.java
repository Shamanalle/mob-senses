package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

class ClientSettingsTest {

    @Test
    void writesDefaultsAndReloadsLiveFromDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        ClientSettings cfg = new ClientSettings(file);
        cfg.load();
        assertTrue(Files.exists(file), "Default client config file must be written on first load");
        assertTrue(cfg.enabled());
        assertEquals(ClientSettings.Anchor.TOP_LEFT, cfg.position());
        assertEquals(10, cfg.x());
        assertEquals(10, cfg.y());
        assertEquals(100, cfg.opacity());
        assertFalse(cfg.compact());
        assertTrue(cfg.showNoise());
        assertTrue(cfg.showLight());
        assertTrue(cfg.showScent());

        String edited = "enabled=true\n"
                + "position=bottom_right\n"
                + "x=16\n"
                + "y=24\n"
                + "opacity=75\n"
                + "compact=true\n"
                + "show_light=false\n";
        Files.write(file, edited.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000L));

        assertTrue(cfg.reloadIfChanged());
        assertEquals(ClientSettings.Anchor.BOTTOM_RIGHT, cfg.position());
        assertEquals(16, cfg.x());
        assertEquals(24, cfg.y());
        assertEquals(75, cfg.opacity());
        assertTrue(cfg.compact());
        assertFalse(cfg.showLight());
    }

    @Test
    void computesScreenCoordinatesAndClampsToScreenBounds() {
        ClientSettings cfg = new ClientSettings(null);
        cfg.setPosition(ClientSettings.Anchor.TOP_LEFT);
        cfg.setX(12);
        cfg.setY(14);
        assertEquals(12, cfg.computeX(400, 118));
        assertEquals(14, cfg.computeY(240, 44));

        cfg.setPosition(ClientSettings.Anchor.BOTTOM_RIGHT);
        assertEquals(400 - 118 - 12, cfg.computeX(400, 118));
        assertEquals(240 - 44 - 14, cfg.computeY(240, 44));

        cfg.setPosition(ClientSettings.Anchor.HOTBAR);
        cfg.setX(0);
        cfg.setY(6);
        assertEquals((400 - 118) / 2, cfg.computeX(400, 118));
        assertEquals(240 - 54 - 44 - 6, cfg.computeY(240, 44));
    }

    @Test
    void supportsStylesShowRangeApplyAlphaAndAutoUpgradesExistingFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        String legacy = "enabled=true\nposition=top_right\nopacity=50\n";
        Files.write(file, legacy.getBytes(StandardCharsets.UTF_8));

        ClientSettings cfg = new ClientSettings(file);
        cfg.load();
        assertEquals(ClientSettings.Anchor.TOP_RIGHT, cfg.position());
        assertEquals(ClientSettings.Style.TACTICAL, cfg.style());
        assertTrue(cfg.showRange());

        String upgradedDisk = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(upgradedDisk.contains("style=tactical"), "Legacy client config should gain style key");
        assertTrue(upgradedDisk.contains("show_range=true"), "Legacy client config should gain show_range key");

        // 50% opacity * 0.5 fade -> ~25% of 200 alpha = 50
        int scaled = cfg.applyAlpha(0xC834D399, 0.5);
        assertEquals(50, (scaled >>> 24) & 0xFF);
        assertEquals(0x34D399, scaled & 0x00FFFFFF);

        assertEquals(ClientSettings.Style.MINIMAL, ClientSettings.Style.byId("minimal"));
        assertEquals(ClientSettings.Style.MINIMAL, ClientSettings.Style.byId("slim"));
        assertEquals(ClientSettings.Style.TACTICAL, ClientSettings.Style.byId("tactical"));
    }
}
