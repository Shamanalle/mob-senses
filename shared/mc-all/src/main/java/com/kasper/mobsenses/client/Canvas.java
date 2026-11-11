package com.kasper.mobsenses.client;

import net.minecraft.network.chat.Component;

/**
 * Version-independent 2D drawing surface for the client Stealth & Noise HUD and Settings GUI.
 */
public interface Canvas {

    int width();

    int height();

    int textWidth(Component text);

    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(Component text, int x, int y, int argb);

    default int width(Component text) {
        return textWidth(text);
    }

    default void centered(Component text, int centerX, int y, int argb) {
        text(text, centerX - textWidth(text) / 2, y, argb);
    }

    default void right(Component text, int rightX, int y, int argb) {
        text(text, rightX - textWidth(text), y, argb);
    }

    default void hLine(int x1, int x2, int y, int argb) {
        if (x2 > x1) {
            fill(x1, y, x2, y + 1, argb);
        }
    }

    default void vLine(int x, int y1, int y2, int argb) {
        if (y2 > y1) {
            fill(x, y1, x + 1, y2, argb);
        }
    }

    default void frame(int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        fill(x1, y1, x2, y1 + 1, argb);
        fill(x1, y2 - 1, x2, y2, argb);
        fill(x1, y1 + 1, x1 + 1, y2 - 1, argb);
        fill(x2 - 1, y1 + 1, x2, y2 - 1, argb);
    }

    default boolean pushScale(float factor) {
        return false;
    }

    default void popScale() {
    }
}
