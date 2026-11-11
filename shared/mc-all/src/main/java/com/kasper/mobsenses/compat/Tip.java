package com.kasper.mobsenses.compat;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Widget hover tooltip wrapper for Minecraft 1.19.3+. */
public final class Tip {

    private final Tooltip tooltip;
    private final Component text;

    private Tip(Component text) {
        this.text = text;
        this.tooltip = Tooltip.create(text);
    }

    public static Tip of(Component text) {
        return text == null ? null : new Tip(text);
    }

    public Tooltip unwrap() {
        return tooltip;
    }

    public Component text() {
        return text;
    }
}
