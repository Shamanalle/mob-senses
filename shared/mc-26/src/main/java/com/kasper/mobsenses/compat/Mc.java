package com.kasper.mobsenses.compat;

import com.kasper.mobsenses.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Client widget & screen helpers for Minecraft 26.x (handles 26.3 moving {@code setScreen} and
 * {@code screen()} from {@link Minecraft} to {@link Gui}).
 */
public final class Mc {

    private static final Method GUI_SET = method(Gui.class, "setScreen", Screen.class);
    private static final Method GUI_GET = method(Gui.class, "screen");
    private static final Method MC_SET = method(Minecraft.class, "setScreen", Screen.class);
    private static final Field MC_SCREEN = field(Minecraft.class, "screen");

    private Mc() {
    }

    public static int x(AbstractWidget widget) {
        return widget.getX();
    }

    public static int y(AbstractWidget widget) {
        return widget.getY();
    }

    public static void setX(AbstractWidget widget, int x) {
        widget.setX(x);
    }

    public static void setY(AbstractWidget widget, int y) {
        widget.setY(y);
    }

    public static void openScreen(Minecraft client, Screen screen) {
        if (client == null) {
            return;
        }
        try {
            if (GUI_SET != null && client.gui != null) {
                GUI_SET.invoke(client.gui, screen);
            } else if (MC_SET != null) {
                MC_SET.invoke(client, screen);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ModLog.failure("Opening Mob Senses screen", e);
        }
    }

    public static Screen currentScreen(Minecraft client) {
        if (client == null) {
            return null;
        }
        try {
            if (GUI_GET != null && client.gui != null) {
                return (Screen) GUI_GET.invoke(client.gui);
            }
            if (MC_SCREEN != null) {
                return (Screen) MC_SCREEN.get(client);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    public static void overlay(Minecraft client, Component text) {
        if (client != null && client.player != null && text != null) {
            client.player.sendSystemMessage(text);
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
