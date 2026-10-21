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
        for (String sub : new String[]{"status", "preset", "hearing", "sight", "voice", "world", "reload", "help"}) {
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
    }
}
