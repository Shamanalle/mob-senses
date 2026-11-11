package com.kasper.mobsenses.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Button & widget tooltip helper for Minecraft 1.16.5 - 1.19.2. */
public final class Btn {

    private static final Map<AbstractWidget, Tip> TIPS = new WeakHashMap<>();

    private Btn() {
    }

    public static Button create(int x, int y, int width, int height, Component label, Runnable onPress, Tip tip) {
        Button button = new Button(x, y, width, height, label, b -> onPress.run());
        setTip(button, tip);
        return button;
    }

    public static void setTip(AbstractWidget widget, Tip tip) {
        if (widget == null) {
            return;
        }
        if (tip == null) {
            TIPS.remove(widget);
        } else {
            TIPS.put(widget, tip);
        }
    }

    public static Component hoveredTipText(List<? extends AbstractWidget> widgets, int mouseX, int mouseY) {
        if (widgets == null) {
            return null;
        }
        for (AbstractWidget w : widgets) {
            if (w == null || !w.visible) {
                continue;
            }
            int x = Mc.x(w);
            int y = Mc.y(w);
            if (mouseX >= x && mouseX < x + w.getWidth() && mouseY >= y && mouseY < y + w.getHeight()) {
                Tip tip = TIPS.get(w);
                if (tip != null) {
                    return tip.text();
                }
            }
        }
        return null;
    }
}
