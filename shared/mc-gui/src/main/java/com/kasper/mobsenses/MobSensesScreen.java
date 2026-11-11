package com.kasper.mobsenses;

import com.kasper.mobsenses.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Concrete settings screen for Minecraft 1.20.2 - 1.21.11 ({@link GuiGraphics} rendering, 4-arg scroll).
 */
public final class MobSensesScreen extends SettingsScreen {

    public MobSensesScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        addRenderableOnly((graphics, mouseX, mouseY, delta) ->
                paintBackdrop(new GuiCanvas(graphics, this.font)));
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        paint(new GuiCanvas(graphics, this.font), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onScroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
