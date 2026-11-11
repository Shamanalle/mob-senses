package com.kasper.mobsenses;

import com.kasper.mobsenses.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Concrete settings screen for Minecraft 26.x ({@link GuiGraphicsExtractor} rendering, 4-arg scroll).
 */
public final class MobSensesScreen extends SettingsScreen {

    public MobSensesScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        addRenderableOnly((graphics, mouseX, mouseY, delta) ->
                paintBackdrop(new ExtractorCanvas(graphics, this.font)));
        super.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        paint(new ExtractorCanvas(graphics, this.font), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onScroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
