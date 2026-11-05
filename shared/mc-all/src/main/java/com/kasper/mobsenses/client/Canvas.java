package com.kasper.mobsenses.client;

import net.minecraft.network.chat.Component;

/**
 * Version-independent 2D drawing surface for the client Stealth & Noise HUD.
 */
public interface Canvas {

    int width();

    int height();

    int textWidth(Component text);

    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(Component text, int x, int y, int argb);
}
