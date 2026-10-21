package com.kasper.mobsenses.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plays singleplayer with the mod: the world's own server runs Mob Senses inside the client. Runs
 * {@code /mobsenses} as the player, in English and in Russian, and takes a screenshot of the chat after
 * each group (uploaded by CI). Fails on a text key that was not translated, on a reply in the wrong
 * language, on an unknown command, and when the noise of a TNT explosion does not reach the mod.
 */
public class CommandsGameTest implements FabricClientGameTest {

    private static final String[] LANGUAGES = {"en_us", "ru_ru"};

    /** Chat lines as the client received them. */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    private static final String[][] GROUPS = {
            {"status", "mobsenses status"},
            {"help", "mobsenses help"},
            {"changes", "mobsenses preset hardcore", "mobsenses hearing 1.5", "mobsenses world here subtle", "mobsenses prest"},
            {"undo", "mobsenses preset default", "mobsenses hearing 1", "mobsenses world here inherit"},
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                RECEIVED.add(message.getString());
            }
        });
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(TestScreens::allowCommands).create()) {
            context.waitTicks(40);
            // A primed TNT: its explosion is noise the mod must hear (in creative, so it does not kill the player)
            context.runOnClient(client -> TestScreens.command(client, "gamemode creative"));
            context.waitTicks(5);
            context.runOnClient(client -> TestScreens.command(client, "summon minecraft:tnt ~8 ~ ~8 {fuse:1}"));
            context.waitTicks(40);
            for (String language : LANGUAGES) {
                context.runOnClient(client -> TestScreens.language(client, language));
                context.waitTicks(5);
                context.waitFor(TestScreens::loaded, 1200);
                // The server answers in the player's language: tell it the new one
                context.runOnClient(TestScreens::sendOptions);
                context.waitTicks(40);
                for (String[] group : GROUPS) {
                    context.runOnClient(TestScreens::clearChat);
                    RECEIVED.clear();
                    for (int i = 1; i < group.length; i++) {
                        String command = group[i];
                        context.runOnClient(client -> TestScreens.command(client, command));
                        context.waitTicks(10);
                    }
                    context.takeScreenshot("commands-" + group[0] + "-" + language);
                    check(group, language);
                }
            }
        }
    }

    private static void check(String[] group, String language) {
        if (RECEIVED.isEmpty()) {
            throw new AssertionError("No reply to " + String.join(", ", group) + " (" + language + ")");
        }
        for (String line : RECEIVED) {
            if (line.contains("command.mobsenses.")) {
                throw new AssertionError("Untranslated text in " + language + ": " + line);
            }
            if (line.contains("Unknown or incomplete command") || line.contains("Неизвестная или неполная команда")) {
                throw new AssertionError("/mobsenses is not a command here (" + language + "): " + RECEIVED);
            }
        }
        String all = String.join("\n", RECEIVED);
        if (group[0].equals("status")) {
            String preset = language.equals("ru_ru") ? "Пресет: " : "Preset: ";
            if (!all.contains("Mob Senses ") || !all.contains(preset)) {
                throw new AssertionError("No status in " + language + ": " + RECEIVED);
            }
            if (all.matches("(?s).*(Noise in the last minute|Шум за последнюю минуту): 0 .*")) {
                throw new AssertionError("The TNT's explosion did not reach the mod: " + RECEIVED);
            }
        }
    }
}
