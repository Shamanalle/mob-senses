package com.kasper.mobsenses.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plays singleplayer with the mod: the world's own server runs Mob Senses inside the client.
 * Sets up a stone courtyard with listener mobs, triggers noise with debug particles enabled, verifies
 * that mobs hear the sound and pathfind toward its origin, exercises voice, held-torch sight, and
 * wounded blood-scent with the client Stealth & Noise HUD, captures 4K {@code store-*} screenshots,
 * and exercises {@code /mobsenses} in English and Russian.
 */
public class CommandsGameTest implements FabricClientGameTest {

    private static final String[] LANGUAGES = {"en_us", "ru_ru"};
    private static final int STORE_WIDTH = 3840;
    private static final int STORE_HEIGHT = 2160;
    private static final int STORE_GUI_SCALE = 5;

    /** Chat lines as the client received them. */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    private static final String[][] GROUPS = {
            {"status", "mobsenses status"},
            {"help", "mobsenses help"},
            {"changes", "mobsenses preset hardcore", "mobsenses hearing 1.5", "mobsenses debug on", "mobsenses world here subtle", "mobsenses prest"},
            {"undo", "mobsenses preset default", "mobsenses hearing 1", "mobsenses debug off", "mobsenses world here inherit"},
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
            context.runOnClient(client -> TestScreens.command(client, "gamemode creative"));
            context.waitTicks(5);

            // Set up the investigation courtyard and listener mobs
            context.runOnClient(TestScreens::setupInvestigationScene);
            context.waitTicks(20);
            context.getInput().resizeWindow(STORE_WIDTH, STORE_HEIGHT);
            context.runOnClient(client -> TestScreens.guiScale(client, STORE_GUI_SCALE));
            context.waitTicks(5);

            // Enable debug particles, ring the bell, and capture 4K store screenshots as mobs investigate
            context.runOnClient(TestScreens::clearChat);
            context.runOnClient(client -> TestScreens.command(client, "mobsenses debug on"));
            context.waitTicks(5);
            context.runOnClient(TestScreens::ringSceneBell);
            context.waitTicks(5);
            storeShot(context, "store-debug");

            context.waitTicks(35);
            context.runOnClient(TestScreens::ringSceneBell);
            context.waitTicks(5);
            storeShot(context, "store-hearing");
            TestScreens.verifyInvestigation();

            // Turn off debug particles for the normal player HUD screenshot so the view stays clean
            context.runOnClient(client -> TestScreens.command(client, "mobsenses debug off"));
            context.waitTicks(35);
            context.runOnClient(TestScreens::clearChat);

            // Exercise voice, held-torch sight, wounded scent trail, and capture 4K HUD screenshot
            context.runOnClient(TestScreens::triggerVoiceSightAndScent);
            context.waitTicks(5);
            context.runOnClient(TestScreens::refreshVoiceAndNoiseForHud);
            context.waitTicks(1);
            storeShot(context, "store-hud");
            context.runOnClient(TestScreens::restorePlayerState);
            context.waitTicks(20);

            context.runOnClient(TestScreens::clearChat);
            context.runOnClient(client -> TestScreens.command(client, "mobsenses status"));
            context.waitTicks(10);
            storeShot(context, "store-status");

            context.getInput().resizeWindow(854, 480);
            context.runOnClient(client -> TestScreens.guiScale(client, 0));
            context.waitTicks(5);

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

    private static void storeShot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(STORE_WIDTH, STORE_HEIGHT));
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
                throw new AssertionError("Noise events did not reach the mod: " + RECEIVED);
            }
            if (all.matches("(?s).*(Mobs alerted in the last minute|Встревожено мобов за последнюю минуту): 0 .*")) {
                throw new AssertionError("No mobs were alerted in status: " + RECEIVED);
            }
        }
    }
}
