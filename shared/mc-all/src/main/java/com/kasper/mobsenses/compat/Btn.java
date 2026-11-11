package com.kasper.mobsenses.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Button & widget tooltip helper for Minecraft 1.19.3+. */
public final class Btn {

    private Btn() {
    }

    public static Button create(int x, int y, int width, int height, Component label, Runnable onPress, Tip tip) {
        Button.Builder builder = Button.builder(label, b -> onPress.run()).bounds(x, y, width, height);
        if (tip != null) {
            builder.tooltip(tip.unwrap());
        }
        return builder.build();
    }

    public static void setTip(AbstractWidget widget, Tip tip) {
        if (widget != null) {
            widget.setTooltip(tip == null ? null : tip.unwrap());
        }
    }

    public static Component hoveredTipText(List<? extends AbstractWidget> widgets, int mouseX, int mouseY) {
        return null;
    }
}
