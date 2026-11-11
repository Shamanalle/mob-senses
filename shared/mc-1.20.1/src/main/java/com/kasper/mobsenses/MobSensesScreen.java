package com.kasper.mobsenses;

import com.kasper.mobsenses.client.Canvas;
import com.kasper.mobsenses.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Concrete settings screen for Minecraft 1.20 - 1.20.1 ({@link GuiGraphics} rendering, 3-arg scroll).
 */
public final class MobSensesScreen extends SettingsScreen {

    public MobSensesScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Canvas canvas = new GuiCanvas(graphics, this.font);
        paintBackdrop(canvas);
        super.render(graphics, mouseX, mouseY, delta);
        paint(canvas, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return onScroll(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }
}
