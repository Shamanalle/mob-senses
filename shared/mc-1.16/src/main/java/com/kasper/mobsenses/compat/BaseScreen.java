package com.kasper.mobsenses.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base screen for Minecraft 1.16.5 (bridges {@code addButton} and {@code buttons}/{@code children}). */
public abstract class BaseScreen extends Screen {

    protected BaseScreen(Component title) {
        super(title);
    }

    protected <T extends AbstractWidget> T addRenderableWidget(T widget) {
        return addButton(widget);
    }

    protected void clearWidgets() {
        buttons.clear();
        children.clear();
    }
}
