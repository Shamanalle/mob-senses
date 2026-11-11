package com.kasper.mobsenses;

import com.kasper.mobsenses.client.Canvas;
import com.kasper.mobsenses.client.SettingsScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;

/**
 * Concrete settings screen for Minecraft 1.16.5 - 1.19.4 ({@link PoseStack} rendering, 3-arg scroll).
 */
public final class MobSensesScreen extends SettingsScreen {

    public MobSensesScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        Canvas canvas = new PoseCanvas(poseStack, this.font);
        paintBackdrop(canvas);
        super.render(poseStack, mouseX, mouseY, delta);
        paint(canvas, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return onScroll(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }
}
