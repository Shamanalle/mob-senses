package com.kasper.mobsenses.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Text component factories for Minecraft 1.19+. */
public final class Txt {

    private Txt() {
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text == null ? "" : text);
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static MutableComponent empty() {
        return Component.empty();
    }
}
