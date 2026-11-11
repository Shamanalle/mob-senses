package com.kasper.mobsenses;

import com.kasper.mobsenses.client.Canvas;
import com.kasper.mobsenses.client.PoseScaler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * {@link Canvas} backed by {@link GuiGraphics} on Minecraft 1.20 - 1.21.11.
 * <p>
 * Uses {@code drawCenteredString} with half-width offset because {@code drawString}'s return type
 * changed from {@code int} to {@code void} in 1.21.6, whereas {@code drawCenteredString} is
 * {@code void} across the entire 1.20 - 1.21.11 range.
 */
public final class GuiCanvas implements Canvas {

    private final GuiGraphics graphics;
    private final Font font;

    public GuiCanvas(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public int textWidth(Component text) {
        return font.width(text);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        int w = font.width(text);
        graphics.drawCenteredString(font, text, x + w / 2, y, argb);
    }

    @Override
    public boolean pushScale(float factor) {
        return PoseScaler.push(graphics, factor);
    }

    @Override
    public void popScale() {
        PoseScaler.pop(graphics);
    }
}
