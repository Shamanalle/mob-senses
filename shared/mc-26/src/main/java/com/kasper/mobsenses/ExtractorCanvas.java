package com.kasper.mobsenses;

import com.kasper.mobsenses.client.Canvas;
import com.kasper.mobsenses.client.PoseScaler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * {@link Canvas} backed by {@link GuiGraphicsExtractor} on Minecraft 26.x.
 */
public final class ExtractorCanvas implements Canvas {

    private final GuiGraphicsExtractor graphics;
    private final Font font;

    public ExtractorCanvas(GuiGraphicsExtractor graphics, Font font) {
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
        graphics.text(font, text, x, y, argb, true);
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
