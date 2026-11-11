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
 * (both admin mode and fair-play HUD lock), and captures 4K store GUI screenshots.
 */
public class SettingsScreenGameTest implements FabricClientGameTest {

    private static final int[][] WINDOWS = {{854, 480}, {1280, 960}};
    private static final String[] LANGUAGES = {"en_us", "ru_ru"};
    private static final int STORE_WIDTH = 3840;
    private static final int STORE_HEIGHT = 2160;
    private static final int STORE_GUI_SCALE = 8;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(40);
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

    private static void pretendServerState(boolean admin, boolean allowClientHud) {
        ServerSettings settings = new ServerSettings(null);
        settings.setOption("hearing", "1.25");
        settings.setOption("wall_muffling", "0.75");
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
        context.getInput().resizeWindow(STORE_WIDTH, STORE_HEIGHT);
        context.waitTicks(4);
        context.runOnClient(client -> {
            TestScreens.guiScale(client, STORE_GUI_SCALE);
            TestScreens.hideHud(client, true);
            TestScreens.clearChat(client);
            MobSenses.serverLink().resetClientState();
        });
        context.waitTicks(2);

        // 1. Stealth HUD tab (live preview + acoustic test pulses)
        context.runOnClient(client -> {
            SettingsScreen.openOn(SettingsScreen.Tab.HUD);
            TestScreens.open(client, ClientLink.createSettingsScreen(null));
        });
        parkCursor(context);
        context.takeScreenshot("store-ui-hud");
        context.runOnClient(client -> TestScreens.open(client, null));
        context.waitTicks(1);

        // 2. Senses & World tab (connected admin telemetry + acoustic wall attenuation summary)
        context.runOnClient(client -> {
            pretendServerState(true, true);
            SettingsScreen.openOn(SettingsScreen.Tab.SENSES);
            TestScreens.open(client, ClientLink.createSettingsScreen(null));
        });
        parkCursor(context);
        context.takeScreenshot("store-ui-senses");
        context.runOnClient(client -> TestScreens.open(client, null));
        context.waitTicks(1);

        // 3. Fair-play server HUD lock banner
        context.runOnClient(client -> {
            pretendServerState(false, false);
            SettingsScreen.openOn(SettingsScreen.Tab.HUD);
            TestScreens.open(client, ClientLink.createSettingsScreen(null));
        });
        parkCursor(context);
        context.takeScreenshot("store-ui-locked");

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
