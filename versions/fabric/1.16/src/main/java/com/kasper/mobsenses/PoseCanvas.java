package com.kasper.mobsenses;

import com.kasper.mobsenses.client.Canvas;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

/**
 * {@link Canvas} backed by {@link PoseStack} and {@link GuiComponent} on Minecraft 1.16.5 - 1.19.4.
 */
public final class PoseCanvas implements Canvas {

    private final PoseStack poseStack;
    private final Font font;

    public PoseCanvas(PoseStack poseStack, Font font) {
        this.poseStack = poseStack;
        this.font = font;
    }

    @Override
    public int width() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int height() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public int textWidth(Component text) {
        return font.width(text);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        GuiComponent.fill(poseStack, x1, y1, x2, y2, argb);
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        font.drawShadow(poseStack, text, x, y, argb);
    }
}
