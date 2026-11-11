package com.kasper.mobsenses.compat;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Text component factories for Minecraft 1.16.5 - 1.18.2. */
public final class Txt {

    private Txt() {
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text == null ? "" : text);
    }

    public static MutableComponent translatable(String key) {
        return new TranslatableComponent(key);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
    }
}
