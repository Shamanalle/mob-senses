package com.kasper.mobsenses.gametest;

import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ServerSettings;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;

/**
 * Opens the 2-tab Mob Senses settings screen ({@code HUD} and {@code SENSES}) in a real client,
 * in English and in Russian across two window geometries ({@code 854x480} and {@code 1280x960}),
 * scrolls each tab through its full viewport height, exercises remote server state synchronization
 * (both admin mode and fair-play HUD lock), and captures Full HD (1920x1080) store GUI screenshots.
 */
public class SettingsScreenGameTest implements FabricClientGameTest {

    private static final int[][] WINDOWS = {{854, 480}, {1280, 960}};
    private static final String[] LANGUAGES = {"en_us", "ru_ru"};
    private static final int STORE_WIDTH = 1920;
    private static final int STORE_HEIGHT = 1080;
    private static final int STORE_GUI_SCALE = 3;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(TestScreens::allowCommands).create()) {
            context.waitTicks(40);
            context.runOnClient(client -> TestScreens.command(client, "gamemode creative"));
            context.waitTicks(5);
            context.runOnClient(TestScreens::setupInvestigationScene);
            context.waitTicks(20);
            for (String language : LANGUAGES) {
                context.runOnClient(client -> TestScreens.language(client, language));
                context.waitTicks(5);
                context.waitFor(TestScreens::loaded, 1200);
                context.waitTicks(40);
                for (int[] window : WINDOWS) {
                    shootTabs(context, language, window);
                }
                if (language.equals("en_us")) {
                    shootStore(context);
                }
            }
            // Restore clean English locale and default link state for subsequent gametests
            context.runOnClient(client -> {
                MobSenses.serverLink().resetClientState();
                TestScreens.language(client, "en_us");
            });
            context.waitTicks(5);
            context.waitFor(TestScreens::loaded, 1200);
        }
    }

    private static void pretendServerState(boolean admin, boolean allowClientHud, double hearing, double wallMuffling) {
        ServerSettings settings = new ServerSettings(null);
        settings.setOption("hearing", String.format(Locale.ROOT, "%.2f", hearing));
        settings.setOption("wall_muffling", String.format(Locale.ROOT, "%.2f", wallMuffling));
        settings.setOption("allow_client_hud", allowClientHud ? "on" : "off");
        String payload = LinkProtocol.writeState(
                settings,
                "minecraft:overworld",
                admin,
                false,
                true,
                42L,
                17L,
                "Paper 1.21.8"
        );
        MobSenses.serverLink().onStateText(payload);
    }

    private static void pretendServerState(boolean admin, boolean allowClientHud) {
        pretendServerState(admin, allowClientHud, 1.25, 0.75);
    }

    private static void scrollScreenTo(Screen screen, int targetScrollY) {
        if (!(screen instanceof SettingsScreen)) {
            return;
        }
        try {
            java.lang.reflect.Field maxF = SettingsScreen.class.getDeclaredField("maxScroll");
            java.lang.reflect.Field curF = SettingsScreen.class.getDeclaredField("scrollY");
            java.lang.reflect.Method layoutM = SettingsScreen.class.getDeclaredMethod("layoutScrollRows");
            maxF.setAccessible(true);
            curF.setAccessible(true);
            layoutM.setAccessible(true);
            int max = maxF.getInt(screen);
            curF.setInt(screen, Math.max(0, Math.min(max, targetScrollY)));
            layoutM.invoke(screen);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void shootTabs(ClientGameTestContext context, String language, int[] window) {
        context.getInput().resizeWindow(window[0], window[1]);
        context.waitTicks(2);
        String size = language + "-" + window[0] + "x" + window[1];

        for (SettingsScreen.Tab tab : SettingsScreen.Tab.values()) {
            if (tab == SettingsScreen.Tab.SENSES) {
                context.runOnClient(client -> pretendServerState(true, true));
            } else {
                context.runOnClient(client -> MobSenses.serverLink().resetClientState());
            }
            String name = tab.name().toLowerCase(Locale.ROOT) + "-" + size;
            context.runOnClient(client -> {
                SettingsScreen.openOn(tab);
                Screen screen = ClientLink.createSettingsScreen(null);
                if (screen == null) {
                    throw new AssertionError("ClientLink.createSettingsScreen(null) returned null");
                }
                TestScreens.open(client, screen);
            });
            context.waitTicks(3);
            context.takeScreenshot("settings-" + name);

            for (int page = 2; page < 10 && context.computeOnClient(client ->
                    TestScreens.current(client) instanceof SettingsScreen s && s.scrollPage()); page++) {
                context.waitTicks(2);
                context.takeScreenshot("settings-" + name + "-" + page);
            }

            context.runOnClient(client -> {
                if (TestScreens.current(client) instanceof SettingsScreen s) {
                    s.scrollToEnd();
                }
            });
            context.waitTicks(2);
            context.takeScreenshot("settings-" + name + "-end");
            context.runOnClient(client -> TestScreens.open(client, null));
            context.waitTicks(1);
        }

        context.runOnClient(client -> MobSenses.serverLink().resetClientState());
    }

    private static void shootStore(ClientGameTestContext context) {
        net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions optsHud =
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("store-ui-hud").withSize(STORE_WIDTH, STORE_HEIGHT);
        net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions optsServer =
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("store-ui-server").withSize(STORE_WIDTH, STORE_HEIGHT);
        net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions optsHearing =
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("store-ui-hearing").withSize(STORE_WIDTH, STORE_HEIGHT);
        net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions optsSight =
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("store-ui-sight").withSize(STORE_WIDTH, STORE_HEIGHT);
        net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions optsLocked =
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("store-ui-locked").withSize(STORE_WIDTH, STORE_HEIGHT);

        context.getInput().resizeWindow(STORE_WIDTH, STORE_HEIGHT);
        context.waitTicks(4);
        context.runOnClient(client -> {
            TestScreens.guiScale(client, STORE_GUI_SCALE);
            TestScreens.hideHud(client, true);
            TestScreens.clearChat(client);
            MobSenses.serverLink().resetClientState();
        });
        context.waitTicks(2);

        // 1. Stealth HUD tab (live preview + acoustic test pulses + full HUD controls)
        context.runOnClient(client -> {
            SettingsScreen.openOn(SettingsScreen.Tab.HUD);
            TestScreens.open(client, ClientLink.createSettingsScreen(null));
        });
        parkCursor(context);
        context.takeScreenshot(optsHud);
        context.runOnClient(client -> TestScreens.open(client, null));
        context.waitTicks(1);

        // 2. Senses & World tab — Top: live Paper telemetry + Acoustic Wall Attenuation summary + presets
        context.runOnClient(client -> {
            pretendServerState(true, true, 1.00, 1.00);
            SettingsScreen.openOn(SettingsScreen.Tab.SENSES);
            TestScreens.open(client, ClientLink.createSettingsScreen(null));
        });
        parkCursor(context);
        context.takeScreenshot(optsServer);

        // 3. Senses & World tab — Middle: Global Sense Preset + Hearing, Walls, Firearms & Voice Chat
        context.runOnClient(client -> scrollScreenTo(TestScreens.current(client), 116));
        parkCursor(context);
        context.takeScreenshot(optsHearing);

        // 4. Senses & World tab — Bottom: Sight, Darkness, Blood Scent & World/Server controls
        context.runOnClient(client -> {
            if (TestScreens.current(client) instanceof SettingsScreen s) {
                s.scrollToEnd();
            }
        });
        parkCursor(context);
        context.takeScreenshot(optsSight);
        context.runOnClient(client -> TestScreens.open(client, null));
        context.waitTicks(1);

        // 5. Fair-play server HUD lock banner
        context.runOnClient(client -> {
            pretendServerState(false, false, 1.00, 1.00);
            SettingsScreen.openOn(SettingsScreen.Tab.HUD);
            TestScreens.open(client, ClientLink.createSettingsScreen(null));
        });
        parkCursor(context);
        context.takeScreenshot(optsLocked);

        context.runOnClient(client -> {
            TestScreens.open(client, null);
            MobSenses.serverLink().resetClientState();
            TestScreens.hideHud(client, false);
            TestScreens.guiScale(client, 0);
        });
        context.waitTicks(2);
    }

    private static void parkCursor(ClientGameTestContext context) {
        context.waitTicks(3);
        context.getInput().setCursorPos(STORE_WIDTH - 40, STORE_HEIGHT - 20);
        context.waitTicks(3);
    }
}
