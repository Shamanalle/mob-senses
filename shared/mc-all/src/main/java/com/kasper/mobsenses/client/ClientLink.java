package com.kasper.mobsenses.client;

import com.kasper.mobsenses.BuildInfo;
import com.kasper.mobsenses.LinkProtocol;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.compat.Mc;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client-side coordinator for the settings GUI, keybinding,
 * and {@code mobsenses:hello / state / admin / admin_reply} synchronization.
 */
public final class ClientLink {

    private static volatile Consumer<String> helloSender = s -> {
    };
    private static volatile Consumer<String> adminSender = s -> {
    };
    private static volatile Function<Screen, Screen> settingsScreenFactory;
    private static volatile boolean openSettingsRequested;

    private static Object activeConnection;
    private static int helloCountdownTicks;

    static {
        MobSenses.serverLink().setAdminTextSender(raw -> adminSender.accept(raw));
    }

    private ClientLink() {
    }

    public static void bindNetworking(Consumer<String> c2sHello, Consumer<String> c2sAdmin) {
        helloSender = c2sHello != null ? c2sHello : (s -> {
        });
        adminSender = c2sAdmin != null ? c2sAdmin : (s -> {
        });
        MobSenses.serverLink().setAdminTextSender(raw -> adminSender.accept(raw));
    }

    public static void setSettingsScreenFactory(Function<Screen, Screen> factory) {
        settingsScreenFactory = factory;
    }

    public static Screen createSettingsScreen(Screen parent) {
        Function<Screen, Screen> f = settingsScreenFactory;
        return f != null ? f.apply(parent) : null;
    }

    public static void requestOpenSettings() {
        openSettingsRequested = true;
    }

    public static void onServerStateText(String raw) {
        Minecraft mc = Minecraft.getInstance();
        Runnable task = () -> {
            if (MobSenses.serverLink().onStateText(raw)) {
                notifyOpenScreen(mc);
            }
        };
        if (mc != null) {
            mc.execute(task);
        } else {
            task.run();
        }
    }

    public static void onAdminReplyText(String raw) {
        Minecraft mc = Minecraft.getInstance();
        Runnable task = () -> {
            if (MobSenses.serverLink().onAdminReplyText(raw)) {
                notifyOpenScreen(mc);
            }
        };
        if (mc != null) {
            mc.execute(task);
        } else {
            task.run();
        }
    }

    private static void notifyOpenScreen(Minecraft mc) {
        if (mc == null) {
            return;
        }
        Screen current = Mc.currentScreen(mc);
        if (current instanceof SettingsScreen) {
            ((SettingsScreen) current).onLinkUpdated();
        }
    }

    public static String languageCode(Minecraft mc) {
        if (mc != null && mc.options != null && mc.options.languageCode != null) {
            return mc.options.languageCode;
        }
        return "en_us";
    }

    public static void tick(Minecraft mc, KeyMapping settingsKey) {
        if (mc == null) {
            return;
        }
        if (MobSenses.clientSettings().file() == null && mc.gameDirectory != null) {
            MobSenses.initClient(mc.gameDirectory.toPath().resolve("config"));
        }
        if (openSettingsRequested) {
            openSettingsRequested = false;
            Screen current = Mc.currentScreen(mc);
            if (!(current instanceof SettingsScreen)) {
                Screen target = createSettingsScreen(current);
                if (target != null) {
                    Mc.openScreen(mc, target);
                }
            }
        }
        if (settingsKey != null) {
            while (settingsKey.consumeClick()) {
                Screen current = Mc.currentScreen(mc);
                if (!(current instanceof SettingsScreen)) {
                    Screen target = createSettingsScreen(current);
                    if (target != null) {
                        Mc.openScreen(mc, target);
                    }
                }
            }
        }

        Object conn = mc.player != null ? mc.getConnection() : null;
        if (conn == null || mc.level == null) {
            if (activeConnection != null || MobSenses.serverLink().connected()) {
                activeConnection = null;
                helloCountdownTicks = 0;
                MobSenses.serverLink().resetClientState();
            }
            return;
        }

        boolean integrated = mc.getSingleplayerServer() != null;
        if (conn != activeConnection) {
            activeConnection = conn;
            MobSenses.serverLink().resetClientState();
            helloCountdownTicks = integrated ? 0 : 5;
        }

        if (!integrated && helloCountdownTicks > 0) {
            helloCountdownTicks--;
            if (helloCountdownTicks == 0) {
                try {
                    String hello = LinkProtocol.writeHello(BuildInfo.VERSION, languageCode(mc));
                    helloSender.accept(hello);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
