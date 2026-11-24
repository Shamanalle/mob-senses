package com.kasper.mobsenses.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Client widget & screen helpers for Minecraft 1.19.3 - 1.21.11. */
public final class Mc {

    private static final Method SET_Y = findSetYMethod();

    private Mc() {
    }

    private static Method findSetYMethod() {
        String[] names = {"setY", "method_46419", "m_253211_", "m_252888_"};
        for (String name : names) {
            try {
                Method m = AbstractWidget.class.getMethod(name, int.class);
                if (m.getReturnType() == void.class) {
                    return m;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Class<?> c = AbstractWidget.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Method m = c.getDeclaredMethod(name, int.class);
                    if (m.getReturnType() != void.class) {
                        continue;
                    }
                    if (!Modifier.isPublic(m.getModifiers())) {
                        m.setAccessible(true);
                    }
                    return m;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
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
        if (widget == null || SET_Y == null) {
            return;
        }
        try {
            SET_Y.invoke(widget, y);
        } catch (Throwable ignored) {
        }
    }

    public static void openScreen(Minecraft client, Screen screen) {
        if (client != null) {
            client.setScreen(screen);
        }
    }

    public static Screen currentScreen(Minecraft client) {
        return client == null ? null : client.screen;
    }

    public static void overlay(Minecraft client, Component text) {
        if (client != null && client.gui != null && text != null) {
            client.gui.setOverlayMessage(text, false);
        }
    }
}
