package com.kasper.mobsenses.compat;

import net.minecraft.network.chat.Component;

/** Widget hover tooltip wrapper for Minecraft 1.16.5 - 1.19.2. */
public final class Tip {

    private final Component text;

    private Tip(Component text) {
        this.text = text;
    }

    public static Tip of(Component text) {
        return text == null ? null : new Tip(text);
    }

    public Component text() {
        return text;
    }
}
