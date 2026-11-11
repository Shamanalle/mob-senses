package com.kasper.mobsenses.compat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base screen for Minecraft 1.17+. */
public abstract class BaseScreen extends Screen {

    protected BaseScreen(Component title) {
        super(title);
    }
}
