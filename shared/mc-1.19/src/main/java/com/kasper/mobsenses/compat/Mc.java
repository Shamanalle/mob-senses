package com.kasper.mobsenses.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client widget & screen helpers for Minecraft 1.16.5 - 1.19.2. */
public final class Mc {

    private Mc() {
    }

    public static int x(AbstractWidget widget) {
        return widget.x;
    }

    public static int y(AbstractWidget widget) {
        return widget.y;
    }

    public static void setX(AbstractWidget widget, int x) {
        widget.x = x;
    }

    public static void setY(AbstractWidget widget, int y) {
        widget.y = y;
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
