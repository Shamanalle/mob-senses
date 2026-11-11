package com.kasper.mobsenses;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminCommandsTest {

    @TempDir
    Path dir;

    private static AdminCommands.Context as(String language, String world) {
        return new AdminCommands.Context() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public String world() {
                return world;
            }

            @Override
            public List<String> worlds() {
                return List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
            }
        };
    }

    private static final AdminCommands.Context CONSOLE = as("", null);

    @BeforeEach
    void start() {
        MobSenses.start(dir, new MobSenses.Platform("Test", "1.21.1"), false);
    }

    @Test
    void statusNamesVersionPlatformAndPreset() {
        String r = AdminCommands.execute("", CONSOLE).toString();
        assertTrue(r.contains("Mob Senses " + BuildInfo.version() + " on Test 1.21.1"), r);
        assertTrue(r.contains("Preset: Default (default)"), r);
        assertTrue(r.contains("Simple Voice Chat is not installed"), r);
        assertFalse(r.contains("command.mobsenses."), r);
    }

    @Test
    void presetIsSavedAndBadOnesRefused() {
        Reply done = AdminCommands.execute("preset hardcore", CONSOLE);
        assertFalse(done.refused());
        assertEquals(Preset.HARDCORE, MobSenses.settings().preset());
        Reply bad = AdminCommands.execute("preset loud", CONSOLE);
        assertTrue(bad.refused());
        assertTrue(bad.toString().contains("off, subtle, default, hardcore"), bad.toString());
        assertEquals(Preset.HARDCORE, MobSenses.settings().preset());
    }

    @Test
    void repliesInThePlayersLanguage() {
        String r = AdminCommands.execute("preset subtle", as("ru_ru", "minecraft:overworld")).toString();
        assertTrue(r.contains("Теперь пресет — Мягкий (subtle)."), r);
    }

    @Test
    void multipliersAcceptCommasAndRefuseOutOfRange() {
        assertFalse(AdminCommands.execute("hearing 1,5", CONSOLE).refused());
        assertEquals(1.5, MobSenses.settings().hearing(), 1e-9);
        assertTrue(AdminCommands.execute("sight 4", CONSOLE).refused());
        assertTrue(AdminCommands.execute("sight x", CONSOLE).refused());
        assertEquals(1.0, MobSenses.settings().sight(), 1e-9);
    }

    @Test
    void worldPresetsHereAndInherit() {
        assertFalse(AdminCommands.execute("world here off", as("en_us", "minecraft:the_nether")).refused());
        assertEquals(Preset.OFF, MobSenses.settings().presetOf("minecraft:the_nether"));
        assertTrue(AdminCommands.execute("world minecraft:moon off", CONSOLE).refused());
        assertFalse(AdminCommands.execute("world minecraft:the_nether inherit", CONSOLE).refused());
        assertEquals(Preset.DEFAULT, MobSenses.settings().presetOf("minecraft:the_nether"));
    }

    @Test
    void typoGetsASuggestion() {
        Reply r = AdminCommands.execute("prest", CONSOLE);
        assertTrue(r.refused());
        assertTrue(r.toString().contains("Did you mean /mobsenses preset?"), r.toString());
    }

    @Test
    void helpListsEverySubcommandWithoutBareKeys() {
        String r = AdminCommands.execute("help", as("de_de", null)).toString();
        for (String sub : new String[]{"status", "preset", "hearing", "sight", "voice", "world", "set", "get", "reset", "hud", "debug", "reload", "help"}) {
            assertTrue(r.contains("/mobsenses " + sub), sub + " in " + r);
        }
        assertFalse(r.contains("command.mobsenses."), r);
    }

    @Test
    void gameEventsAreCountedUnlessTheWorldIsOff() {
        long before = MobSenses.noise().total();
        MobSenses.gameEvent("minecraft:step", "minecraft:overworld", 0, 64, 0);
        assertEquals(before + 1, MobSenses.noise().total());
        MobSenses.settings().setWorldPreset("minecraft:the_end", Preset.OFF);
        MobSenses.gameEvent("minecraft:step", "minecraft:the_end", 0, 64, 0);
        assertEquals(before + 1, MobSenses.noise().total());
    }

    @Test
    void suggestions() {
        assertEquals(List.of("preset"), AdminCommands.suggestions("pre", CONSOLE));
        assertEquals(List.of("hardcore"), AdminCommands.suggestions("preset h", CONSOLE));
        assertEquals(List.of("on", "off"), AdminCommands.suggestions("voice ", CONSOLE));
        assertTrue(AdminCommands.suggestions("world ", as("en_us", "minecraft:overworld")).contains("here"));
        assertTrue(AdminCommands.suggestions("world here ", CONSOLE).contains("inherit"));
        assertEquals(List.of("on", "off"), AdminCommands.suggestions("debug ", CONSOLE));
        assertFalse(MobSenses.debug());
        assertFalse(AdminCommands.execute("debug", CONSOLE).refused());
        assertTrue(MobSenses.debug());
        assertFalse(AdminCommands.execute("debug off", CONSOLE).refused());
        assertFalse(MobSenses.debug());

        assertFalse(AdminCommands.execute("set gunshot_silenced_range 10", CONSOLE).refused());
        assertEquals(10.0, MobSenses.settings().gunshotSilencedRange(), 1e-6);
        assertTrue(AdminCommands.execute("set gunshot_silenced_range", CONSOLE).toString().contains("10"));
        assertTrue(AdminCommands.suggestions("set gun", CONSOLE).contains("gunshot_range"));
        assertFalse(AdminCommands.execute("reset gunshot_silenced_range", CONSOLE).refused());
        assertEquals(16.0, MobSenses.settings().gunshotSilencedRange(), 1e-6);
        assertTrue(AdminCommands.execute("get guns", CONSOLE).toString().contains("gunshot_range"));
        assertFalse(AdminCommands.execute("hud hotbar", CONSOLE).refused());
        assertEquals(ClientSettings.Anchor.HOTBAR, MobSenses.clientSettings().position());
        assertFalse(AdminCommands.execute("hud opacity 75", CONSOLE).refused());
        assertEquals(75, MobSenses.clientSettings().opacity());
        assertFalse(AdminCommands.execute("hud offset 14 22", CONSOLE).refused());
        assertEquals(14, MobSenses.clientSettings().x());
        assertEquals(22, MobSenses.clientSettings().y());
        assertFalse(AdminCommands.execute("hud bar scent off", CONSOLE).refused());
        assertFalse(MobSenses.clientSettings().showScent());
        assertFalse(AdminCommands.execute("hud reset", CONSOLE).refused());
        assertEquals(ClientSettings.Anchor.TOP_LEFT, MobSenses.clientSettings().position());
        assertEquals(100, MobSenses.clientSettings().opacity());
        assertTrue(MobSenses.clientSettings().showScent());
    }

    @Test
    void clientHudCommandDedicatedRefusalAndLanHudIsolation() {
        assertFalse(AdminCommands.executeHud("minimal", "en_us").refused());
        assertEquals(ClientSettings.Style.MINIMAL, MobSenses.clientSettings().style());
        assertFalse(AdminCommands.executeHud("tactical", "ru_ru").refused());
        assertEquals(ClientSettings.Style.TACTICAL, MobSenses.clientSettings().style());
        assertFalse(AdminCommands.executeHud("range off", "en_us").refused());
        assertFalse(MobSenses.clientSettings().showRange());
        assertTrue(AdminCommands.hudSuggestions("mi").contains("minimal"));
        assertTrue(AdminCommands.hudSuggestions("bar ").contains("range"));
        assertFalse(AdminCommands.executeHud("reset", "en_us").refused());
        assertTrue(MobSenses.clientSettings().showRange());

        AdminCommands.Context dedicated = new AdminCommands.Context() {
            @Override
            public String language() {
                return "en_us";
            }

            @Override
            public String world() {
                return "minecraft:overworld";
            }

            @Override
            public List<String> worlds() {
                return List.of("minecraft:overworld");
            }

            @Override
            public boolean isDedicatedServer() {
                return true;
            }
        };
        Reply refused = AdminCommands.execute("hud minimal", dedicated);
        assertTrue(refused.refused());
        assertTrue(refused.toString().contains("/mshud"), refused.toString());

        java.util.UUID host = java.util.UUID.randomUUID();
        java.util.UUID guest = java.util.UUID.randomUUID();
        MobSenses.hud().reset();
        MobSenses.setLocalPlayerId(host);
        try {
            MobSenses.gameEvent("minecraft:explode", "minecraft:overworld", 0, 64, 0,
                    NoiseModel.Posture.WALKING, null, null, true, 1.0, guest);
            assertEquals(0, MobSenses.hud().noisePercent(), "LAN guest noise must not spike host HUD");

            MobSenses.gameEvent("minecraft:explode", "minecraft:overworld", 0, 64, 0,
                    NoiseModel.Posture.WALKING, null, null, true, 1.0, host);
            assertTrue(MobSenses.hud().noisePercent() >= 80, "Local host noise must update host HUD");
        } finally {
            MobSenses.setLocalPlayerId(null);
            MobSenses.hud().reset();
        }
    }

    @Test
    void allowClientHudFairPlayLockAndClickableReplyChips() {
        assertTrue(MobSenses.settings().allowClientHud());
        Reply statusDefault = AdminCommands.execute("status", CONSOLE);
        assertFalse(statusDefault.toString().contains("locked by server"));
        assertTrue(statusDefault.lines().stream().anyMatch(Reply.Line::hasSpans), "status should carry clickable chips");
        for (Reply.Line line : statusDefault.lines()) {
            assertFalse(line.text().contains("["), "Plain console text must stay free of [chip] brackets: " + line.text());
        }

        assertFalse(AdminCommands.execute("set allow_client_hud false", CONSOLE).refused());
        assertFalse(MobSenses.settings().allowClientHud());
        String lockedStatus = AdminCommands.execute("status", CONSOLE).toString();
        assertTrue(lockedStatus.contains("Client Stealth HUD: locked by server"), lockedStatus);

        Reply presetReply = AdminCommands.execute("preset hardcore", CONSOLE);
        assertEquals("Preset is now Hardcore (hardcore).", presetReply.lines().get(0).text());
        assertTrue(presetReply.lines().get(0).hasSpans());
    }
}
